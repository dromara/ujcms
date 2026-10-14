package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.PageRegion;
import com.ujcms.cms.core.domain.PageRegionSnapshot;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.PageRegionSnapshotMapper;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 区域发布快照历史 + 发布门测试（region-history / design-review P1-1、P1-4）：发布追加物化快照与
 * 回填漂移、内容去重、保留裁剪；恢复 = 复制建新面板的一次新发布（草稿在场拒绝 / discardDraft 放行、
 * 坐标校验）；删除覆盖行前捕获被销毁树；必填设置门（空值阻断、指名行列面板字段、停用面板豁免、
 * 声明 default 不豁免）。
 */
class RegionServiceHistoryTest {
    private static final Long SITE = 1L;
    private static final String PAGE = "home";
    private static final String REGION = "body";
    private static final String THEME = "1/default";

    private PageRegionMapper mapper;
    private PageRegionSnapshotMapper snapshotMapper;
    private PlacementService placementService;
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
        manifestService = mock(ManifestService.class);
        CompositionSchemas schemas = new CompositionSchemas();
        shapeLoader = new ShapeLoader(schemas);
        manifestLoader = new ManifestLoader(schemas);
        registry(false);
        cacheSync = mock(CompositionCacheSync.class);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(new Config());
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of());
        service = new RegionService(mapper, snapshotMapper, placementService, mock(PanelResolver.class),
                manifestService, schemas, new SnowflakeSequence(0, 0), cacheSync, configService,
                FieldTypeMatrixTest.registry());
    }

    /**
     * 注册表：RichHtml 组件（title 设置，{@code required} 可选，带 zh-CN 标签与 default——验证
     * 声明 default 不豁免必填门）+ home/body 区域清单。
     */
    private void registry(boolean titleRequired) {
        String shapeJson = """
                { "name": "RichHtml", "version": "1.0.0", "titleField": "title",
                  "label": { "zh-CN": "富文本" },
                  "settings": { "title": { "type": "text", "max": 200, "required": %s,
                                           "label": { "zh-CN": "标题" }, "default": "缺省值" } },
                  "item": { "title": { "type": "text" } } }
                """.formatted(titleRequired);
        String manifestJson = """
                { "name": "home", "slots": {
                    "body": { "kind": "region", "allowedShapes": ["RichHtml"],
                              "allowedRowLayouts": ["full"] } } }
                """;
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape(THEME, shapeLoader.load(shapeJson, Shape.SOURCE_THEME_PREFIX + THEME, "x"), "{}", "x")
                .addThemeManifest(THEME, manifestLoader.load(manifestJson, "x"), "{}", "x")
                .build();
        when(manifestService.getSnapshot()).thenReturn(snapshot);
    }

    private Placement panel(long id, boolean enabled, Map<String, Object> settings) {
        Placement p = new Placement();
        p.setId(id);
        p.setSiteId(SITE);
        p.setPageRef(PAGE);
        p.setSlotName(REGION);
        p.setPanelShape("RichHtml");
        p.setSettings(new HashMap<>(settings));
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

    private static String tree(String panelId) {
        return """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["%s"] } ] } ] }
                """.formatted(panelId);
    }

    /**
     * 物化内容 JSON（与 materialize 产出同构，语义比较用）。
     */
    private static String content(String title) {
        return """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": [
                  { "shape": "RichHtml", "settings": { "title": "%s" }, "enabled": true } ] } ] } ] }
                """.formatted(title);
    }

    private PageRegionSnapshot snapshotRow(long id, String contentJson) {
        PageRegionSnapshot s = new PageRegionSnapshot();
        s.setId(id);
        s.setSiteId(SITE);
        s.setPageRef(PAGE);
        s.setRegionName(REGION);
        s.setScope(PageRegion.SCOPE_DEFAULT);
        s.setContentJson(contentJson);
        return s;
    }

    private void stubRow(PageRegion row) {
        when(mapper.selectByUnique(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(row);
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
    }

    @Test
    void seedResetBackfillSnapshotsDraftThenPublishedBeforePanelRewrite() {
        // 种子重置前置回填（D22）：先未发布草稿（重置将丢弃草稿——「绝不静默销毁未发布工作」
        // 由快照保全）、后被替换的已发布树，均 source=backfill
        PageRegion row = region(tree("10"), tree("11"));
        when(placementService.select(10L)).thenReturn(panel(10L, true, Map.of("title", "旧")));
        when(placementService.select(11L)).thenReturn(panel(11L, true, Map.of("title", "草稿")));

        service.seedResetBackfill(row, "admin");

        ArgumentCaptor<PageRegionSnapshot> captor = ArgumentCaptor.forClass(PageRegionSnapshot.class);
        verify(snapshotMapper, times(2)).insert(captor.capture());
        PageRegionSnapshot draft = captor.getAllValues().get(0);
        assertEquals(PageRegionSnapshot.SOURCE_BACKFILL, draft.getSource());
        assertTrue(draft.getContentJson().contains("草稿"));
        PageRegionSnapshot replaced = captor.getAllValues().get(1);
        assertEquals(PageRegionSnapshot.SOURCE_BACKFILL, replaced.getSource());
        assertTrue(replaced.getContentJson().contains("旧"));
    }

    @Test
    void seedResetPublishReplacesTreeStampsHashAppendsSnapshotAndCleansOrphans() {
        // 种子重置发布（D22）：树替换 + 草稿清空 + 来源散列专用语句写入（updateWithLock 不含来源列）
        // + source=seed-reset 快照 + 掉树面板标准孤立清理 + 构成缓存失效
        PageRegion row = region(tree("10"), tree("11"));
        row.setOrigin("theme:mytheme");
        when(mapper.updateWithLock(any(PageRegion.class), anyInt())).thenReturn(1);
        when(mapper.listByRegion(SITE, PAGE, REGION)).thenReturn(List.of(row));
        when(placementService.select(20L)).thenReturn(panel(20L, true, Map.of("title", "种子")));
        when(placementService.listByPage(SITE, PAGE)).thenReturn(List.of(
                panel(10L, true, Map.of()), panel(11L, true, Map.of()), panel(20L, true, Map.of())));
        RegionLayout layout = RegionLayout.parse(tree("20"));

        service.seedResetPublish(row, layout, "hash-abc", "admin");

        assertEquals(layout.toJson(), row.getLayoutJson());
        assertNull(row.getDraftLayoutJson());
        verify(mapper).updateWithLock(row, 1);
        verify(mapper).updateOriginHash(100L, "hash-abc");
        assertEquals("hash-abc", row.getOriginHash());
        ArgumentCaptor<PageRegionSnapshot> captor = ArgumentCaptor.forClass(PageRegionSnapshot.class);
        verify(snapshotMapper).insert(captor.capture());
        assertEquals(PageRegionSnapshot.SOURCE_SEED_RESET, captor.getValue().getSource());
        assertEquals("admin", captor.getValue().getActor());
        assertTrue(captor.getValue().getContentJson().contains("种子"));
        // 掉树面板（10/11）删除，新树面板（20）保留——内容已由前置回填快照保全
        verify(placementService).delete(10L);
        verify(placementService).delete(11L);
        verify(placementService, never()).delete(20L);
        verify(cacheSync).invalidateAll("page-region");
    }

    @Test
    void publishBackfillsPreFeatureStateThenAppendsPublishedSnapshot() {
        // 旧已发布树（面板10=「旧」）无任何快照覆盖 → 先回填，再追加新发布状态（面板11=「新」）
        stubRow(region(tree("10"), tree("11")));
        when(placementService.select(10L)).thenReturn(panel(10L, true, Map.of("title", "旧")));
        when(placementService.select(11L)).thenReturn(panel(11L, true, Map.of("title", "新")));

        int version = service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1,
                List.of(THEME), "admin", "上新");

        assertEquals(2, version);
        ArgumentCaptor<PageRegionSnapshot> captor = ArgumentCaptor.forClass(PageRegionSnapshot.class);
        verify(snapshotMapper, times(2)).insert(captor.capture());
        PageRegionSnapshot backfill = captor.getAllValues().get(0);
        assertEquals(PageRegionSnapshot.SOURCE_BACKFILL, backfill.getSource());
        assertTrue(backfill.getContentJson().contains("旧"));
        assertEquals(1, backfill.getRowsCount());
        assertEquals(1, backfill.getPanelsCount());
        PageRegionSnapshot published = captor.getAllValues().get(1);
        assertEquals(PageRegionSnapshot.SOURCE_PUBLISH, published.getSource());
        assertEquals("admin", published.getActor());
        assertEquals("上新", published.getLabel());
        assertTrue(published.getContentJson().contains("新"));
        assertNull(published.getRestoredFrom());
    }

    @Test
    void publishSkipsBackfillWhenLatestSnapshotMatches() {
        // 最新快照已覆盖被替换状态（语义相等，键序无关）→ 不回填，只追加发布条目
        stubRow(region(tree("10"), tree("11")));
        when(placementService.select(10L)).thenReturn(panel(10L, true, Map.of("title", "旧")));
        when(placementService.select(11L)).thenReturn(panel(11L, true, Map.of("title", "新")));
        when(snapshotMapper.listByCoordinates(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(List.of(snapshotRow(500L, content("旧"))));

        service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1,
                List.of(THEME), "admin", null);

        ArgumentCaptor<PageRegionSnapshot> captor = ArgumentCaptor.forClass(PageRegionSnapshot.class);
        verify(snapshotMapper, times(1)).insert(captor.capture());
        assertEquals(PageRegionSnapshot.SOURCE_PUBLISH, captor.getValue().getSource());
    }

    @Test
    void publishDedupsWhenContentUnchanged() {
        // 草稿与已发布语义相同且已被最新快照覆盖 → 回填与追加均去重，发布本身照常完成
        stubRow(region(tree("10"), tree("10")));
        when(placementService.select(10L)).thenReturn(panel(10L, true, Map.of("title", "同")));
        when(snapshotMapper.listByCoordinates(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(List.of(snapshotRow(500L, content("同"))));

        int version = service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1,
                List.of(THEME), "admin", null);

        assertEquals(2, version);
        verify(snapshotMapper, never()).insert(any());
        verify(mapper).updateWithLock(any(PageRegion.class), anyInt());
    }

    @Test
    void publishTrimsHistoryBeyondRetention() {
        stubRow(region(tree("10"), tree("11")));
        when(placementService.select(10L)).thenReturn(panel(10L, true, Map.of("title", "旧")));
        when(placementService.select(11L)).thenReturn(panel(11L, true, Map.of("title", "新")));
        List<PageRegionSnapshot> full = new java.util.ArrayList<>();
        for (long i = 1; i <= RegionService.HISTORY_RETENTION + 2; i++) {
            full.add(snapshotRow(i, content("历史" + i)));
        }
        when(snapshotMapper.listByCoordinates(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(full);

        service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1,
                List.of(THEME), "admin", null);

        // 列表新→旧：保留前 50 条，删除下标 50、51（即 id 51、52）
        verify(snapshotMapper, times(2)).delete(anyLong());
        verify(snapshotMapper).delete(51L);
        verify(snapshotMapper).delete(52L);
    }

    // ---- 发布门（P1-4） --------------------------------------------------------------------------

    @Test
    void publishBlocksOnBlankRequiredSettingEvenWithDefault() {
        registry(true);
        stubRow(region("{\"rows\":[]}", tree("11")));
        // title 必填且声明 default——空值仍阻断（required 语义为「操作员必须显式设置」）
        when(placementService.select(11L)).thenReturn(panel(11L, true, Map.of()));

        Http400Exception e = assertThrows(Http400Exception.class,
                () -> service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1,
                        List.of(THEME), "admin", null));
        assertTrue(e.getMessage().contains("必填设置"));
        assertTrue(e.getMessage().contains("第1行第1列"));
        assertTrue(e.getMessage().contains("富文本"));
        assertTrue(e.getMessage().contains("标题"));
        verify(mapper, never()).updateWithLock(any(), anyInt());
        verify(snapshotMapper, never()).insert(any());
    }

    @Test
    void publishGateSkipsDisabledPanelsAndPassesWhenSet() {
        registry(true);
        // 面板11：必填已设值；面板12：停用且空——不阻断
        String draft = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["11", "12"] } ] } ] }
                """;
        stubRow(region("{\"rows\":[]}", draft));
        when(placementService.select(11L)).thenReturn(panel(11L, true, Map.of("title", "有值")));
        when(placementService.select(12L)).thenReturn(panel(12L, false, Map.of()));

        service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1,
                List.of(THEME), "admin", null);
        verify(mapper).updateWithLock(any(PageRegion.class), anyInt());
    }

    // ---- 恢复（region-history §6） ----------------------------------------------------------------

    @Test
    void restoreRepublishesSnapshotContentAsNewPanels() {
        PageRegion row = region("{\"rows\":[]}", null);
        stubRow(row);
        when(snapshotMapper.select(500L)).thenReturn(snapshotRow(500L, content("历史")));
        // 最新快照 = 空树（被替换状态已覆盖）→ 恢复只追加 source=restore 一条
        when(snapshotMapper.listByCoordinates(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(List.of(snapshotRow(400L, "{\"rows\":[]}")));
        when(placementService.savePanel(any(Placement.class), any())).thenReturn(9100L);
        when(placementService.select(9100L)).thenReturn(panel(9100L, true, Map.of("title", "历史")));

        int version = service.restore(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT,
                500L, 1, false, List.of(THEME), "admin");

        // v1 --saveDraft--> v2 --publish--> v3
        assertEquals(3, version);
        // 面板复制建新：savePanel 收到无ID投放（快照不含面板ID）
        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService).savePanel(panelCaptor.capture(), any());
        assertEquals(0L, panelCaptor.getValue().getId());
        assertEquals("历史", panelCaptor.getValue().getSettings().get("title"));
        // 已发布树翻转为恢复内容
        assertEquals(List.of("9100"), RegionLayout.parse(row.getLayoutJson()).panelIds());
        assertNull(row.getDraftLayoutJson());
        // 追加恢复条目，restored_from_ 指向被恢复快照
        ArgumentCaptor<PageRegionSnapshot> snapCaptor = ArgumentCaptor.forClass(PageRegionSnapshot.class);
        verify(snapshotMapper).insert(snapCaptor.capture());
        assertEquals(PageRegionSnapshot.SOURCE_RESTORE, snapCaptor.getValue().getSource());
        assertEquals(500L, snapCaptor.getValue().getRestoredFrom());
    }

    @Test
    void restoreRejectsExistingDraftWithoutFlagAndDiscardsWithFlag() {
        PageRegion row = region("{\"rows\":[]}", tree("13"));
        stubRow(row);
        when(snapshotMapper.select(500L)).thenReturn(snapshotRow(500L, content("历史")));
        when(snapshotMapper.listByCoordinates(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(List.of(snapshotRow(400L, "{\"rows\":[]}")));

        // 无旗标：拒绝，未发布草稿不被静默销毁
        Http400Exception e = assertThrows(Http400Exception.class,
                () -> service.restore(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT,
                        500L, 1, false, List.of(THEME), "admin"));
        assertTrue(e.getMessage().contains("草稿"));
        verify(mapper, never()).updateWithLock(any(), anyInt());

        // discardDraft=true：事务内丢弃草稿后恢复（v1 --discard--> v2 --saveDraft--> v3 --publish--> v4）
        when(placementService.savePanel(any(Placement.class), any())).thenReturn(9100L);
        when(placementService.select(9100L)).thenReturn(panel(9100L, true, Map.of("title", "历史")));
        int version = service.restore(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT,
                500L, 1, true, List.of(THEME), "admin");
        assertEquals(4, version);
        assertEquals(List.of("9100"), RegionLayout.parse(row.getLayoutJson()).panelIds());
    }

    @Test
    void restoreRejectsCoordinateMismatch() {
        PageRegionSnapshot other = snapshotRow(500L, content("历史"));
        other.setPageRef("channel");
        when(snapshotMapper.select(500L)).thenReturn(other);
        assertThrows(Http400Exception.class,
                () -> service.restore(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT,
                        500L, 1, false, List.of(THEME), "admin"));
        assertThrows(Http400Exception.class,
                () -> service.restore(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT,
                        999L, 1, false, List.of(THEME), "admin"));
    }

    // ---- 删除覆盖行快照（region-history §5c） ------------------------------------------------------

    @Test
    void deleteOverrideCapturesDestroyedTree() {
        PageRegion override = region(tree("9002"), null);
        override.setId(200L);
        override.setScope("channel:5");
        when(mapper.selectByUnique(SITE, PAGE, REGION, "channel:5")).thenReturn(override);
        when(placementService.select(9002L)).thenReturn(panel(9002L, true, Map.of("title", "覆盖")));

        service.deleteOverride(SITE, PAGE, REGION, "channel:5", 1, List.of(THEME), "admin");

        ArgumentCaptor<PageRegionSnapshot> captor = ArgumentCaptor.forClass(PageRegionSnapshot.class);
        verify(snapshotMapper).insert(captor.capture());
        assertEquals(PageRegionSnapshot.SOURCE_DELETE, captor.getValue().getSource());
        assertEquals("channel:5", captor.getValue().getScope());
        assertTrue(captor.getValue().getContentJson().contains("覆盖"));
        verify(mapper).delete(200L);
    }

    // ---- 按设备显示（design-review P2-5） ----------------------------------------------------------

    @Test
    void snapshotMaterializesVisibilityOnlyWhenNonDefault() {
        // 快照规范形：visibility=all 不落键（未标记树的快照 JSON 与特性上线前逐字节一致，
        // 去重/回填不受扰）；desktop/mobile 落键
        Placement flagged = panel(11L, true, Map.of("title", "新"));
        flagged.setVisibility(Placement.VISIBILITY_DESKTOP);
        stubRow(region(tree("10"), tree("11")));
        when(placementService.select(10L)).thenReturn(panel(10L, true, Map.of("title", "旧")));
        when(placementService.select(11L)).thenReturn(flagged);

        service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1,
                List.of(THEME), "admin", null);

        ArgumentCaptor<PageRegionSnapshot> captor = ArgumentCaptor.forClass(PageRegionSnapshot.class);
        verify(snapshotMapper, times(2)).insert(captor.capture());
        // 回填（面板10=all）：无 visibility 键
        assertTrue(!captor.getAllValues().get(0).getContentJson().contains("visibility"));
        // 新发布（面板11=desktop）：落键
        assertTrue(captor.getAllValues().get(1).getContentJson().contains("\"visibility\":\"desktop\""));
    }

    @Test
    void restoreRoundTripsVisibility() {
        PageRegion row = region("{\"rows\":[]}", null);
        stubRow(row);
        String contentWithVisibility = """
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": [
                  { "shape": "RichHtml", "settings": { "title": "历史" }, "enabled": true,
                    "visibility": "mobile" } ] } ] } ] }
                """;
        when(snapshotMapper.select(500L)).thenReturn(snapshotRow(500L, contentWithVisibility));
        when(snapshotMapper.listByCoordinates(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT))
                .thenReturn(List.of(snapshotRow(400L, "{\"rows\":[]}")));
        when(placementService.savePanel(any(Placement.class), any())).thenReturn(9100L);
        Placement restored = panel(9100L, true, Map.of("title", "历史"));
        restored.setVisibility(Placement.VISIBILITY_MOBILE);
        when(placementService.select(9100L)).thenReturn(restored);

        service.restore(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT,
                500L, 1, false, List.of(THEME), "admin");

        // 恢复建新面板携带快照的显示设备
        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService).savePanel(panelCaptor.capture(), any());
        assertEquals(Placement.VISIBILITY_MOBILE, panelCaptor.getValue().getVisibility());
    }

    @Test
    void publishGateAppliesToDeviceHiddenPanels() {
        // 按设备隐藏不豁免必填门（P2-5）：仅移动的面板仍在移动端发布渲染，必填照常适用
        registry(true);
        stubRow(region("{\"rows\":[]}", tree("11")));
        Placement mobileOnly = panel(11L, true, Map.of());
        mobileOnly.setVisibility(Placement.VISIBILITY_MOBILE);
        when(placementService.select(11L)).thenReturn(mobileOnly);

        Http400Exception e = assertThrows(Http400Exception.class,
                () -> service.publish(SITE, PAGE, REGION, PageRegion.SCOPE_DEFAULT, 1,
                        List.of(THEME), "admin", null));
        assertTrue(e.getMessage().contains("必填设置"));
    }
}
