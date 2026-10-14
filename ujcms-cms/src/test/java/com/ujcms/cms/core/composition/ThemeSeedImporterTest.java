package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.PageManifest.SlotDef;
import com.ujcms.cms.core.composition.ThemeContentSeedImporter.Failure;
import com.ujcms.cms.core.composition.ThemeSeedImporter.SeedImportResult;
import com.ujcms.cms.core.domain.PageRegion;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.PlacementMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主题种子导入器测试（page-regions §10；component-settings Phase D 起仅导入
 * {@code seed/regions/*.regions.json} 的设置驱动面板）：区域幂等（唯一键跳过）、面板来源标记与
 * originId 合成、{@code {{theme}}} 资源前缀与 {@code {{channel:…}}}/{@code {{article:…}}}
 * 引用令牌展开、区域插槽未声明的跳过降级、令牌不可解析的行级降级（R2 通道，整区域/刷新面板
 * 为跳过单元）、覆盖策略（§6.1 / A3）对本源面板的刷新、本源区域结构重建（D22：还原恒重建、
 * 同步未修改按散列条件重建；非本源行结构在任何策略下不动）。
 */
class ThemeSeedImporterTest {
    private static final Long SITE = 1L;
    private static final String THEME = "mytheme";
    private static final String ORIGIN = "theme:mytheme";

    private PlacementMapper placementMapper;
    private PageRegionMapper pageRegionMapper;
    private ChannelMapper channelMapper;
    private ArticleMapper articleMapper;
    private com.ujcms.cms.core.service.ChannelService channelService;
    private PlacementService placementService;
    private RegionService regionService;
    private ThemeSettingsService themeSettingsService;
    private ManifestService manifestService;
    private ThemeSeedImporter importer;
    private final AtomicLong panelIdSeq = new AtomicLong(9000);

    @BeforeEach
    void setUp() {
        placementMapper = mock(PlacementMapper.class);
        pageRegionMapper = mock(PageRegionMapper.class);
        channelMapper = mock(ChannelMapper.class);
        articleMapper = mock(ArticleMapper.class);
        placementService = mock(PlacementService.class);
        regionService = mock(RegionService.class);
        themeSettingsService = mock(ThemeSettingsService.class);
        manifestService = mock(ManifestService.class);
        // 真实 SettingsResolver（D24：只填空修复失效引用的判定与健康页同口径）——栏目存在性经 channelService
        channelService = mock(com.ujcms.cms.core.service.ChannelService.class);
        SettingsResolver settingsResolver = new SettingsResolver(mock(com.ujcms.cms.core.service.ArticleService.class),
                channelService, mock(com.ujcms.cms.core.service.BlockService.class),
                mock(com.ujcms.cms.ext.service.VoteService.class));
        // 真实 SeedAssetCopier（E1 起面板设置可携带 {{asset:}} 令牌）：本用例种子无令牌，无文件 IO
        com.ujcms.cms.core.service.ConfigService configService =
                mock(com.ujcms.cms.core.service.ConfigService.class);
        com.ujcms.cms.core.domain.Config config = new com.ujcms.cms.core.domain.Config();
        config.setUploadStorage(new com.ujcms.cms.core.domain.Config.Storage(0, "file:/unused", "/uploads"));
        when(configService.getUnique()).thenReturn(config);
        SeedAssetCopier assetCopier = new SeedAssetCopier(configService,
                new com.ujcms.common.web.PathResolver(null),
                mock(com.ujcms.cms.core.service.AttachmentService.class));
        importer = new ThemeSeedImporter(placementMapper, pageRegionMapper, channelMapper, articleMapper,
                mock(com.ujcms.cms.core.mapper.BlockMapper.class),
                mock(com.ujcms.cms.ext.mapper.VoteMapper.class),
                placementService, regionService, themeSettingsService, manifestService, settingsResolver,
                new CompositionSchemas(), new SnowflakeSequence(0, 0), assetCopier);
        // savePanel 为新面板分配并回显投放ID、更新沿用既有ID（真实实现亦如此），供区域树装配
        when(placementService.savePanel(any(Placement.class), any(SlotDef.class)))
                .thenAnswer(inv -> {
                    Placement bean = inv.getArgument(0);
                    if (bean.getId() == null || bean.getId() == 0L) {
                        bean.setId(panelIdSeq.incrementAndGet());
                    }
                    return bean.getId();
                });
        // 默认快照：主题清单声明 home/body 区域。需要其它快照的用例自行重置。
        when(manifestService.getSnapshot()).thenReturn(regionSnapshot());
    }

    /** 含 home/body 区域声明的主题清单快照——区域导入用。 */
    private RegistrySnapshot regionSnapshot() {
        ManifestLoader manifestLoader = new ManifestLoader(new CompositionSchemas());
        String manifestJson = """
                { "name": "home", "slots": {
                    "body": { "kind": "region", "allowedShapes": ["RichHtml"],
                              "allowedRowLayouts": ["full", "halves"],
                              "constraints": { "maxRows": 5, "maxPanelsPerColumn": 3 } } } }
                """;
        String themeKey = ThemeSettingsService.themeKey(SITE, THEME);
        return RegistrySnapshot.builder()
                .addThemeManifest(themeKey, manifestLoader.load(manifestJson, "x"), "{}", "x")
                .build();
    }

    /** 仅含 seed/regions（home/body，v2 设置面板）的主题目录。 */
    private Path regionThemeDir(Path tmp) throws IOException {
        Path dir = tmp.resolve("region-theme");
        Path regions = dir.resolve("seed/regions");
        Files.createDirectories(regions);
        Files.writeString(regions.resolve("home.regions.json"), """
                { "page": "home", "regions": {
                  "body": { "rows": [
                    { "id": "r1", "layout": "full", "columns": [ { "panels": [
                        { "component": "RichHtml",
                          "settings": { "title": "Welcome",
                                        "image": "{{theme}}/assets/seed/a.jpg" } } ] } ] },
                    { "id": "r2", "layout": "halves", "columns": [
                        { "panels": [ { "component": "RichHtml", "settings": {},
                                        "visibility": "mobile" } ] },
                        { "panels": [] } ] }
                  ] } } }
                """);
        return dir;
    }

    @Test
    void importsRegionWithPanelsOriginTaggingAndThemeExpansion(@TempDir Path tmp) throws IOException {
        when(placementMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any())).thenReturn(null);
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(null);

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes", 5L);

        assertEquals(1, result.regionsImported());
        assertEquals(0, result.regionsSkipped());

        // 两个面板均经 savePanel 持久化（区域 SlotDef 来自主题清单）
        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        ArgumentCaptor<SlotDef> slotDefCaptor = ArgumentCaptor.forClass(SlotDef.class);
        verify(placementService, times(2)).savePanel(panelCaptor.capture(), slotDefCaptor.capture());
        assertEquals(List.of("RichHtml"), slotDefCaptor.getValue().getAllowedShapes());
        List<Placement> panels = panelCaptor.getAllValues();
        Placement first = panels.get(0);
        assertEquals("body", first.getSlotName());
        assertEquals("home", first.getPageRef());
        assertEquals("RichHtml", first.getPanelShape());
        assertEquals(ORIGIN, first.getOrigin());
        // 面板 originId 合成为 <page>#<region>#<rowId>#<col>#<panel>
        assertEquals("home#body#r1#0#0", first.getOriginId());
        // {{theme}} 展开为 <storeUrl>/<siteId>/<themeName>（面板设置管线）
        assertEquals("/themes/1/mytheme/assets/seed/a.jpg", first.getSettings().get("image"));
        assertEquals(Boolean.TRUE, first.getEnabled());
        // 显示设备（design-review P2-5）：未声明按 all，种子声明 visibility 落列
        assertEquals(Placement.VISIBILITY_ALL, first.getVisibility());
        assertEquals("home#body#r2#0#0", panels.get(1).getOriginId());
        assertEquals(Placement.VISIBILITY_MOBILE, panels.get(1).getVisibility());
        // R4：落面板来源散列（导入时刻投影）
        verify(placementMapper, times(2)).updateOriginHash(any(), any());

        // 区域行：直接落为已发布树（无草稿），带来源标记，面板ID为 savePanel 回显的真实ID
        ArgumentCaptor<PageRegion> regionCaptor = ArgumentCaptor.forClass(PageRegion.class);
        verify(pageRegionMapper).insert(regionCaptor.capture());
        PageRegion region = regionCaptor.getValue();
        assertEquals(ORIGIN, region.getOrigin());
        assertEquals("home", region.getPageRef());
        assertEquals("body", region.getRegionName());
        assertNull(region.getDraftLayoutJson());
        RegionLayout layout = RegionLayout.parse(region.getLayoutJson());
        assertEquals(List.of("9001", "9002"), layout.panelIds());
        // 树形：r1 full(1 列) / r2 halves(2 列，第二列空)
        assertEquals(2, layout.rows().size());
        assertEquals(1, layout.rows().get(0).columns().size());
        assertEquals(2, layout.rows().get(1).columns().size());
        assertTrue(layout.rows().get(1).columns().get(1).panels().isEmpty());
        verify(themeSettingsService).markSeedImported(SITE, THEME);
    }

    /** 含 home/aside 区域声明（主题清单，template 轨道种子）的注册表快照。 */
    private RegistrySnapshot asideSnapshot() {
        ManifestLoader manifestLoader = new ManifestLoader(new CompositionSchemas());
        String manifestJson = """
                { "name": "home", "slots": {
                    "aside": { "kind": "region", "allowedShapes": ["RankedList"],
                               "allowedRowLayouts": ["full"],
                               "constraints": { "maxRows": 8, "maxPanelsPerColumn": 2 } } } }
                """;
        String themeKey = ThemeSettingsService.themeKey(SITE, THEME);
        return RegistrySnapshot.builder()
                .addThemeManifest(themeKey, manifestLoader.load(manifestJson, "x"), "{}", "x")
                .build();
    }

    @Test
    void importsSettingsPanelAsComponentInstance(@TempDir Path tmp) throws IOException {
        // v2 设置驱动面板种子（component-settings §4.3）：{component, settings} → 组件实例投放。
        when(manifestService.getSnapshot()).thenReturn(asideSnapshot());
        when(placementMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any())).thenReturn(null);
        when(pageRegionMapper.selectByUnique(SITE, "home", "aside",
                PageRegion.SCOPE_DEFAULT)).thenReturn(null);
        Path dir = tmp.resolve("aside-theme");
        Files.createDirectories(dir.resolve("seed/regions"));
        Files.writeString(dir.resolve("seed/regions/home.regions.json"), """
                { "page": "home", "regions": {
                  "aside": { "rows": [
                    { "id": "a1", "layout": "full", "columns": [ { "panels": [
                        { "component": "RankedList",
                          "settings": { "title": "热点新闻", "limit": 9,
                                        "note": "{{theme}}/assets/x.css" } } ] } ] }
                  ] } } }
                """);

        SeedImportResult result = importer.importSeed(SITE, THEME, dir.toFile(), "/themes", null);

        assertEquals(1, result.regionsImported());
        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService).savePanel(panelCaptor.capture(), any(SlotDef.class));
        Placement panel = panelCaptor.getValue();
        assertEquals("RankedList", panel.getPanelShape());
        assertEquals("aside", panel.getSlotName());
        assertEquals("热点新闻", panel.getSettings().get("title"));
        assertEquals(9, panel.getSettings().get("limit"));
        // 设置值经 {{theme}} 资源前缀展开
        assertEquals("/themes/1/mytheme/assets/x.css", panel.getSettings().get("note"));
        assertEquals(Boolean.TRUE, panel.getEnabled());
        assertEquals(ORIGIN, panel.getOrigin());
        assertEquals("home#aside#a1#0#0", panel.getOriginId());
    }

    @Test
    void expandsContentRefTokensInPanelSettings(@TempDir Path tmp) throws IOException {
        // {{channel:<alias>}} / {{article:<originId>}}（theme-marketplace §5.2）展开为字符串ID
        when(placementMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any())).thenReturn(null);
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(null);
        Path dir = tmp.resolve("token-theme");
        Files.createDirectories(dir.resolve("seed/regions"));
        Files.writeString(dir.resolve("seed/regions/home.regions.json"), """
                { "page": "home", "regions": {
                  "body": { "rows": [
                    { "id": "r1", "layout": "full", "columns": [ { "panels": [
                        { "component": "RichHtml",
                          "settings": { "channel": "{{channel:news}}",
                                        "article": "{{article:a-1}}" } } ] } ] }
                  ] } } }
                """);
        com.ujcms.cms.core.domain.Channel channel = new com.ujcms.cms.core.domain.Channel();
        channel.setId(42L);
        when(channelMapper.findBySiteIdAndSlug(SITE, "news")).thenReturn(List.of(channel));
        com.ujcms.cms.core.domain.Article article = new com.ujcms.cms.core.domain.Article();
        article.setId(77L);
        when(articleMapper.selectByOrigin(SITE, ORIGIN, "a-1")).thenReturn(article);

        SeedImportResult result = importer.importSeed(SITE, THEME, dir.toFile(), "/themes", null);

        assertEquals(1, result.regionsImported());
        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService).savePanel(panelCaptor.capture(), any(SlotDef.class));
        assertEquals("42", panelCaptor.getValue().getSettings().get("channel"));
        assertEquals("77", panelCaptor.getValue().getSettings().get("article"));
    }

    /** 含 home/body + home/side 两区域声明的主题清单快照——令牌降级用例（区域为跳过单元）。 */
    private RegistrySnapshot twoRegionSnapshot() {
        ManifestLoader manifestLoader = new ManifestLoader(new CompositionSchemas());
        String manifestJson = """
                { "name": "home", "slots": {
                    "body": { "kind": "region", "allowedShapes": ["RichHtml"],
                              "allowedRowLayouts": ["full"],
                              "constraints": { "maxRows": 5, "maxPanelsPerColumn": 3 } },
                    "side": { "kind": "region", "allowedShapes": ["RichHtml"],
                              "allowedRowLayouts": ["full"],
                              "constraints": { "maxRows": 5, "maxPanelsPerColumn": 3 } } } }
                """;
        String themeKey = ThemeSettingsService.themeKey(SITE, THEME);
        return RegistrySnapshot.builder()
                .addThemeManifest(themeKey, manifestLoader.load(manifestJson, "x"), "{}", "x")
                .build();
    }

    @Test
    void unresolvableRefTokenSkipsRegionRecordsFailureAndContinues(@TempDir Path tmp) throws IOException {
        // 令牌不可解析（白板站点 seedScope=composition 的常态）→ 该区域整块跳过并记行级失败
        // （R2 通道，2026-07-27 前是整次回滚 + HTTP 500）；后续区域照常导入。同区的好面板一并
        // 不落——区域行一旦落库重导既不覆盖结构也不重建面板，面板级跳过会留下无从续传的半区域；
        // 整块跳过则建目标内容后重导即整块补齐
        when(manifestService.getSnapshot()).thenReturn(twoRegionSnapshot());
        when(placementMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any())).thenReturn(null);
        Path dir = tmp.resolve("token-fail-theme");
        Files.createDirectories(dir.resolve("seed/regions"));
        Files.writeString(dir.resolve("seed/regions/home.regions.json"), """
                { "page": "home", "regions": {
                  "body": { "rows": [
                    { "id": "r1", "layout": "full", "columns": [ { "panels": [
                        { "component": "RichHtml",
                          "settings": { "channel": "{{channel:missing}}" } },
                        { "component": "RichHtml", "settings": { "title": "同区好面板" } } ] } ] }
                  ] },
                  "side": { "rows": [
                    { "id": "s1", "layout": "full", "columns": [ { "panels": [
                        { "component": "RichHtml", "settings": { "title": "后续区域" } } ] } ] }
                  ] } } }
                """);
        when(channelMapper.findBySiteIdAndSlug(SITE, "missing")).thenReturn(List.of());

        SeedImportResult result = importer.importSeed(SITE, THEME, dir.toFile(), "/themes", null);

        assertEquals(1, result.regionsImported());
        assertEquals(1, result.regionsSkipped());
        assertEquals(1, result.panelsImported());
        assertEquals(List.of(), result.undeclaredSlots());
        assertEquals(1, result.failures().size());
        Failure failure = result.failures().get(0);
        assertEquals(Failure.CAT_PANELS, failure.category());
        assertEquals("home#body#r1#0#0", failure.ref());
        assertEquals(Failure.REASON_PANEL_TOKEN_UNRESOLVED, failure.reason());
        assertTrue(failure.detail().contains("{{channel:missing}}"));
        // 仅 side 区域及其面板落库（body 未落任何行）
        ArgumentCaptor<PageRegion> regionCaptor = ArgumentCaptor.forClass(PageRegion.class);
        verify(pageRegionMapper, times(1)).insert(regionCaptor.capture());
        assertEquals("side", regionCaptor.getValue().getRegionName());
        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService, times(1)).savePanel(panelCaptor.capture(), any(SlotDef.class));
        assertEquals("home#side#s1#0#0", panelCaptor.getValue().getOriginId());
    }

    @Test
    void noSeedDirYieldsEmptyResult(@TempDir Path tmp) throws IOException {
        Path bare = tmp.resolve("bare");
        Files.createDirectories(bare);
        SeedImportResult result = importer.importSeed(SITE, THEME, bare.toFile(), "/themes", null);
        assertEquals(0, result.panelsImported());
        assertEquals(0, result.regionsImported());
        verify(placementService, never()).savePanel(any(), any());
        verify(pageRegionMapper, never()).insert(any());
        verify(themeSettingsService, times(1)).markSeedImported(SITE, THEME);
    }

    @Test
    void reimportRegionIsIdempotentViaUniqueKey(@TempDir Path tmp) throws IOException {
        // 区域唯一键已存在（操作员编排或旧种子）→ 结构树整块跳过，不覆盖；缺省 fillOnly 面板也不刷新
        PageRegion existing = new PageRegion();
        existing.setId(500L);
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(existing);

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes", null);

        assertEquals(0, result.regionsImported());
        assertEquals(1, result.regionsSkipped());
        // 幂等跳过是常态，不属于「未声明插槽」告警（H5）
        assertEquals(java.util.List.of(), result.undeclaredSlots());
        verify(pageRegionMapper, never()).insert(any());
        verify(placementService, never()).savePanel(any(), any());
    }

    @Test
    void regionSkippedWhenSlotNotDeclared(@TempDir Path tmp) throws IOException {
        // 区域插槽未在主题清单声明 → 跳过并告警，不使整个安装失败；
        // 跳过明细（page#region）计入结果供响应呈现（H5）
        when(manifestService.getSnapshot()).thenReturn(RegistrySnapshot.empty());
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(null);

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes", null);

        assertEquals(0, result.regionsImported());
        assertEquals(1, result.regionsSkipped());
        assertEquals(java.util.List.of("home#body"), result.undeclaredSlots());
        verify(pageRegionMapper, never()).insert(any());
        verify(placementService, never()).savePanel(any(), any());
    }

    @Test
    void regionSkippedWhenSlotIsSharedReference(@TempDir Path tmp) throws IOException {
        // 共享区域（page-regions §15）在宿主页播种：跟随页 home 的 body 种子跳过并计入 sharedSlots，
        // 不造出默认分叉
        ManifestLoader manifestLoader = new ManifestLoader(new CompositionSchemas());
        String themeKey = ThemeSettingsService.themeKey(SITE, THEME);
        when(manifestService.getSnapshot()).thenReturn(RegistrySnapshot.builder()
                .addThemeManifest(themeKey, manifestLoader.load("""
                        { "name": "site", "slots": { "body": { "kind": "region", "allowedShapes": ["RichHtml"] } } }
                        """, "site"), "{}", "site")
                .addThemeManifest(themeKey, manifestLoader.load("""
                        { "name": "home", "slots": { "body": { "kind": "region", "shared": "site" } } }
                        """, "home"), "{}", "home")
                .build());

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes", null);

        assertEquals(0, result.regionsImported());
        assertEquals(1, result.regionsSkipped());
        assertEquals(java.util.List.of("home#body->site"), result.sharedSlots());
        assertEquals(java.util.List.of(), result.undeclaredSlots());
        verify(pageRegionMapper, never()).insert(any());
        verify(placementService, never()).savePanel(any(), any());
    }

    // ----- overwrite policies (§6.1 / A3) -----

    /** 既有的本源面板行（home#body#r1#0#0），供覆盖策略用例复用。 */
    private Placement existingSeededPanel(String originId, Map<String, Object> settings) {
        Placement placement = new Placement();
        placement.setId(888L);
        placement.setSiteId(SITE);
        placement.setPageRef("home");
        placement.setSlotName("body");
        placement.setPanelShape("RichHtml");
        placement.setSettings(new HashMap<>(settings));
        placement.setEnabled(true);
        placement.setOrigin(ORIGIN);
        placement.setOriginId(originId);
        placement.setCreated(java.time.OffsetDateTime.parse("2026-01-01T00:00:00+08:00"));
        return placement;
    }

    @Test
    void syncUnmodifiedRefreshesUnmodifiedPanelsAndSkipsOperatorModified(@TempDir Path tmp) throws IOException {
        // 区域已存在（结构恒只填空）；本源面板按 R4 散列判定：未改动 → 刷新，操作员改过 → 跳过
        PageRegion existingRegion = new PageRegion();
        existingRegion.setId(500L);
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(existingRegion);

        // r1 面板：散列复现（操作员未改）→ 按种子刷新，保留行ID/created
        Placement unmodified = existingSeededPanel("home#body#r1#0#0",
                Map.of("title", "旧标题"));
        unmodified.setOriginHash(SeedHash.placementHash(unmodified));
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r1#0#0")).thenReturn(unmodified);
        // r2 面板：行内容已偏离散列（操作员改过）→ 跳过
        Placement modified = existingSeededPanel("home#body#r2#0#0", Map.of("title", "操作员改过"));
        modified.setId(889L);
        modified.setOriginHash("0000000000000000000000000000000000000000000000000000000000000000");
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r2#0#0")).thenReturn(modified);

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes",
                null, SeedOverwrite.SYNC_UNMODIFIED);

        assertEquals(0, result.regionsImported());
        assertEquals(1, result.regionsSkipped());
        assertEquals(1, result.panelsUpdated());
        assertEquals(1, result.panelsSkipped());
        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService, times(1)).savePanel(panelCaptor.capture(), any(SlotDef.class));
        Placement bean = panelCaptor.getValue();
        // 身份保留：覆盖既有行而非新建
        assertEquals(888L, bean.getId());
        assertEquals(unmodified.getCreated(), bean.getCreated());
        // 配置回到种子声明（{{theme}} 已展开）
        assertEquals("Welcome", bean.getSettings().get("title"));
        assertEquals("/themes/1/mytheme/assets/seed/a.jpg", bean.getSettings().get("image"));
        // 结构树不动
        verify(pageRegionMapper, never()).insert(any());
    }

    @Test
    void panelMovedOutOfSeedRegionIsSkippedNotRelocated(@TempDir Path tmp) throws IOException {
        // 非本源区域（无来源标记——结构在任何策略下不重建，D22）的刷新路径：
        // 面板已被移出本区域（页面/插槽不再匹配种子位置）→ 跳过，不搬回
        PageRegion existingRegion = new PageRegion();
        existingRegion.setId(500L);
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(existingRegion);
        Placement moved = existingSeededPanel("home#body#r1#0#0", Map.of());
        moved.setSlotName("aside");
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r1#0#0")).thenReturn(moved);
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r2#0#0")).thenReturn(null);

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes",
                null, SeedOverwrite.RESET_ALL_ORIGIN);

        assertEquals(1, result.panelsSkipped());
        assertEquals(0, result.panelsUpdated());
        verify(placementService, never()).savePanel(any(), any());
    }

    /** 含令牌面板（r1）与普通面板（r2）各一行的种子目录——刷新/干跑降级用例。 */
    private Path tokenAndPlainThemeDir(Path tmp) throws IOException {
        Path dir = tmp.resolve("token-sync-theme");
        Files.createDirectories(dir.resolve("seed/regions"));
        Files.writeString(dir.resolve("seed/regions/home.regions.json"), """
                { "page": "home", "regions": {
                  "body": { "rows": [
                    { "id": "r1", "layout": "full", "columns": [ { "panels": [
                        { "component": "RichHtml",
                          "settings": { "channel": "{{channel:missing}}" } } ] } ] },
                    { "id": "r2", "layout": "full", "columns": [ { "panels": [
                        { "component": "RichHtml", "settings": { "title": "新标题" } } ] } ] }
                  ] } } }
                """);
        return dir;
    }

    @Test
    void unresolvableRefTokenSkipsPanelRefreshAndContinues(@TempDir Path tmp) throws IOException {
        // 刷新路径（区域已存在）无结构含义：令牌不可解析的降级单元是单面板——该面板跳过刷新
        // （旧内容保留）并记行级失败，后续面板照常刷新
        PageRegion existingRegion = new PageRegion();
        existingRegion.setId(500L);
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(existingRegion);
        when(channelMapper.findBySiteIdAndSlug(SITE, "missing")).thenReturn(List.of());
        Placement tokenPanel = existingSeededPanel("home#body#r1#0#0", Map.of("channel", "42"));
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r1#0#0")).thenReturn(tokenPanel);
        Placement plainPanel = existingSeededPanel("home#body#r2#0#0", Map.of("title", "旧标题"));
        plainPanel.setId(889L);
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r2#0#0")).thenReturn(plainPanel);

        SeedImportResult result = importer.importSeed(SITE, THEME, tokenAndPlainThemeDir(tmp).toFile(),
                "/themes", null, SeedOverwrite.RESET_ALL_ORIGIN);

        assertEquals(1, result.panelsSkipped());
        assertEquals(1, result.panelsUpdated());
        assertEquals(1, result.failures().size());
        Failure failure = result.failures().get(0);
        assertEquals(Failure.CAT_PANELS, failure.category());
        assertEquals("home#body#r1#0#0", failure.ref());
        assertEquals(Failure.REASON_PANEL_TOKEN_UNRESOLVED, failure.reason());
        assertTrue(failure.detail().contains("{{channel:missing}}"));
        // 仅 r2 面板经 savePanel 刷新；r1 旧内容保留
        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService, times(1)).savePanel(panelCaptor.capture(), any(SlotDef.class));
        assertEquals(889L, panelCaptor.getValue().getId());
        assertEquals("新标题", panelCaptor.getValue().getSettings().get("title"));
    }

    @Test
    void planToleratesUnresolvableRefTokenAsSeedChanged(@TempDir Path tmp) throws IOException {
        // 干跑计划与导入共用 buildPanelPlacement（严格模式）：令牌不可解析绝不使 seed/preview 抛 500，
        // 计入种子有变（种子形无从复现存量散列）
        PageRegion existingRegion = new PageRegion();
        existingRegion.setId(500L);
        existingRegion.setOrigin(ORIGIN);
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(existingRegion);
        when(channelMapper.findBySiteIdAndSlug(SITE, "missing")).thenReturn(List.of());
        Placement tokenPanel = existingSeededPanel("home#body#r1#0#0", Map.of("channel", "42"));
        tokenPanel.setOriginHash(SeedHash.placementHash(tokenPanel));
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r1#0#0")).thenReturn(tokenPanel);

        ThemeSeedImporter.CompositionSeedPlan plan =
                importer.plan(SITE, THEME, tokenAndPlainThemeDir(tmp).toFile(), "/themes");

        assertEquals(1, plan.panels().existing());
        assertEquals(0, plan.panels().operatorModified());
        assertEquals(1, plan.panels().seedChanged());
        // D22：本源区域缺失的种子面板（r2 无行）计入新增——结构重建会补建
        assertEquals(1, plan.panels().added());
    }

    // ----- structure rebuild (D22) -----

    /** 既有本源区域行（home/body，带来源标记），供 D22 重建用例复用。 */
    private PageRegion existingOriginRegion(String layoutJson) {
        PageRegion region = new PageRegion();
        region.setId(500L);
        region.setSiteId(SITE);
        region.setPageRef("home");
        region.setRegionName("body");
        region.setScope(PageRegion.SCOPE_DEFAULT);
        region.setOrigin(ORIGIN);
        region.setLayoutJson(layoutJson);
        region.setVersion(3);
        return region;
    }

    /** 单行 full 单列、引用给定面板ID的结构树 JSON（被操作员清空/改动后的现状树）。 */
    private static String layoutJsonOf(String... panelIds) {
        return new RegionLayout(List.of(new RegionLayout.Row("x1", "full",
                List.of(new RegionLayout.Column(List.of(panelIds)))))).toJson();
    }

    @Test
    void resetRebuildsOriginRegionRecreatingDeletedPanels(@TempDir Path tmp) throws IOException {
        // 还原（resetAllOrigin）：本源区域整体重建为种子默认——被删的种子面板重建、结构树覆盖；
        // 回填快照先于面板改写，发布经 seedResetPublish（掉树面板走标准孤立清理，历史可恢复）
        PageRegion existingRegion = existingOriginRegion(layoutJsonOf());
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(existingRegion);
        when(placementMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any())).thenReturn(null);

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes",
                null, SeedOverwrite.RESET_ALL_ORIGIN);

        assertEquals(1, result.regionsReset());
        assertEquals(0, result.regionsImported());
        assertEquals(0, result.regionsSkipped());
        assertEquals(2, result.panelsImported());
        // 顺序不变量：回填快照 → 面板落库 → 发布（快照物化自面板行现值，后改写则操作员内容无从保全）
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(regionService, placementService);
        inOrder.verify(regionService).seedResetBackfill(eq(existingRegion), any());
        inOrder.verify(placementService, times(2)).savePanel(any(Placement.class), any(SlotDef.class));
        ArgumentCaptor<RegionLayout> layoutCaptor = ArgumentCaptor.forClass(RegionLayout.class);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        inOrder.verify(regionService).seedResetPublish(eq(existingRegion), layoutCaptor.capture(),
                hashCaptor.capture(), any());
        // 新树以真实投放ID装配、按种子形（r1 full / r2 halves），散列即新树投影
        RegionLayout layout = layoutCaptor.getValue();
        assertEquals(List.of("9001", "9002"), layout.panelIds());
        assertEquals(2, layout.rows().size());
        assertEquals(SeedHash.regionHash(layout), hashCaptor.getValue());
        // 面板带来源三元组落库并落散列
        verify(placementMapper, times(2)).updateOriginHash(any(), any());
        // 结构重建不走新建路径
        verify(pageRegionMapper, never()).insert(any());
    }

    @Test
    void resetNeverRebuildsNonOriginRegion(@TempDir Path tmp) throws IOException {
        // 安全不变量 1（§6.1）：无来源标记（操作员自建/已剥离）的区域行，还原也不触碰其结构
        PageRegion operatorRegion = new PageRegion();
        operatorRegion.setId(500L);
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(operatorRegion);
        when(placementMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any())).thenReturn(null);

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes",
                null, SeedOverwrite.RESET_ALL_ORIGIN);

        assertEquals(0, result.regionsReset());
        assertEquals(1, result.regionsSkipped());
        verify(regionService, never()).seedResetBackfill(any(), any());
        verify(regionService, never()).seedResetPublish(any(), any(), any(), any());
        verify(placementService, never()).savePanel(any(), any());
    }

    @Test
    void resetRebuildsForeignThemeOriginRegionAndRestampsOrigin(@TempDir Path tmp) throws IOException {
        // D23：他主题本源区域行（上一主题演示布局的遗留）在还原下整体重建为本主题种子默认，
        // 走 D22 同一回填 → 面板 → 发布路径，并改记来源为本主题（区域坐标无主题维度）
        PageRegion foreignRegion = existingOriginRegion(layoutJsonOf("777"));
        foreignRegion.setOrigin("theme:previous");
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(foreignRegion);
        when(placementMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any())).thenReturn(null);

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes",
                null, SeedOverwrite.RESET_ALL_ORIGIN);

        assertEquals(1, result.regionsReset());
        assertEquals(0, result.regionsSkipped());
        assertEquals(2, result.panelsImported());
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(regionService, placementService, pageRegionMapper);
        inOrder.verify(regionService).seedResetBackfill(eq(foreignRegion), any());
        inOrder.verify(placementService, times(2)).savePanel(any(Placement.class), any(SlotDef.class));
        inOrder.verify(regionService).seedResetPublish(eq(foreignRegion), any(), any(), any());
        inOrder.verify(pageRegionMapper).updateOrigin(500L, ORIGIN);
        assertEquals(ORIGIN, foreignRegion.getOrigin());
        verify(pageRegionMapper, never()).insert(any());
    }

    @Test
    void nonResetPoliciesRebuildForeignThemeOriginRegionOnlyWhenHashProvesUntouched(@TempDir Path tmp)
            throws IOException {
        // D23：fillOnly / syncUnmodified 下，他主题本源行仅当 R4 散列在场且复现（自上一主题导入后未被
        // 操作员改动——纯遗留演示布局）时重建；散列不复现（改过）或缺失（无从判定）则照旧整块跳过、来源不改
        String json = layoutJsonOf("777");
        String untouched = SeedHash.regionHash(RegionLayout.parse(json));
        for (SeedOverwrite overwrite : List.of(SeedOverwrite.FILL_ONLY, SeedOverwrite.SYNC_UNMODIFIED)) {
            for (String storedHash : java.util.Arrays.asList(untouched, "edited-since-import", null)) {
                org.mockito.Mockito.clearInvocations(regionService, placementService, pageRegionMapper);
                PageRegion foreignRegion = existingOriginRegion(json);
                foreignRegion.setOrigin("theme:previous");
                foreignRegion.setOriginHash(storedHash);
                when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                        .thenReturn(foreignRegion);
                when(placementMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any())).thenReturn(null);

                SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(),
                        "/themes", null, overwrite);

                String label = overwrite + "/" + storedHash;
                if (untouched.equals(storedHash)) {
                    assertEquals(1, result.regionsReset(), label);
                    assertEquals(0, result.regionsSkipped(), label);
                    verify(regionService).seedResetPublish(eq(foreignRegion), any(), any(), any());
                    verify(pageRegionMapper).updateOrigin(500L, ORIGIN);
                    assertEquals(ORIGIN, foreignRegion.getOrigin(), label);
                } else {
                    assertEquals(0, result.regionsReset(), label);
                    assertEquals(1, result.regionsSkipped(), label);
                    verify(regionService, never()).seedResetPublish(any(), any(), any(), any());
                    verify(pageRegionMapper, never()).updateOrigin(any(), any());
                    assertEquals("theme:previous", foreignRegion.getOrigin(), label);
                }
            }
        }
    }

    @Test
    void resetDeProvenancesMovedPanelAndRecreatesAtSeedPosition(@TempDir Path tmp) throws IOException {
        // 还原下被操作员移出本区域的本源面板：单行剥离来源（转操作员投放，绝不搬回），种子位置重建新面板
        PageRegion existingRegion = existingOriginRegion(layoutJsonOf());
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(existingRegion);
        Placement moved = existingSeededPanel("home#body#r1#0#0", Map.of());
        moved.setSlotName("aside");
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r1#0#0")).thenReturn(moved);
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r2#0#0")).thenReturn(null);

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes",
                null, SeedOverwrite.RESET_ALL_ORIGIN);

        assertEquals(1, result.regionsReset());
        assertEquals(2, result.panelsImported());
        verify(placementMapper).clearOriginById(888L);
        // 两个位置均为新建投放（移出的行留在原处，种子位置不复用其ID）
        ArgumentCaptor<Placement> panelCaptor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService, times(2)).savePanel(panelCaptor.capture(), any(SlotDef.class));
        assertTrue(panelCaptor.getAllValues().stream().allMatch(p -> p.getId() >= 9001L));
    }

    @Test
    void syncUnmodifiedRebuildsStructureWhenHashProvesUntouchedAndSeedChanged(@TempDir Path tmp)
            throws IOException {
        // 同步未修改：散列在场且复现（结构未被操作员改动）、种子结构有变 → 重建；
        // 存活面板按 R4 逐面板判定刷新并沿用行ID
        Placement first = existingSeededPanel("home#body#r1#0#0", Map.of("title", "旧标题"));
        first.setOriginHash(SeedHash.placementHash(first));
        Placement second = existingSeededPanel("home#body#r2#0#0", Map.of());
        second.setId(889L);
        second.setVisibility(Placement.VISIBILITY_MOBILE);
        second.setOriginHash(SeedHash.placementHash(second));
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r1#0#0")).thenReturn(first);
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r2#0#0")).thenReturn(second);
        String oldLayout = layoutJsonOf("888", "889");
        PageRegion existingRegion = existingOriginRegion(oldLayout);
        existingRegion.setOriginHash(SeedHash.regionHash(RegionLayout.parse(oldLayout)));
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(existingRegion);

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes",
                null, SeedOverwrite.SYNC_UNMODIFIED);

        assertEquals(1, result.regionsReset());
        assertEquals(2, result.panelsUpdated());
        ArgumentCaptor<RegionLayout> layoutCaptor = ArgumentCaptor.forClass(RegionLayout.class);
        verify(regionService).seedResetPublish(eq(existingRegion), layoutCaptor.capture(), any(), any());
        // 结构收敛到种子形（r1 full / r2 halves），沿用存活面板行ID
        assertEquals(List.of("888", "889"), layoutCaptor.getValue().panelIds());
        assertEquals(2, layoutCaptor.getValue().rows().size());
    }

    @Test
    void syncUnmodifiedKeepsOperatorModifiedStructure(@TempDir Path tmp) throws IOException {
        // 同步未修改：结构散列不复现（操作员改过布局）→ 结构不动，仅面板内容按策略刷新
        Placement unmodified = existingSeededPanel("home#body#r1#0#0", Map.of("title", "旧标题"));
        unmodified.setOriginHash(SeedHash.placementHash(unmodified));
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r1#0#0")).thenReturn(unmodified);
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r2#0#0")).thenReturn(null);
        PageRegion existingRegion = existingOriginRegion(layoutJsonOf("888"));
        existingRegion.setOriginHash("0000000000000000000000000000000000000000000000000000000000000000");
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(existingRegion);

        SeedImportResult result = importer.importSeed(SITE, THEME, regionThemeDir(tmp).toFile(), "/themes",
                null, SeedOverwrite.SYNC_UNMODIFIED);

        assertEquals(0, result.regionsReset());
        assertEquals(1, result.regionsSkipped());
        assertEquals(1, result.panelsUpdated());
        verify(regionService, never()).seedResetPublish(any(), any(), any(), any());
    }

    @Test
    void resetFallsBackToPanelSyncWhenDeletedPanelTokenUnresolvable(@TempDir Path tmp) throws IOException {
        // 重建需重建缺失面板而其令牌不可解析 → 整块放弃重建（建目标内容后重导即整块补齐，R2 口径），
        // 落回内容刷新路径：存活面板照常刷新
        when(channelMapper.findBySiteIdAndSlug(SITE, "missing")).thenReturn(List.of());
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r1#0#0")).thenReturn(null);
        Placement plainPanel = existingSeededPanel("home#body#r2#0#0", Map.of("title", "旧标题"));
        plainPanel.setId(889L);
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r2#0#0")).thenReturn(plainPanel);
        PageRegion existingRegion = existingOriginRegion(layoutJsonOf("889"));
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(existingRegion);

        SeedImportResult result = importer.importSeed(SITE, THEME, tokenAndPlainThemeDir(tmp).toFile(),
                "/themes", null, SeedOverwrite.RESET_ALL_ORIGIN);

        assertEquals(0, result.regionsReset());
        assertEquals(1, result.regionsSkipped());
        assertEquals(1, result.panelsUpdated());
        verify(regionService, never()).seedResetBackfill(any(), any());
        verify(regionService, never()).seedResetPublish(any(), any(), any(), any());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).detail().contains("rebuild skipped"));
    }

    // ----- fillOnly stale-ref repair (theme-marketplace D24) -----

    /** 含 channelRef 设置的 RichHtml 组件 + home/body 区域清单的快照——失效引用修复用例。 */
    private RegistrySnapshot channelRefSnapshot() {
        CompositionSchemas schemas = new CompositionSchemas();
        String themeKey = ThemeSettingsService.themeKey(SITE, THEME);
        Shape richHtml = new ShapeLoader(schemas).load("""
                { "name": "RichHtml", "version": "2.0.0",
                  "settings": { "channel": { "type": "channelRef" }, "title": { "type": "text" } },
                  "data": { "from": "article", "where": { "channel": "$settings.channel" } },
                  "item": { "headline": { "type": "text" } } }
                """, Shape.SOURCE_THEME_PREFIX + themeKey, "x");
        String manifestJson = """
                { "name": "home", "slots": {
                    "body": { "kind": "region", "allowedShapes": ["RichHtml"],
                              "allowedRowLayouts": ["full"],
                              "constraints": { "maxRows": 5, "maxPanelsPerColumn": 3 } } } }
                """;
        return RegistrySnapshot.builder()
                .addThemeShape(themeKey, richHtml, "{}", "x")
                .addThemeManifest(themeKey, new ManifestLoader(schemas).load(manifestJson, "x"), "{}", "x")
                .build();
    }

    /** 每行一个钉住 {{channel:video}} 的 RichHtml 面板的种子目录（rows 行）。 */
    private Path videoThemeDir(Path tmp, int rows) throws IOException {
        Path dir = tmp.resolve("video-theme");
        Files.createDirectories(dir.resolve("seed/regions"));
        StringBuilder rowsJson = new StringBuilder();
        for (int i = 1; i <= rows; i++) {
            if (i > 1) {
                rowsJson.append(",\n");
            }
            rowsJson.append("{ \"id\": \"r").append(i).append("\", \"layout\": \"full\", \"columns\": [ { \"panels\": [ "
                    + "{ \"component\": \"RichHtml\", \"settings\": { \"channel\": \"{{channel:video}}\" } } ] } ] }");
        }
        Files.writeString(dir.resolve("seed/regions/home.regions.json"),
                "{ \"page\": \"home\", \"regions\": { \"body\": { \"rows\": [\n" + rowsJson + "\n] } } }");
        return dir;
    }

    private static com.ujcms.cms.core.domain.Channel channelOf(long id) {
        com.ujcms.cms.core.domain.Channel channel = new com.ujcms.cms.core.domain.Channel();
        channel.setId(id);
        channel.setSiteId(SITE);
        channel.setSlug("video");
        return channel;
    }

    private Placement videoPanel(long id, String originId, String channelId) {
        Placement placement = new Placement();
        placement.setId(id);
        placement.setSiteId(SITE);
        placement.setPageRef("home");
        placement.setSlotName("body");
        placement.setPanelShape("RichHtml");
        placement.setSettings(new HashMap<>(Map.of("channel", channelId)));
        placement.setEnabled(true);
        placement.setOrigin(ORIGIN);
        placement.setOriginId(originId);
        return placement;
    }

    @Test
    void removeThenFillOnlyImportRepairsDeadRefsOnUntouchedOriginPanels(@TempDir Path tmp) throws IOException {
        // D24 往返：导入（钉住栏目 11）→ 移除演示数据（栏目 11 物理删除）→ 重导内容以新ID 12 重建 video
        // → 只填空重导：散列证明未动过的本源面板，其死引用视作空缺，按种子重展开为 12（行ID保留、散列重落）
        // → 再导一次：引用健康，无事发生
        when(manifestService.getSnapshot()).thenReturn(channelRefSnapshot());
        Path dir = videoThemeDir(tmp, 1);
        // 内存存储：区域行按唯一键、面板行按 (origin, originId) 与ID 落存，模拟库行往返
        Map<String, Placement> panelsByOriginId = new HashMap<>();
        Map<Long, Placement> panelsById = new HashMap<>();
        java.util.concurrent.atomic.AtomicReference<PageRegion> regionRow =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenAnswer(inv -> regionRow.get());
        doAnswer(inv -> {
            regionRow.set(inv.getArgument(0));
            return 1;
        }).when(pageRegionMapper).insert(any(PageRegion.class));
        when(placementMapper.selectByOrigin(eq(SITE), eq(ORIGIN), any()))
                .thenAnswer(inv -> panelsByOriginId.get(inv.<String>getArgument(2)));
        when(placementService.savePanel(any(Placement.class), any(SlotDef.class))).thenAnswer(inv -> {
            Placement bean = inv.getArgument(0);
            if (bean.getId() == null || bean.getId() == 0L) {
                bean.setId(panelIdSeq.incrementAndGet());
            }
            panelsByOriginId.put(bean.getOriginId(), bean);
            panelsById.put(bean.getId(), bean);
            return bean.getId();
        });
        when(placementMapper.updateOriginHash(any(), any())).thenAnswer(inv -> {
            Placement stored = panelsById.get(inv.<Long>getArgument(0));
            if (stored != null) {
                stored.setOriginHash(inv.getArgument(1));
            }
            return 1;
        });

        // 阶段 1：首次导入——video 栏目 = 11
        com.ujcms.cms.core.domain.Channel video11 = channelOf(11L);
        when(channelMapper.findBySiteIdAndSlug(SITE, "video")).thenReturn(List.of(video11));
        when(channelService.select(11L)).thenReturn(video11);
        SeedImportResult first = importer.importSeed(SITE, THEME, dir.toFile(), "/themes", null);
        assertEquals(1, first.regionsImported());
        assertEquals(1, first.panelsImported());
        assertEquals(0, first.panelsRepaired());
        Placement seeded = panelsByOriginId.get("home#body#r1#0#0");
        assertEquals("11", seeded.getSettings().get("channel"));
        assertNotNull(seeded.getOriginHash());

        // 阶段 2：移除演示数据删除栏目 11；重导内容以新ID 12 重建 video（面板行原样未动）
        com.ujcms.cms.core.domain.Channel video12 = channelOf(12L);
        when(channelService.select(11L)).thenReturn(null);
        when(channelService.select(12L)).thenReturn(video12);
        when(channelMapper.findBySiteIdAndSlug(SITE, "video")).thenReturn(List.of(video12));

        // 阶段 3：只填空重导——区域已存在（结构不动、不新建），死引用面板按种子重展开
        SeedImportResult second = importer.importSeed(SITE, THEME, dir.toFile(), "/themes", null);
        assertEquals(0, second.regionsImported());
        assertEquals(1, second.regionsSkipped());
        assertEquals(0, second.panelsImported());
        assertEquals(0, second.panelsUpdated());
        assertEquals(1, second.panelsRepaired());
        Placement repaired = panelsByOriginId.get("home#body#r1#0#0");
        assertEquals(seeded.getId(), repaired.getId());
        assertEquals("12", repaired.getSettings().get("channel"));
        assertEquals(SeedHash.placementHash(repaired), repaired.getOriginHash());
        verify(pageRegionMapper, times(1)).insert(any(PageRegion.class));

        // 阶段 4：再导一次——引用健康，幂等无事
        SeedImportResult third = importer.importSeed(SITE, THEME, dir.toFile(), "/themes", null);
        assertEquals(0, third.panelsRepaired());
        verify(placementService, times(2)).savePanel(any(Placement.class), any(SlotDef.class));
    }

    @Test
    void fillOnlyRepairsOnlyHashProvenUntouchedPanelsWithDeadRefs(@TempDir Path tmp) throws IOException {
        // 只填空的修复边界：r1 散列复现 + 死引用 → 修复；r2 操作员改过（散列不复现）+ 死引用 → 不动；
        // r3 散列复现 + 引用健康 → 不动；r4 散列缺失（早于 R4）+ 死引用 → 不动
        when(manifestService.getSnapshot()).thenReturn(channelRefSnapshot());
        Path dir = videoThemeDir(tmp, 4);
        PageRegion existingRegion = new PageRegion();
        existingRegion.setId(500L);
        existingRegion.setOrigin(ORIGIN);
        when(pageRegionMapper.selectByUnique(SITE, "home", "body", PageRegion.SCOPE_DEFAULT))
                .thenReturn(existingRegion);
        when(channelService.select(11L)).thenReturn(null);
        com.ujcms.cms.core.domain.Channel video12 = channelOf(12L);
        when(channelService.select(12L)).thenReturn(video12);
        when(channelMapper.findBySiteIdAndSlug(SITE, "video")).thenReturn(List.of(video12));

        Placement untouchedDead = videoPanel(1L, "home#body#r1#0#0", "11");
        untouchedDead.setOriginHash(SeedHash.placementHash(untouchedDead));
        Placement modifiedDead = videoPanel(2L, "home#body#r2#0#0", "11");
        modifiedDead.setOriginHash("0000000000000000000000000000000000000000000000000000000000000000");
        Placement untouchedHealthy = videoPanel(3L, "home#body#r3#0#0", "12");
        untouchedHealthy.setOriginHash(SeedHash.placementHash(untouchedHealthy));
        Placement preR4Dead = videoPanel(4L, "home#body#r4#0#0", "11");
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r1#0#0")).thenReturn(untouchedDead);
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r2#0#0")).thenReturn(modifiedDead);
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r3#0#0")).thenReturn(untouchedHealthy);
        when(placementMapper.selectByOrigin(SITE, ORIGIN, "home#body#r4#0#0")).thenReturn(preR4Dead);

        SeedImportResult result = importer.importSeed(SITE, THEME, dir.toFile(), "/themes", null);

        assertEquals(1, result.panelsRepaired());
        assertEquals(0, result.panelsUpdated());
        ArgumentCaptor<Placement> captor = ArgumentCaptor.forClass(Placement.class);
        verify(placementService, times(1)).savePanel(captor.capture(), any(SlotDef.class));
        assertEquals(1L, captor.getValue().getId());
        assertEquals("12", captor.getValue().getSettings().get("channel"));
        verify(pageRegionMapper, never()).insert(any());
    }
}
