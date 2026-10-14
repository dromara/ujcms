package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.RegionService.ColumnInput;
import com.ujcms.cms.core.composition.RegionService.PanelInput;
import com.ujcms.cms.core.composition.RegionService.RowInput;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.PageRegion;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.PageRegionSnapshotMapper;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http409Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 区域服务测试（page-regions §7.1 / §7.3 / component-settings §3）：渲染期解析（已发布树、草稿预览、
 * 悬挂引用、停用面板、未声明区域）与后台保存草稿 / 发布 / 丢弃。面板内容解析委托给
 * {@link PanelResolver}（mock）；区域结构由本服务装配；面板持久化经 {@link PlacementService#savePanel}。
 */
class RegionServiceTest {
    private static final Long SITE = 1L;
    private static final String PAGE = "home";
    private static final String REGION = "body";
    private static final String THEME = "1/default";

    private PageRegionMapper mapper;
    private PageRegionSnapshotMapper snapshotMapper;
    private PlacementService placementService;
    private PanelResolver panelResolver;
    private ManifestService manifestService;
    private ShapeLoader shapeLoader;
    private ManifestLoader manifestLoader;
    private CompositionCacheSync cacheSync;
    private RegionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PageRegionMapper.class);
        snapshotMapper = mock(PageRegionSnapshotMapper.class);
        placementService = mock(PlacementService.class);
        panelResolver = mock(PanelResolver.class);
        manifestService = mock(ManifestService.class);
        CompositionSchemas schemas = new CompositionSchemas();
        shapeLoader = new ShapeLoader(schemas);
        manifestLoader = new ManifestLoader(schemas);

        String shapeJson = """
                { "name": "RichHtml", "version": "1.0.0", "titleField": "title",
                  "settings": { "title": { "type": "text", "max": 200 } },
                  "item": { "title": { "type": "text" } } }
                """;
        // 该清单经 page-manifest.schema.json 的 oneOf 校验通过，即同时验证 region 种类的 Schema 正确性
        String manifestJson = """
                { "name": "home", "slots": {
                    "body": { "kind": "region", "allowedShapes": ["RichHtml"],
                              "allowedRowLayouts": ["full", "halves"],
                              "constraints": { "maxRows": 5, "maxPanelsPerColumn": 3 } } } }
                """;
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape(THEME, shapeLoader.load(shapeJson, Shape.SOURCE_THEME_PREFIX + THEME, "x"), "{}", "x")
                .addThemeManifest(THEME, manifestLoader.load(manifestJson, "x"), "{}", "x")
                .build();
        when(manifestService.getSnapshot()).thenReturn(snapshot);
        // 面板解析回显（v2 契约）：每个面板产出一条以其投放ID命名的条目
        when(panelResolver.resolve(any(Placement.class), any(ResolveContext.class)))
                .thenAnswer(inv -> {
                    Placement p = inv.getArgument(0);
                    return new ResolvedRegion.Panel(String.valueOf(p.getId()), p.getPanelShape(), "1.0.0",
                            p.getSettings(), List.of(Map.of("title", "面板" + p.getId())),
                            p.getEnabled() == null || p.getEnabled(), p.getVisibility());
                });

        cacheSync = mock(CompositionCacheSync.class);
        // 模板存储用默认配置（url=/themes）——主题缩略图 URL 解析用（P6/D11）
        ConfigService configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(new Config());
        service = new RegionService(mapper, snapshotMapper, placementService, panelResolver, manifestService,
                schemas, new SnowflakeSequence(0, 0), cacheSync, configService, FieldTypeMatrixTest.registry());
    }

    private Placement panel(long id, boolean enabled) {
        Placement p = new Placement();
        p.setId(id);
        p.setSiteId(SITE);
        p.setPageRef(PAGE);
        p.setSlotName(REGION);
        p.setPanelShape("RichHtml");
        p.setSettings(new HashMap<>());
        p.setEnabled(enabled);
        return p;
    }

    private PageRegion region(String layoutJson, String draftJson) {
        PageRegion r = new PageRegion();
        r.setId(100L);
        r.setSiteId(SITE);
        r.setPageRef(PAGE);
        r.setRegionName(REGION);
        r.setLayoutJson(layoutJson);
        r.setDraftLayoutJson(draftJson);
        r.setVersion(1);
        return r;
    }

    private ResolveContext ctx() {
        return ResolveContext.template(THEME, Map.of("siteId", SITE));
    }

    @Test
    void resolvePublishedTreeExpandsPanelsInOrder() {
        String layout = """
                { "rows": [
                  { "id": "r1", "layout": "full", "columns": [ { "panels": ["10"] } ] },
                  { "id": "r2", "layout": "halves",
                    "columns": [ { "panels": ["20"] }, { "panels": ["30"] } ] }
                ] }
                """;
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(region(layout, null));
        when(placementService.select(10L)).thenReturn(panel(10L, true));
        when(placementService.select(20L)).thenReturn(panel(20L, true));
        when(placementService.select(30L)).thenReturn(panel(30L, true));

        ResolvedRegion resolved = service.resolve(SITE, PAGE, REGION, ctx(), false);

        assertEquals(REGION, resolved.region());
        assertFalse(resolved.draft());
        assertEquals(2, resolved.layout().rows().size());
        ResolvedRegion.Row row0 = resolved.layout().rows().get(0);
        assertEquals("full", row0.layout());
        assertEquals(1, row0.columns().size());
        assertEquals("10", row0.columns().get(0).panels().get(0).id());
        assertEquals("RichHtml", row0.columns().get(0).panels().get(0).component());
        assertEquals(1, row0.columns().get(0).panels().get(0).items().size());
        ResolvedRegion.Row row1 = resolved.layout().rows().get(1);
        assertEquals("halves", row1.layout());
        assertEquals(2, row1.columns().size());
        assertEquals("30", row1.columns().get(1).panels().get(0).id());
    }

    @Test
    void previewUsesDraftTreeWhenPresent() {
        String published = "{ \"rows\": [] }";
        String draft = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["40"] } ] } ] }
                """;
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(region(published, draft));
        when(placementService.select(40L)).thenReturn(panel(40L, true));

        ResolvedRegion live = service.resolve(SITE, PAGE, REGION, ctx(), false);
        assertTrue(live.layout().rows().isEmpty());

        ResolvedRegion preview = service.resolve(SITE, PAGE, REGION,
                ResolveContext.templatePreview(THEME, Map.of("siteId", SITE)), true);
        assertTrue(preview.draft());
        assertEquals(1, preview.layout().rows().size());
        assertEquals("40", preview.layout().rows().get(0).columns().get(0).panels().get(0).id());
    }

    @Test
    void danglingPanelRefIsSkipped() {
        String layout = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["99"] } ] } ] }
                """;
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(region(layout, null));
        when(placementService.select(99L)).thenReturn(null);

        ResolvedRegion resolved = service.resolve(SITE, PAGE, REGION, ctx(), false);
        assertTrue(resolved.layout().rows().get(0).columns().get(0).panels().isEmpty());
    }

    @Test
    void disabledPanelResolvesEmptyAndBypassesSourceResolutionWhenNotPreview() {
        String layout = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["10"] } ] } ] }
                """;
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(region(layout, null));
        when(placementService.select(10L)).thenReturn(panel(10L, false));

        ResolvedRegion resolved = service.resolve(SITE, PAGE, REGION, ctx(), false);
        ResolvedRegion.Panel p = resolved.layout().rows().get(0).columns().get(0).panels().get(0);
        assertFalse(p.enabled());
        assertTrue(p.items().isEmpty());
        assertEquals("RichHtml", p.component());
        assertEquals("1.0.0", p.version());
        // 非预览 + 停用：不调用面板解析（设置/数据模板均不求值）
        verify(panelResolver, never()).resolve(any(), any());
    }

    @Test
    void undeclaredRegionResolvesEmpty() {
        ResolvedRegion resolved = service.resolve(SITE, PAGE, "nope", ctx(), false);
        assertTrue(resolved.layout().rows().isEmpty());
        // 区域行未加载（区域未声明即短路）
        verify(mapper, never()).selectByUnique(any(), any(), any(), any());
    }

    @Test
    void noRegionRowResolvesEmpty() {
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(null);
        assertTrue(service.resolve(SITE, PAGE, REGION, ctx(), false).layout().rows().isEmpty());
    }

    @Test
    void saveDraftUpsertsPanelsAndWritesDraftTree() {
        when(placementService.savePanel(any(Placement.class), any())).thenReturn(9001L);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(null);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of());

        List<RowInput> rows = List.of(new RowInput("r1", "full",
                List.of(new ColumnInput(List.of(
                        new PanelInput(null, "RichHtml", null, true, null))))));
        RegionService.SaveDraftResult result =
                service.saveDraft(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, rows, 0, List.of(THEME));

        ArgumentCaptor<PageRegion> captor = ArgumentCaptor.forClass(PageRegion.class);
        verify(mapper).insert(captor.capture());
        PageRegion saved = captor.getValue();
        assertEquals(List.of("9001"), RegionLayout.parse(saved.getDraftLayoutJson()).panelIds());
        // 新区域的已发布树为空，直到 publish
        assertTrue(RegionLayout.parse(saved.getLayoutJson()).rows().isEmpty());
        verify(placementService).savePanel(any(Placement.class), any());
        // visual-builder §6：返回新版本号 + 遍历序真实面板ID（构建器逐操作串联）
        assertEquals(1, result.version());
        assertEquals(List.of(new RegionService.PanelRef("9001", "RichHtml")), result.panels());
    }

    @Test
    void saveDraftRejectsStructuralErrorBeforeUpsert() {
        // halves 需 2 列，只给 1 列 → 结构校验失败，不应 upsert 任何面板或区域
        List<RowInput> bad = List.of(new RowInput("r1", "halves",
                List.of(new ColumnInput(List.of(
                        new PanelInput(null, "RichHtml", null, true, null))))));
        assertThrows(Http400Exception.class,
                () -> service.saveDraft(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, bad, 0, List.of(THEME)));
        verify(placementService, never()).savePanel(any(), any());
        verify(mapper, never()).insert(any());
        verify(mapper, never()).updateWithLock(any(), anyInt());
    }

    @Test
    void saveDraftStaleVersionConflictsBeforeAnyPanelWrite() {
        // 乐观锁快速失败：区域行 version=1，客户端提交 0（陈旧读取）→ 409，且不建任何面板
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(region("{\"rows\":[]}", null));
        List<RowInput> rows = List.of(new RowInput("r1", "full",
                List.of(new ColumnInput(List.of(
                        new PanelInput(null, "RichHtml", null, true, null))))));
        assertThrows(Http409Exception.class,
                () -> service.saveDraft(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, rows, 0, List.of(THEME)));
        verify(placementService, never()).savePanel(any(), any());
        verify(mapper, never()).insert(any());
        verify(mapper, never()).updateWithLock(any(), anyInt());
    }

    @Test
    void saveDraftGuardedUpdateZeroRowsConflicts() {
        // check-then-act 竞态兜底：快速失败通过（版本相符），但事务内 updateWithLock 0 行 → 409
        when(placementService.savePanel(any(Placement.class), any())).thenReturn(9001L);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(region("{\"rows\":[]}", null));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(0);
        List<RowInput> rows = List.of(new RowInput("r1", "full",
                List.of(new ColumnInput(List.of(
                        new PanelInput(null, "RichHtml", null, true, null))))));
        assertThrows(Http409Exception.class,
                () -> service.saveDraft(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, rows, 1, List.of(THEME)));
    }

    // ---- 写时复制（visual-builder shell-polish D20）：草稿保存不改写被已发布树引用的面板 ----------

    private static final String PUBLISHED_9001 = """
            { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["9001"] } ] } ] }
            """;

    @Test
    void saveDraftCopiesPublishedPanelWhoseContentChanged() {
        // 面板 9001 被已发布树引用，草稿改了设置 → 复制为新投放（新建路径），已发布树仍指向 9001
        PageRegion row = region(PUBLISHED_9001, null);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(row);
        when(mapper.listByRegion(SITE, PAGE, REGION)).thenReturn(List.of(row));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        Placement published = panel(9001L, true);
        published.setOrigin("theme:default");
        published.setOriginId("home#body#r1#0#0");
        published.setOriginHash("h1");
        when(placementService.select(9001L)).thenReturn(published);
        when(placementService.savePanel(any(Placement.class), any())).thenReturn(9002L);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of(published));

        List<RowInput> rows = List.of(new RowInput("r1", "full",
                List.of(new ColumnInput(List.of(
                        new PanelInput(9001L, "RichHtml", Map.of("title", "新标题"), true, null))))));
        RegionService.SaveDraftResult result =
                service.saveDraft(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, rows, 1, List.of(THEME));

        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService).savePanel(panelCaptor.capture(), any());
        Placement copy = panelCaptor.getValue();
        // 新建路径（id 0）：已发布行不被改写；来源三元组随副本，种子谱系与「操作员已改」判定不失真
        assertEquals(0L, copy.getId());
        assertEquals("theme:default", copy.getOrigin());
        assertEquals("home#body#r1#0#0", copy.getOriginId());
        assertEquals("h1", copy.getOriginHash());
        assertEquals("新标题", copy.getSettings().get("title"));
        ArgumentCaptor<PageRegion> captor = ArgumentCaptor.forClass(PageRegion.class);
        verify(mapper).updateWithLock(captor.capture(), eq(1));
        assertEquals(List.of("9002"), RegionLayout.parse(captor.getValue().getDraftLayoutJson()).panelIds());
        assertEquals(List.of("9001"), RegionLayout.parse(captor.getValue().getLayoutJson()).panelIds());
        assertEquals(List.of(new RegionService.PanelRef("9002", "RichHtml")), result.panels());
        // 原行仍被已发布树引用 → 不作孤立清理
        verify(placementService, never()).delete(9001L);
    }

    @Test
    void saveDraftKeepsPublishedPanelWhenContentUnchanged() {
        // 冻结面板内容未变（移动/重排）：保留ID、不写库，但仍复验 Shape/显示设备
        PageRegion row = region(PUBLISHED_9001, null);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(row);
        when(mapper.listByRegion(SITE, PAGE, REGION)).thenReturn(List.of(row));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        Placement published = panel(9001L, true);
        when(placementService.select(9001L)).thenReturn(published);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of(published));

        List<RowInput> rows = List.of(new RowInput("r1", "full",
                List.of(new ColumnInput(List.of(
                        new PanelInput(9001L, "RichHtml", null, true, null))))));
        RegionService.SaveDraftResult result =
                service.saveDraft(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, rows, 1, List.of(THEME));

        verify(placementService, never()).savePanel(any(), any());
        verify(placementService).validate(any(Placement.class), any());
        ArgumentCaptor<PageRegion> captor = ArgumentCaptor.forClass(PageRegion.class);
        verify(mapper).updateWithLock(captor.capture(), eq(1));
        assertEquals(List.of("9001"), RegionLayout.parse(captor.getValue().getDraftLayoutJson()).panelIds());
        assertEquals(List.of(new RegionService.PanelRef("9001", "RichHtml")), result.panels());
    }

    @Test
    void saveDraftUpdatesDraftOnlyPanelInPlace() {
        // 仅草稿树引用（尚未发布）的面板照旧就地更新，不复制
        String draft = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["9001"] } ] } ] }
                """;
        PageRegion row = region("{\"rows\":[]}", draft);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(row);
        when(mapper.listByRegion(SITE, PAGE, REGION)).thenReturn(List.of(row));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        when(placementService.savePanel(any(Placement.class), any())).thenReturn(9001L);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of(panel(9001L, true)));

        List<RowInput> rows = List.of(new RowInput("r1", "full",
                List.of(new ColumnInput(List.of(
                        new PanelInput(9001L, "RichHtml", Map.of("title", "改"), true, null))))));
        service.saveDraft(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, rows, 1, List.of(THEME));

        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService).savePanel(panelCaptor.capture(), any());
        assertEquals(9001L, panelCaptor.getValue().getId());
        verify(placementService, never()).select(any());
    }

    @Test
    void publishAfterCopyOnWriteDeletesReplacedPanel() {
        // 发布翻转树后原行不再被引用 → 孤立清理删除 9001，副本 9002 存续
        String draft = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["9002"] } ] } ] }
                """;
        PageRegion row = region(PUBLISHED_9001, draft);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(row);
        when(mapper.listByRegion(SITE, PAGE, REGION)).thenReturn(List.of(row));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of(panel(9001L, true), panel(9002L, true)));

        service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1, List.of(THEME), "admin", null);

        verify(placementService).delete(9001L);
        verify(placementService, never()).delete(9002L);
    }

    @Test
    void publishCopiesDraftToPublishedAndClearsDraft() {
        String draft = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["9001"] } ] } ] }
                """;
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(region("{\"rows\":[]}", draft));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of());

        service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1, List.of(THEME),
                "admin", null);

        ArgumentCaptor<PageRegion> captor = ArgumentCaptor.forClass(PageRegion.class);
        verify(mapper).updateWithLock(captor.capture(), eq(1));
        PageRegion saved = captor.getValue();
        assertEquals(List.of("9001"), RegionLayout.parse(saved.getLayoutJson()).panelIds());
        assertNull(saved.getDraftLayoutJson());
    }

    @Test
    void publishWithoutDraftThrows() {
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(region("{\"rows\":[]}", null));
        assertThrows(Http400Exception.class,
                () -> service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1,
                        List.of(THEME), "admin", null));
    }

    @Test
    void publishStaleVersionConflicts() {
        String draft = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["9001"] } ] } ] }
                """;
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(region("{\"rows\":[]}", draft));
        assertThrows(Http409Exception.class,
                () -> service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 0,
                        List.of(THEME), "admin", null));
        verify(mapper, never()).updateWithLock(any(), anyInt());
    }

    @Test
    void paletteShapesUnionsAllowedShapesResolvesAndFlagsMissing() {
        // 区域 allowedShapes 含可解析（RichHtml，带 presentation）与不可解析（GhostShape）的名字；
        // 另有一个既有面板携带已不被允许的 LegacyShape —— 也须并入（供标签显示，控制台置灰）
        String shapeJson = """
                { "name": "RichHtml", "version": "1.0.0", "titleField": "title",
                  "presentation": { "icon": "text", "group": { "zh-CN": "通用组件" }, "order": 10 },
                  "item": { "title": { "type": "text" } } }
                """;
        String manifestJson = """
                { "name": "home", "slots": {
                    "body": { "kind": "region", "allowedShapes": ["RichHtml", "GhostShape"],
                              "allowedRowLayouts": ["full"] } } }
                """;
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape(THEME, shapeLoader.load(shapeJson, Shape.SOURCE_THEME_PREFIX + THEME, "x"), "{}", "x")
                .addThemeManifest(THEME, manifestLoader.load(manifestJson, "x"), "{}", "x")
                .build();
        when(manifestService.getSnapshot()).thenReturn(snapshot);
        Placement legacy = panel(77L, true);
        legacy.setPanelShape("LegacyShape");
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of(legacy));

        List<RegionService.PaletteShape> shapes =
                service.paletteShapes(SITE, PAGE, List.of(THEME));

        assertEquals(List.of("RichHtml", "GhostShape", "LegacyShape"),
                shapes.stream().map(RegionService.PaletteShape::name).toList());
        RegionService.PaletteShape richHtml = shapes.get(0);
        assertFalse(richHtml.missing());
        assertEquals(Shape.SOURCE_THEME_PREFIX + THEME, richHtml.source());
        assertEquals("text", richHtml.presentation().get("icon"));
        assertTrue(shapes.get(1).missing());
        assertTrue(shapes.get(2).missing());
    }


    @Test
    void paletteShapesResolveThemeThumbnails() {
        // 缩略图服务端解析（P6/D11）：相对路径 → 模板存储公开前缀 + 主题键（theme-owned-shapes D15 起唯一规则）；
        // 穿越路径（加载期 Schema 已拒，此处手工构造 Shape 验证纵深防御）→ null 回退 icon
        String themeKey = "1/liquid-default";
        String themeShape = """
                { "name": "ThemeThumb", "version": "1.0.0",
                  "presentation": { "thumbnail": "thumbnails/theme-thumb.svg" },
                  "item": { "t": { "type": "text" } } }
                """;
        Shape evil = new Shape("EvilThumb", "1.0.0", null, Map.of(), List.of(), null, Map.of(),
                List.of(), List.of(), Shape.SOURCE_THEME_PREFIX + themeKey,
                Map.of("presentation", Map.of("thumbnail", "../evil.svg")));
        String manifestJson = """
                { "name": "home", "slots": {
                    "body": { "kind": "region", "allowedShapes": ["ThemeThumb", "EvilThumb"],
                              "allowedRowLayouts": ["full"] } } }
                """;
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape(themeKey, shapeLoader.load(themeShape, Shape.SOURCE_THEME_PREFIX + themeKey, "x"),
                        "{}", "x")
                .addThemeShape(themeKey, evil, "{}", "x")
                .addThemeManifest(themeKey, manifestLoader.load(manifestJson, "x"), "{}", "x")
                .build();
        when(manifestService.getSnapshot()).thenReturn(snapshot);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of());

        List<RegionService.PaletteShape> shapes =
                service.paletteShapes(SITE, PAGE, List.of(themeKey));

        assertEquals("/themes/1/liquid-default/thumbnails/theme-thumb.svg", shapes.get(0).thumbnail());
        assertNull(shapes.get(1).thumbnail());
    }

    @Test
    void paletteShapesDataUriThumbnailPassesThrough() {
        // data:image/ URI 任意命名空间直通（P6/D11）
        String dataUri = "data:image/svg+xml;base64,PHN2Zy8+";
        String dataShape = """
                { "name": "DataThumb", "version": "1.0.0",
                  "presentation": { "thumbnail": "%s" },
                  "item": { "t": { "type": "text" } } }
                """.formatted(dataUri);
        String manifestJson = """
                { "name": "home", "slots": {
                    "body": { "kind": "region", "allowedShapes": ["DataThumb"],
                              "allowedRowLayouts": ["full"] } } }
                """;
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape(THEME, shapeLoader.load(dataShape, Shape.SOURCE_THEME_PREFIX + THEME, "x"), "{}", "x")
                .addThemeManifest(THEME, manifestLoader.load(manifestJson, "x"), "{}", "x")
                .build();
        when(manifestService.getSnapshot()).thenReturn(snapshot);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of());

        List<RegionService.PaletteShape> shapes =
                service.paletteShapes(SITE, PAGE, List.of(THEME));

        assertEquals(dataUri, shapes.get(0).thumbnail());
    }

    @Test
    void listDeclaredRegionsFiltersByPage() {
        // 页面过滤（页面聚合详情用）：只查所过滤页面的区域行
        String otherManifest = """
                { "name": "channel", "slots": {
                    "side": { "kind": "region", "allowedShapes": ["RichHtml"],
                              "allowedRowLayouts": ["full"] } } }
                """;
        String homeManifest = """
                { "name": "home", "slots": {
                    "body": { "kind": "region", "allowedShapes": ["RichHtml"],
                              "allowedRowLayouts": ["full"] } } }
                """;
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeManifest(THEME, manifestLoader.load(homeManifest, "x"), "{}", "x")
                .addThemeManifest(THEME, manifestLoader.load(otherManifest, "x"), "{}", "x")
                .build();
        when(manifestService.getSnapshot()).thenReturn(snapshot);

        List<RegionService.RegionSummary> filtered =
                service.listDeclaredRegions(SITE, PAGE, List.of(THEME));
        assertEquals(1, filtered.size());
        assertEquals(PAGE, filtered.get(0).page());
        verify(mapper, never()).selectByUnique(SITE, "channel", "side", PageRegion.SCOPE_DEFAULT);

        // 不过滤仍返回全部
        assertEquals(2, service.listDeclaredRegions(SITE, List.of(THEME)).size());
    }

    @Test
    void discardClearsDraftAndCleansUpOrphans() {
        String draft = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["9001"] } ] } ] }
                """;
        PageRegion row = region("{\"rows\":[]}", draft);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(row);
        when(mapper.listByRegion(SITE, PAGE, REGION)).thenReturn(List.of(row));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        // 草稿引用的 9001 在丢弃后既不被已发布树也不被草稿树引用 → 作为孤立面板删除
        Placement orphan = panel(9001L, true);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of(orphan));

        service.discard(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1);

        ArgumentCaptor<PageRegion> captor = ArgumentCaptor.forClass(PageRegion.class);
        verify(mapper).updateWithLock(captor.capture(), eq(1));
        assertNull(captor.getValue().getDraftLayoutJson());
        verify(placementService).delete(9001L);
    }

    // ---- 范围覆盖（visual-builder §9 / B P4） ------------------------------------------------------

    private PageRegion scopedRegion(String scope, String layoutJson) {
        PageRegion r = region(layoutJson, null);
        r.setId(200L);
        r.setScope(scope);
        return r;
    }

    @Test
    void resolvePrefersChannelScopeOverVariantThenDefault() {
        String channelLayout = """
                { "rows": [ { "id": "rc", "layout": "full", "columns": [ { "panels": ["10"] } ] } ] }
                """;
        when(placementService.select(10L)).thenReturn(panel(10L, true));
        ResolveContext ctx = ResolveContext.template(THEME, Map.of("siteId", SITE, "channelId", 5L));
        // 频道覆盖行存在 → 命中 channel:5，变体与默认不再点查
        when(mapper.selectByUnique(SITE, PAGE, REGION, "channel:5"))
                .thenReturn(scopedRegion("channel:5", channelLayout));

        ResolvedRegion resolved = service.resolve(SITE, PAGE, REGION, ctx, false, "channel:magazine");
        assertEquals("channel:5", resolved.scope());
        assertEquals("rc", resolved.layout().rows().get(0).id());
        verify(mapper, never()).selectByUnique(SITE, PAGE, REGION,
                "variant:channel:magazine");
        verify(mapper, never()).selectByUnique(SITE, PAGE, REGION,
                PageRegion.SCOPE_DEFAULT);
    }

    @Test
    void resolveFallsBackVariantThenDefault() {
        String variantLayout = """
                { "rows": [ { "id": "rv", "layout": "full", "columns": [ { "panels": [] } ] } ] }
                """;
        ResolveContext ctx = ResolveContext.template(THEME, Map.of("siteId", SITE, "channelId", 5L));
        // 频道覆盖行不存在 → 落到变体覆盖
        when(mapper.selectByUnique(SITE, PAGE, REGION, "channel:5")).thenReturn(null);
        when(mapper.selectByUnique(SITE, PAGE, REGION, "variant:channel:magazine"))
                .thenReturn(scopedRegion("variant:channel:magazine", variantLayout));

        ResolvedRegion resolved = service.resolve(SITE, PAGE, REGION, ctx, false, "channel:magazine");
        assertEquals("variant:channel:magazine", resolved.scope());
        assertEquals("rv", resolved.layout().rows().get(0).id());

        // 变体覆盖也不存在 → 页面默认
        when(mapper.selectByUnique(SITE, PAGE, REGION, "variant:channel:magazine"))
                .thenReturn(null);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(region("{\"rows\":[]}", null));
        assertEquals(PageRegion.SCOPE_DEFAULT,
                service.resolve(SITE, PAGE, REGION, ctx, false, "channel:magazine").scope());
    }

    @Test
    void resolveWithoutScopeContextQueriesDefaultOnly() {
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(region("{\"rows\":[]}", null));
        ResolvedRegion resolved = service.resolve(SITE, PAGE, REGION, ctx(), false);
        assertEquals(PageRegion.SCOPE_DEFAULT, resolved.scope());
        verify(mapper).selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT);
    }

    @Test
    void emptyScopedTreeShadowsDefaultEntirely() {
        // 覆盖行空树 =「该频道刻意为空」：完全遮蔽默认行，不回退（D6 defaults+overrides 语义）
        ResolveContext ctx = ResolveContext.template(THEME, Map.of("siteId", SITE, "channelId", 5L));
        when(mapper.selectByUnique(SITE, PAGE, REGION, "channel:5"))
                .thenReturn(scopedRegion("channel:5", "{\"rows\":[]}"));
        ResolvedRegion resolved = service.resolve(SITE, PAGE, REGION, ctx, false, null);
        assertEquals("channel:5", resolved.scope());
        assertTrue(resolved.layout().rows().isEmpty());
        verify(mapper, never()).selectByUnique(SITE, PAGE, REGION,
                PageRegion.SCOPE_DEFAULT);
    }

    @Test
    void saveDraftCreatingOverrideStripsPanelIds() {
        // 创建覆盖：客户端提交默认树（面板带ID 10）→ 服务端剥离ID复制为新投放，默认行面板不被改写
        when(placementService.savePanel(any(Placement.class), any())).thenReturn(9002L);
        when(mapper.selectByUnique(SITE, PAGE, REGION, "channel:5")).thenReturn(null);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of());

        List<RowInput> rows = List.of(new RowInput("r1", "full",
                List.of(new ColumnInput(List.of(
                        new PanelInput(10L, "RichHtml", null, true, null))))));
        service.saveDraft(SITE, PAGE, REGION, "channel:5", rows, 0, List.of(THEME));

        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService).savePanel(panelCaptor.capture(), any());
        // ID 已剥离：toBean 不再设置 id（实体缺省 0 = savePanel 的新建路径），不会改写默认行的面板 10
        assertEquals(0L, panelCaptor.getValue().getId());
        ArgumentCaptor<PageRegion> captor = ArgumentCaptor.forClass(PageRegion.class);
        verify(mapper).insert(captor.capture());
        assertEquals("channel:5", captor.getValue().getScope());
        assertEquals(List.of("9002"), RegionLayout.parse(captor.getValue().getDraftLayoutJson()).panelIds());
    }

    @Test
    void saveDraftExistingOverrideKeepsPanelIds() {
        // 覆盖行已存在：正常 upsert（面板ID保留，归属校验在 savePanel 内）
        when(placementService.savePanel(any(Placement.class), any())).thenReturn(9002L);
        when(mapper.selectByUnique(SITE, PAGE, REGION, "channel:5"))
                .thenReturn(scopedRegion("channel:5", "{\"rows\":[]}"));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of());

        List<RowInput> rows = List.of(new RowInput("r1", "full",
                List.of(new ColumnInput(List.of(
                        new PanelInput(9002L, "RichHtml", null, true, null))))));
        service.saveDraft(SITE, PAGE, REGION, "channel:5", rows, 1, List.of(THEME));

        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService).savePanel(panelCaptor.capture(), any());
        assertEquals(9002L, panelCaptor.getValue().getId());
    }

    @Test
    void saveDraftRejectsInvalidScope() {
        List<RowInput> rows = List.of();
        assertThrows(Http400Exception.class,
                () -> service.saveDraft(SITE, PAGE, REGION, "channel:abc", rows, 0,
                        List.of()));
        assertThrows(Http400Exception.class,
                () -> service.saveDraft(SITE, PAGE, REGION, "bogus", rows, 0, List.of(THEME)));
    }

    @Test
    void cleanupUnionsReferencesAcrossScopeRows() {
        // 保存覆盖行草稿后清理孤立面板：默认行仍引用的 10 不得删除；无任何范围引用的 77 删除
        when(placementService.savePanel(any(Placement.class), any())).thenReturn(9002L);
        PageRegion defaultRow = region(
                "{ \"rows\": [ { \"id\": \"r1\", \"layout\": \"full\", \"columns\": [ { \"panels\": [\"10\"] } ] } ] }",
                null);
        PageRegion overrideRow = scopedRegion("channel:5", "{\"rows\":[]}");
        when(mapper.selectByUnique(SITE, PAGE, REGION, "channel:5"))
                .thenReturn(overrideRow);
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        when(mapper.listByRegion(SITE, PAGE, REGION))
                .thenReturn(List.of(defaultRow, overrideRow));
        when(placementService.listByPage(SITE, PAGE))
                .thenReturn(List.of(panel(10L, true), panel(77L, true), panel(9002L, true)));

        List<RowInput> rows = List.of(new RowInput("r1", "full",
                List.of(new ColumnInput(List.of(
                        new PanelInput(9002L, "RichHtml", null, true, null))))));
        service.saveDraft(SITE, PAGE, REGION, "channel:5", rows, 1, List.of(THEME));

        verify(placementService, never()).delete(10L);
        verify(placementService).delete(77L);
        // 9002 被覆盖行草稿引用（listByRegion 返回的 overrideRow 实例在 upsert 中被写入草稿树）
        verify(placementService, never()).delete(9002L);
    }

    @Test
    void getDetailForAbsentOverrideSeedsFromDefault() {
        String defaultLayout = """
                { "rows": [ { "id": "rd", "layout": "full", "columns": [ { "panels": ["10"] } ] } ] }
                """;
        when(mapper.selectByUnique(SITE, PAGE, REGION, "channel:5")).thenReturn(null);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(region(defaultLayout, null));
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of(panel(10L, true)));

        RegionService.RegionDetail detail =
                service.getDetail(SITE, PAGE, REGION, "channel:5", List.of(THEME));
        assertEquals("channel:5", detail.scope());
        assertFalse(detail.overrideExists());
        assertEquals(0, detail.version());
        assertEquals("rd", detail.published().rows().get(0).id());
        assertNull(detail.draft());
    }

    @Test
    void deleteOverrideRemovesRowAndOrphansAndRejectsDefault() {
        PageRegion overrideRow = scopedRegion("channel:5",
                "{ \"rows\": [ { \"id\": \"r1\", \"layout\": \"full\", \"columns\": [ { \"panels\": [\"9002\"] } ] } ] }");
        when(mapper.selectByUnique(SITE, PAGE, REGION, "channel:5"))
                .thenReturn(overrideRow);
        // 删除后清理：仅默认行存续，9002 无引用 → 删除
        PageRegion defaultRow = region("{\"rows\":[]}", null);
        when(mapper.listByRegion(SITE, PAGE, REGION)).thenReturn(List.of(defaultRow));
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of(panel(9002L, true)));

        service.deleteOverride(SITE, PAGE, REGION, "channel:5", 1, List.of(THEME), "admin");
        verify(mapper).delete(200L);
        verify(placementService).delete(9002L);
        verify(cacheSync).invalidateAll("page-region");

        assertThrows(Http400Exception.class,
                () -> service.deleteOverride(SITE, PAGE, REGION,
                        PageRegion.SCOPE_DEFAULT, 1, List.of(THEME), "admin"));
    }

    @Test
    void publishNotifiesCacheSync() {
        String draft = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["9001"] } ] } ] }
                """;
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(region("{\"rows\":[]}", draft));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of());

        service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1, List.of(THEME),
                "admin", null);
        verify(cacheSync).invalidateAll("page-region");
    }

    /** 归档面板删除树先行（D21）：先从已发布树与草稿树剔除引用再删投放行，不产生悬空引用 */
    @Test
    void deleteArchivedPanelPrunesTreesBeforeRowDelete() {
        String published = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["10", "11"] } ] } ] }
                """;
        String draft = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["10"] } ] } ] }
                """;
        PageRegion row = region(published, draft);
        when(mapper.listByRegion(SITE, PAGE, REGION)).thenReturn(List.of(row));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        Placement archived = panel(10, true);
        archived.setArchivedAt(java.time.OffsetDateTime.now());

        service.deleteArchivedPanel(archived);

        ArgumentCaptor<PageRegion> captor = ArgumentCaptor.forClass(PageRegion.class);
        verify(mapper).updateWithLock(captor.capture(), eq(1));
        assertEquals(List.of("11"), RegionLayout.parse(captor.getValue().getLayoutJson()).panelIds());
        assertEquals(List.of(), RegionLayout.parse(captor.getValue().getDraftLayoutJson()).panelIds());
        // 行/列结构原样保留（空列合法）
        assertEquals(1, RegionLayout.parse(captor.getValue().getDraftLayoutJson()).rows().size());
        verify(placementService).delete(10L);
        verify(cacheSync).invalidateAll("page-region");
    }

    /** 清空归档面板（D21）：按区域分组树先行剔除后删除全部归档行，返回删除数 */
    @Test
    void deleteArchivedPanelsPrunesAndDeletesAllArchived() {
        String published = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["10", "12"] } ] } ] }
                """;
        PageRegion row = region(published, null);
        when(mapper.listByRegion(SITE, PAGE, REGION)).thenReturn(List.of(row));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        Placement archived = panel(10, true);
        archived.setArchivedAt(java.time.OffsetDateTime.now());
        Placement live = panel(12, true);
        when(placementService.listBySiteId(SITE)).thenReturn(List.of(archived, live));

        assertEquals(1, service.deleteArchivedPanels(SITE));

        ArgumentCaptor<PageRegion> captor = ArgumentCaptor.forClass(PageRegion.class);
        verify(mapper).updateWithLock(captor.capture(), eq(1));
        assertEquals(List.of("12"), RegionLayout.parse(captor.getValue().getLayoutJson()).panelIds());
        verify(placementService).delete(10L);
        verify(placementService, never()).delete(12L);
    }

    /** 清理悬空引用（显式修复）：只剔指向不存在投放行的引用；归档面板的行存活，引用保留 */
    @Test
    void pruneDanglingRefsRemovesOnlyRefsToMissingPanels() {
        String published = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["10", "11", "12"] } ] } ] }
                """;
        PageRegion row = region(published, null);
        when(mapper.listBySiteId(SITE)).thenReturn(List.of(row));
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        Placement live = panel(11, true);
        Placement archived = panel(12, true);
        archived.setArchivedAt(java.time.OffsetDateTime.now());
        // 10 已不存在（带外删除残留）；11 在用；12 归档但行存活
        when(placementService.listBySiteId(SITE)).thenReturn(List.of(live, archived));

        assertEquals(1, service.pruneDanglingRefs(SITE));

        ArgumentCaptor<PageRegion> captor = ArgumentCaptor.forClass(PageRegion.class);
        verify(mapper).updateWithLock(captor.capture(), eq(1));
        assertEquals(List.of("11", "12"), RegionLayout.parse(captor.getValue().getLayoutJson()).panelIds());
        verify(placementService, never()).delete(any());
        verify(cacheSync).invalidateAll("page-region");
    }

    // ---- 共享区域（page-regions §15）----

    /** 共享区域快照：无模板的 site 宿主完整声明 body，home 以 shared 引用之。 */
    private void useSharedRegionSnapshot() {
        String shapeJson = """
                { "name": "RichHtml", "version": "1.0.0", "titleField": "title",
                  "settings": { "title": { "type": "text", "max": 200 } },
                  "item": { "title": { "type": "text" } } }
                """;
        String siteJson = """
                { "name": "site", "slots": {
                    "body": { "kind": "region", "allowedShapes": ["RichHtml"],
                              "label": { "zh-CN": "主体区域", "en": "Body" }, "description": "宿主描述",
                              "allowedRowLayouts": ["full", "halves"],
                              "constraints": { "maxRows": 5, "maxPanelsPerColumn": 3 } } } }
                """;
        String homeJson = """
                { "name": "home", "template": "index",
                  "slots": { "body": { "kind": "region", "shared": "site" } } }
                """;
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape(THEME, shapeLoader.load(shapeJson, Shape.SOURCE_THEME_PREFIX + THEME, "x"), "{}", "x")
                .addThemeManifest(THEME, manifestLoader.load(siteJson, "site"), "{}", "site")
                .addThemeManifest(THEME, manifestLoader.load(homeJson, "home"), "{}", "home")
                .build();
        when(manifestService.getSnapshot()).thenReturn(snapshot);
    }

    private static final String OWNER_LAYOUT = """
            { "rows": [ { "id": "ro", "layout": "full", "columns": [ { "panels": ["10"] } ] } ] }
            """;

    private PageRegion ownerRegion(String layoutJson) {
        PageRegion r = region(layoutJson, null);
        r.setId(300L);
        r.setPageRef("site");
        r.setVersion(4);
        return r;
    }

    private Placement ownerPanel(long id) {
        Placement p = panel(id, true);
        p.setPageRef("site");
        return p;
    }

    @Test
    void resolveOnUnforkedFollowerFallsBackToOwnerDefaultRow() {
        useSharedRegionSnapshot();
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(null);
        when(mapper.selectByUnique(SITE, "site", REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(ownerRegion(OWNER_LAYOUT));
        when(placementService.select(10L)).thenReturn(ownerPanel(10L));

        ResolvedRegion resolved = service.resolve(SITE, PAGE, REGION, ctx(), false);
        assertEquals("ro", resolved.layout().rows().get(0).id());
        assertEquals("10", resolved.layout().rows().get(0).columns().get(0).panels().get(0).id());
    }

    @Test
    void resolveOnForkedFollowerUsesItsOwnRowWithoutTouchingOwner() {
        useSharedRegionSnapshot();
        String pageLayout = """
                { "rows": [ { "id": "rp", "layout": "full", "columns": [ { "panels": ["11"] } ] } ] }
                """;
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(region(pageLayout, null));
        when(placementService.select(11L)).thenReturn(panel(11L, true));

        ResolvedRegion resolved = service.resolve(SITE, PAGE, REGION, ctx(), false);
        assertEquals("rp", resolved.layout().rows().get(0).id());
        verify(mapper, never()).selectByUnique(SITE, "site", REGION, PageRegion.SCOPE_DEFAULT);
    }

    @Test
    void getDetailOnUnforkedFollowerReturnsOwnerCoordinate() {
        useSharedRegionSnapshot();
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(null);
        when(mapper.selectByUnique(SITE, "site", REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(ownerRegion(OWNER_LAYOUT));
        when(placementService.listByPage(SITE, "site")).thenReturn(List.of(ownerPanel(10L)));

        RegionService.RegionDetail detail =
                service.getDetail(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, List.of(THEME));
        // 详情即宿主行：坐标、版本、树、面板都是 site 的；控制台此后直接对 site 坐标读写
        assertEquals("site", detail.page());
        assertEquals("site", detail.sharedFrom());
        assertEquals("Body", detail.label().get("en"));
        assertEquals("宿主描述", detail.description());
        assertFalse(detail.forked());
        assertTrue(detail.overrideExists());
        assertEquals(4, detail.version());
        assertEquals("ro", detail.published().rows().get(0).id());
        assertEquals(List.of("10"), detail.panels().stream().map(RegionService.PanelDetail::id).toList());
    }

    @Test
    void getDetailOnForkedFollowerReturnsPageRow() {
        useSharedRegionSnapshot();
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(region("{\"rows\":[]}", null));
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of());

        RegionService.RegionDetail detail =
                service.getDetail(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, List.of(THEME));
        assertEquals(PAGE, detail.page());
        assertEquals("site", detail.sharedFrom());
        assertTrue(detail.forked());
        assertEquals(1, detail.version());
        verify(mapper, never()).selectByUnique(SITE, "site", REGION, PageRegion.SCOPE_DEFAULT);
    }

    @Test
    void getDetailForChannelOverrideOnUnforkedFollowerSeedsFromOwner() {
        useSharedRegionSnapshot();
        when(mapper.selectByUnique(SITE, PAGE, REGION, "channel:5")).thenReturn(null);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(null);
        when(mapper.selectByUnique(SITE, "site", REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(ownerRegion(OWNER_LAYOUT));
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of());

        RegionService.RegionDetail detail = service.getDetail(SITE, PAGE, REGION, "channel:5", List.of(THEME));
        assertEquals(PAGE, detail.page());
        assertEquals("channel:5", detail.scope());
        assertFalse(detail.overrideExists());
        assertFalse(detail.forked());
        assertEquals(0, detail.version());
        assertEquals("ro", detail.published().rows().get(0).id());
    }

    @Test
    void listDeclaredRegionsMarksSharedAndForkedState() {
        useSharedRegionSnapshot();
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(null);
        when(mapper.selectByUnique(SITE, "site", REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(ownerRegion(OWNER_LAYOUT));

        List<RegionService.RegionSummary> all = service.listDeclaredRegions(SITE, List.of(THEME));
        RegionService.RegionSummary owner = all.stream().filter(s -> s.page().equals("site")).findFirst().orElseThrow();
        RegionService.RegionSummary follower = all.stream().filter(s -> s.page().equals(PAGE)).findFirst().orElseThrow();
        assertNull(owner.sharedFrom());
        assertFalse(owner.forked());
        assertEquals("site", follower.sharedFrom());
        assertFalse(follower.forked());
        // 未分叉的跟随页目录状态反映宿主行
        assertEquals(1, follower.publishedRows());
        assertEquals(List.of("RichHtml"), follower.allowedShapes());
        // 显示名/描述随宿主定义复制（SlotDef.asSharedFrom 透传）
        assertEquals("主体区域", follower.label().get("zh-CN"));
        assertEquals("宿主描述", follower.description());
    }

    @Test
    void saveDraftForkingFollowerStripsOwnerPanelIds() {
        useSharedRegionSnapshot();
        when(placementService.savePanel(any(Placement.class), any())).thenReturn(9003L);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(null);
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of());

        // 分叉：客户端提交宿主树（面板带宿主行的ID 10）→ 建行即剥离ID复制为本页新投放，宿主面板不被改写
        List<RowInput> rows = List.of(new RowInput("r1", "full",
                List.of(new ColumnInput(List.of(new PanelInput(10L, "RichHtml", null, true, null))))));
        service.saveDraft(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, rows, 0, List.of(THEME));

        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService).savePanel(panelCaptor.capture(), any());
        assertEquals(0L, panelCaptor.getValue().getId());
        assertEquals(PAGE, panelCaptor.getValue().getPageRef());
        ArgumentCaptor<PageRegion> captor = ArgumentCaptor.forClass(PageRegion.class);
        verify(mapper).insert(captor.capture());
        assertEquals(PAGE, captor.getValue().getPageRef());
        assertEquals(PageRegion.SCOPE_DEFAULT, captor.getValue().getScope());
        assertEquals(List.of("9003"), RegionLayout.parse(captor.getValue().getDraftLayoutJson()).panelIds());
    }

    @Test
    void saveDraftRejectsNonDefaultScopeOnOwnerPage() {
        useSharedRegionSnapshot();
        List<RowInput> rows = List.of();
        assertThrows(Http400Exception.class,
                () -> service.saveDraft(SITE, "site", REGION, "channel:5", rows, 0, List.of(THEME)));
        verify(mapper, never()).insert(any());
    }

    @Test
    void deleteOverrideUnforksFollowerDefaultRowButNotOwnerRow() {
        useSharedRegionSnapshot();
        PageRegion forked = region("""
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["9002"] } ] } ] }
                """, null);
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT)).thenReturn(forked);
        when(mapper.listByRegion(SITE, PAGE, REGION)).thenReturn(List.of());
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of(panel(9002L, true)));

        service.deleteOverride(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1, List.of(THEME), "admin");
        verify(mapper).delete(100L);
        verify(placementService).delete(9002L);
        verify(cacheSync).invalidateAll("page-region");

        // 宿主页的默认行仍不可删
        when(mapper.selectByUnique(SITE, "site", REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(ownerRegion("{\"rows\":[]}"));
        assertThrows(Http400Exception.class,
                () -> service.deleteOverride(SITE, "site", REGION, PageRegion.SCOPE_DEFAULT, 4,
                        List.of(THEME), "admin"));
    }
}
