package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.PageManifest.SlotDef;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.PageRegion;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.RegistryReloadMapper;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.ChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 构成健康服务测试（§11.5，component-settings Phase D 起为面板口径）：面板设置的失效栏目/区块引用
 * （类型化边界解析为空）、组件不可解析的面板、孤立/归档投放、区域结构树的孤立面板/悬空引用、
 * 空的已声明区域。
 */
class CompositionHealthServiceTest {
    private static final Long SITE_ID = 1L;
    private static final String THEME = "1/default";

    private ManifestService manifestService;
    private PlacementService placementService;
    private ChannelService channelService;
    private BlockService blockService;
    private PageRegionMapper pageRegionMapper;
    private CompositionHealthService service;

    @BeforeEach
    void setUp() {
        manifestService = mock(ManifestService.class);
        placementService = mock(PlacementService.class);
        channelService = mock(ChannelService.class);
        blockService = mock(BlockService.class);
        RegistryReloadMapper registryReloadMapper = mock(RegistryReloadMapper.class);
        pageRegionMapper = mock(PageRegionMapper.class);
        when(pageRegionMapper.listBySiteId(SITE_ID)).thenReturn(List.of());

        SettingsResolver settingsResolver = new SettingsResolver(mock(ArticleService.class), channelService,
                blockService, mock(com.ujcms.cms.ext.service.VoteService.class));
        service = new CompositionHealthService(manifestService, placementService, settingsResolver,
                registryReloadMapper, pageRegionMapper);
    }

    /** 主题命名空间快照：RankedList 组件（channelRef + blockRef 设置）+ home 清单（live 区域）。 */
    private RegistrySnapshot themeSnapshot() {
        CompositionSchemas schemas = new CompositionSchemas();
        Shape rankedList = new ShapeLoader(schemas).load("""
                { "name": "RankedList", "version": "2.0.0",
                  "settings": {
                    "channel": { "type": "channelRef" },
                    "block":   { "type": "blockRef" }
                  },
                  "data": { "from": "article", "where": { "channel": "$settings.channel" } },
                  "item": { "headline": { "type": "text" } } }
                """, Shape.SOURCE_THEME_PREFIX + THEME, "x");
        PageManifest home = new ManifestLoader(schemas).load("""
                { "name": "home", "slots": {
                    "live": { "kind": "region", "allowedShapes": ["RankedList"] } } }
                """, "home.manifest.json");
        return RegistrySnapshot.builder()
                .addThemeShape(THEME, rankedList, "{}", "x")
                .addThemeManifest(THEME, home, "{}", "home.manifest.json")
                .build();
    }

    private Placement panel(long id, String page, String region, String component, Map<String, Object> settings) {
        Placement placement = new Placement();
        placement.setId(id);
        placement.setSiteId(SITE_ID);
        placement.setPageRef(page);
        placement.setSlotName(region);
        placement.setPanelShape(component);
        placement.setSettings(new HashMap<>(settings));
        placement.setEnabled(true);
        return placement;
    }

    private PageRegion region(long id, String page, String regionName, List<String> panelIds) {
        RegionLayout layout = new RegionLayout(List.of(new RegionLayout.Row("r1", "full",
                List.of(new RegionLayout.Column(panelIds)))));
        PageRegion region = new PageRegion();
        region.setId(id);
        region.setSiteId(SITE_ID);
        region.setPageRef(page);
        region.setRegionName(regionName);
        region.setLayoutJson(layout.toJson());
        return region;
    }

    @Test
    void aggregatesPanelFindings() {
        when(manifestService.getSnapshot()).thenReturn(themeSnapshot());
        // 栏目 12 存在且属本站（健康引用）；999 已删除；区块 888 已删除
        Channel news = new Channel();
        news.setId(12L);
        news.setSiteId(SITE_ID);
        news.setSlug("news");
        news.setName("新闻");
        when(channelService.select(12L)).thenReturn(news);
        when(channelService.select(999L)).thenReturn(null);
        when(blockService.select(888L)).thenReturn(null);

        // 面板 10：channelRef 与 blockRef 均已失效；面板 11：健康引用；面板 12：组件不可解析；
        // 投放 20：归档（可恢复）；投放 21：页面/区域不再被声明（孤立）
        Placement stale = panel(10L, "home", "live", "RankedList", Map.of("channel", 999, "block", 888));
        stale.setOrigin("theme:default");
        Placement healthy = panel(11L, "home", "live", "RankedList", Map.of("channel", 12));
        Placement ghost = panel(12L, "home", "live", "GhostShape", Map.of());
        Placement archived = panel(20L, "home", "live", "RankedList", Map.of());
        archived.setArchivedAt(OffsetDateTime.now());
        Placement orphaned = panel(21L, "home", "ghost", "RankedList", Map.of());
        when(placementService.listBySiteId(SITE_ID)).thenReturn(List.of(stale, healthy, ghost, archived, orphaned));

        // 结构树引用全部面板ID → 无孤立面板/悬空引用噪音；home/live 已发布树非空 → 无空区域
        PageRegion live = region(1L, "home", "live", List.of("10", "11", "12", "20"));
        PageRegion ghostRegion = region(2L, "home", "ghost", List.of("21"));
        when(pageRegionMapper.listBySiteId(SITE_ID)).thenReturn(List.of(live, ghostRegion));
        when(pageRegionMapper.selectByUnique(SITE_ID, "home", "live",
                PageRegion.SCOPE_DEFAULT)).thenReturn(live);

        CompositionHealthReport report = service.report(SITE_ID, List.of(THEME));

        // 失效引用（SettingsResolver.staleRefs 口径，D24）：设置有效值非空、经类型化边界解析后引擎值为空
        // （目标已删除/越站）——一张表按 kind 区分，附面板来源标记（主题种子面板可自动修复）
        assertEquals(2, report.stalePanelRefs().size());
        CompositionHealthReport.StalePanelRef staleChannel = report.stalePanelRefs().stream()
                .filter(ref -> "channel".equals(ref.setting())).findFirst().orElseThrow();
        assertEquals("10", staleChannel.panelId());
        assertEquals("RankedList", staleChannel.component());
        assertEquals(SettingsResolver.KIND_CHANNEL, staleChannel.kind());
        assertEquals("999", staleChannel.targetRef());
        assertEquals("theme:default", staleChannel.origin());
        CompositionHealthReport.StalePanelRef staleBlock = report.stalePanelRefs().stream()
                .filter(ref -> "block".equals(ref.setting())).findFirst().orElseThrow();
        assertEquals("10", staleBlock.panelId());
        assertEquals(SettingsResolver.KIND_BLOCK, staleBlock.kind());
        assertEquals("888", staleBlock.targetRef());
        assertNull(report.stalePanelRefs().stream().filter(ref -> !"10".equals(ref.panelId()))
                .findFirst().orElse(null));

        // 组件不可解析：panel_shape_ 在命名空间链（活动主题 ∪ core）解析不到
        assertEquals(1, report.unresolvableComponents().size());
        assertEquals("12", report.unresolvableComponents().get(0).panelId());
        assertEquals("GhostShape", report.unresolvableComponents().get(0).component());

        assertEquals(1, report.archivedPlacements().size());
        assertEquals("20", report.archivedPlacements().get(0).id());
        assertEquals(1, report.orphanedPlacements().size());
        assertEquals("21", report.orphanedPlacements().get(0).id());

        // 区域健康在本场景无发现
        assertTrue(report.orphanPanels().isEmpty());
        assertTrue(report.danglingPanelRefs().isEmpty());
        assertTrue(report.emptyRequiredRegions().isEmpty());
    }

    @Test
    void reportsLiteralSeedTokens() {
        // 未展开种子令牌（theme-seed-export R1）：D9 之前导入的遗留——字段无关的递归清点，
        // 与面板扫描同范围（已归档面板不入列）
        when(manifestService.getSnapshot()).thenReturn(themeSnapshot());
        Placement legacy = panel(30L, "home", "live", "RankedList", Map.of(
                "channel", "{{channel:video}}",
                "extra", Map.of("items", List.of("{{asset:img/a.svg}}"))));
        Placement archived = panel(31L, "home", "live", "RankedList", Map.of("channel", "{{channel:x}}"));
        archived.setArchivedAt(OffsetDateTime.now());
        Placement clean = panel(32L, "home", "live", "RankedList", Map.of("channel", 12));
        // 别名映射遗留形（D20）：字段定义驱动检出（channelRef 值 {"alias": …}）
        Placement aliasLegacy = panel(33L, "home", "live", "RankedList",
                Map.of("channel", Map.of("alias", "video")));
        when(placementService.listBySiteId(SITE_ID)).thenReturn(List.of(legacy, archived, clean, aliasLegacy));
        Channel news = new Channel();
        news.setId(12L);
        news.setSiteId(SITE_ID);
        when(channelService.select(12L)).thenReturn(news);

        CompositionHealthReport report = service.report(SITE_ID, List.of(THEME));

        assertEquals(3, report.literalSeedTokens().size());
        CompositionHealthReport.LiteralSeedToken ref = report.literalSeedTokens().stream()
                .filter(t -> "30".equals(t.panelId()) && "channel".equals(t.setting()))
                .findFirst().orElseThrow();
        assertEquals("home", ref.pageRef());
        assertEquals("live", ref.region());
        assertEquals("RankedList", ref.component());
        assertEquals("{{channel:video}}", ref.token());
        CompositionHealthReport.LiteralSeedToken asset = report.literalSeedTokens().stream()
                .filter(t -> "extra.items[0]".equals(t.setting())).findFirst().orElseThrow();
        assertEquals("{{asset:img/a.svg}}", asset.token());
        CompositionHealthReport.LiteralSeedToken alias = report.literalSeedTokens().stream()
                .filter(t -> "33".equals(t.panelId())).findFirst().orElseThrow();
        assertEquals("channel", alias.setting());
        assertEquals("{\"alias\": \"video\"}", alias.token());
    }

    @Test
    void aggregatesRegionFindings() {
        // 主题清单声明两个区域：home/body（有行）、about/body（无 PageRegion 行 → 空）
        CompositionSchemas schemas = new CompositionSchemas();
        Shape articleList = new ShapeLoader(schemas).load("""
                { "name": "ArticleList", "version": "1.0.0", "settings": { "title": { "type": "text" } } }
                """, Shape.SOURCE_THEME_PREFIX + THEME, "x");
        SlotDef bodyRegion = new SlotDef(false, List.of("ArticleList"), List.of("full"), 20, 6);
        PageManifest home = new PageManifest("home", Map.of("zh-CN", "首页"), List.of(), null, null,
                Map.of("body", bodyRegion), ThemeSettingsSchema.EMPTY, Map.of());
        PageManifest about = new PageManifest("about", Map.of(), List.of(), null, null,
                Map.of("body", bodyRegion), ThemeSettingsSchema.EMPTY, Map.of());
        when(manifestService.getSnapshot()).thenReturn(RegistrySnapshot.builder()
                .addThemeShape(THEME, articleList, "{}", "x")
                .addThemeManifest(THEME, home, "{}", "home.manifest.json")
                .addThemeManifest(THEME, about, "{}", "about.manifest.json").build());

        // home/body 已发布树引用面板 100（存活）与 999（悬空）
        PageRegion region = region(1L, "home", "body", List.of("100", "999"));
        when(pageRegionMapper.listBySiteId(SITE_ID)).thenReturn(List.of(region));
        when(pageRegionMapper.selectByUnique(SITE_ID, "home", "body",
                PageRegion.SCOPE_DEFAULT)).thenReturn(region);
        when(pageRegionMapper.selectByUnique(SITE_ID, "about", "body",
                PageRegion.SCOPE_DEFAULT)).thenReturn(null);

        // 面板投放：100（被引用）、101（孤立，未被任何树引用）
        Placement referenced = panel(100L, "home", "body", "ArticleList", Map.of());
        Placement orphan = panel(101L, "home", "body", "ArticleList", Map.of());
        when(placementService.listBySiteId(SITE_ID)).thenReturn(List.of(referenced, orphan));

        CompositionHealthReport report = service.report(SITE_ID, List.of(THEME));

        assertEquals(1, report.orphanPanels().size());
        assertEquals("101", report.orphanPanels().get(0).id());
        assertEquals(1, report.danglingPanelRefs().size());
        assertEquals("999", report.danglingPanelRefs().get(0).panelId());
        // about/body 已声明但无已发布行
        assertEquals(1, report.emptyRequiredRegions().size());
        assertEquals("about", report.emptyRequiredRegions().get(0).pageRef());
        // 面板组件在主题命名空间可解析、设置无引用 → 无失效/不可解析发现
        assertTrue(report.stalePanelRefs().isEmpty());
        assertTrue(report.unresolvableComponents().isEmpty());
    }

    @Test
    void unforkedFollowerOfSharedRegionIsNotReportedEmpty() {
        // page-regions §15：news/body 引用 site/body；news 无自有默认行，有效行为宿主行 → 不算空区域，
        // 宿主行的面板（page_ref=site）也不算孤立
        CompositionSchemas schemas = new CompositionSchemas();
        Shape articleList = new ShapeLoader(schemas).load("""
                { "name": "ArticleList", "version": "1.0.0", "settings": { "title": { "type": "text" } } }
                """, Shape.SOURCE_THEME_PREFIX + THEME, "x");
        ManifestLoader loader = new ManifestLoader(schemas);
        when(manifestService.getSnapshot()).thenReturn(RegistrySnapshot.builder()
                .addThemeShape(THEME, articleList, "{}", "x")
                .addThemeManifest(THEME, loader.load("""
                        { "name": "site", "slots": { "body": { "kind": "region", "allowedShapes": ["ArticleList"] } } }
                        """, "site"), "{}", "site")
                .addThemeManifest(THEME, loader.load("""
                        { "name": "news", "template": "channel",
                          "slots": { "body": { "kind": "region", "shared": "site" } } }
                        """, "news"), "{}", "news")
                .build());
        PageRegion owner = region(1L, "site", "body", List.of("100"));
        when(pageRegionMapper.listBySiteId(SITE_ID)).thenReturn(List.of(owner));
        when(pageRegionMapper.selectByUnique(SITE_ID, "site", "body", PageRegion.SCOPE_DEFAULT)).thenReturn(owner);
        when(pageRegionMapper.selectByUnique(SITE_ID, "news", "body", PageRegion.SCOPE_DEFAULT)).thenReturn(null);
        when(placementService.listBySiteId(SITE_ID))
                .thenReturn(List.of(panel(100L, "site", "body", "ArticleList", Map.of())));

        CompositionHealthReport report = service.report(SITE_ID, List.of(THEME));

        assertTrue(report.emptyRequiredRegions().isEmpty());
        assertTrue(report.orphanPanels().isEmpty());
        assertTrue(report.danglingPanelRefs().isEmpty());
    }

    @Test
    void reportsRequiredSettingsMissingOnPublishedTreesOnly() {
        // 必填设置为空（design-review P1-4）：已发布树的启用面板空值命中；停用面板与仅草稿引用的
        // 面板不命中（草稿由发布门拦截）；声明 default 不豁免（与发布门同口径）
        CompositionSchemas schemas = new CompositionSchemas();
        Shape headline = new ShapeLoader(schemas).load("""
                { "name": "Headline", "version": "1.0.0",
                  "settings": { "title": { "type": "text", "required": true, "default": "缺省" } },
                  "item": { "t": { "type": "text" } } }
                """, Shape.SOURCE_THEME_PREFIX + THEME, "x");
        PageManifest home = new PageManifest("home", Map.of(), List.of(), null, null,
                Map.of("body", new SlotDef(false, List.of("Headline"), List.of("full"), 20, 6)),
                ThemeSettingsSchema.EMPTY, Map.of());
        when(manifestService.getSnapshot()).thenReturn(RegistrySnapshot.builder()
                .addThemeShape(THEME, headline, "{}", "x")
                .addThemeManifest(THEME, home, "{}", "home.manifest.json").build());

        // 100：已发布+启用+空 → 命中；101：已发布+停用+空 → 豁免；102：仅草稿引用+空 → 豁免；
        // 103：已发布+启用+已设值 → 通过
        Placement blank = panel(100L, "home", "body", "Headline", Map.of());
        Placement disabled = panel(101L, "home", "body", "Headline", Map.of());
        disabled.setEnabled(false);
        Placement draftOnly = panel(102L, "home", "body", "Headline", Map.of());
        Placement filled = panel(103L, "home", "body", "Headline", Map.of("title", "有值"));
        when(placementService.listBySiteId(SITE_ID))
                .thenReturn(List.of(blank, disabled, draftOnly, filled));

        PageRegion region = region(1L, "home", "body", List.of("100", "101", "103"));
        region.setDraftLayoutJson(new RegionLayout(List.of(new RegionLayout.Row("r2", "full",
                List.of(new RegionLayout.Column(List.of("102")))))).toJson());
        when(pageRegionMapper.listBySiteId(SITE_ID)).thenReturn(List.of(region));
        when(pageRegionMapper.selectByUnique(SITE_ID, "home", "body",
                PageRegion.SCOPE_DEFAULT)).thenReturn(region);

        CompositionHealthReport report = service.report(SITE_ID, List.of(THEME));

        assertEquals(1, report.requiredSettingsMissing().size());
        CompositionHealthReport.RequiredSettingMissing finding = report.requiredSettingsMissing().get(0);
        assertEquals("100", finding.panelId());
        assertEquals("home", finding.pageRef());
        assertEquals("body", finding.region());
        assertEquals(PageRegion.SCOPE_DEFAULT, finding.scope());
        assertEquals("Headline", finding.component());
        assertEquals("title", finding.setting());
    }

    /** 注册表状态拆分口径：全局计数含全部主题；活动口径仅活动主题声明（theme-owned-shapes D15）。 */
    @Test
    void registryStatusSplitsActiveThemeCounts() {
        CompositionSchemas schemas = new CompositionSchemas();
        ShapeLoader shapeLoader = new ShapeLoader(schemas);
        ManifestLoader manifestLoader = new ManifestLoader(schemas);
        String shapeJson = """
                { "name": "%s", "version": "1.0.0", "item": { "t": { "type": "text" } } }
                """;
        String manifestJson = """
                { "name": "%s", "slots": { "s": { "kind": "region", "allowedShapes": ["Card"] } } }
                """;
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                // 活动主题声明 Card / Extra（活动口径 2）
                .addThemeShape(THEME, shapeLoader.load(shapeJson.formatted("Card"),
                        Shape.SOURCE_THEME_PREFIX + THEME, "x"), "{}", "x")
                .addThemeShape(THEME, shapeLoader.load(shapeJson.formatted("Extra"),
                        Shape.SOURCE_THEME_PREFIX + THEME, "x"), "{}", "x")
                .addThemeManifest(THEME, manifestLoader.load(manifestJson.formatted("home"), "x"), "{}", "x")
                // 非活动主题：不计入活动口径
                .addThemeShape("1/other", shapeLoader.load(shapeJson.formatted("Hero"),
                        Shape.SOURCE_THEME_PREFIX + "1/other", "x"), "{}", "x")
                .addThemeManifest("1/other", manifestLoader.load(manifestJson.formatted("home"), "x"), "{}", "x")
                .addThemeManifest("1/other", manifestLoader.load(manifestJson.formatted("channel"), "x"), "{}", "x")
                .build();
        when(manifestService.getSnapshot()).thenReturn(snapshot);
        when(placementService.listBySiteId(SITE_ID)).thenReturn(List.of());

        CompositionHealthReport report = service.report(SITE_ID, List.of(THEME));

        assertEquals(3, report.registry().shapeCount());
        assertEquals(3, report.registry().manifestCount());
        assertEquals(2, report.registry().activeShapeCount());
        assertEquals(1, report.registry().activeManifestCount());
    }
}
