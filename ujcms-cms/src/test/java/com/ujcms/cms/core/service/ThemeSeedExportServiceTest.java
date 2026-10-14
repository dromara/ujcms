package com.ujcms.cms.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ujcms.cms.core.component.ThemeInstallException;
import com.ujcms.cms.core.component.ThemeInstallReport;
import com.ujcms.cms.core.component.ThemeInstallValidator;
import com.ujcms.cms.core.composition.ExportPlan;
import com.ujcms.cms.core.composition.SeedAdoption;
import com.ujcms.cms.core.composition.SeedExportBundle;
import com.ujcms.cms.core.composition.SeedExportRequest;
import com.ujcms.cms.core.composition.SeedOriginAdopter;
import com.ujcms.cms.core.composition.ThemeLifecycleLock;
import com.ujcms.cms.core.composition.ThemeSeedExporter;
import com.ujcms.cms.core.composition.ThemeSettingsService;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.ThemeState;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.common.web.PathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E2 导出编排测试（theme-seed-export §7/§8）：三模式分派、刷新的陈旧文件语义（只增删本次范围内
 * 种子族）、清单更新（语义无变化不写盘）、大小预算快速失败、Q1 活动主题确认门槛、认领委派。
 * 引擎为桩（引擎自身语义由 {@code SeedRoundTripTest} 覆盖），存储为真实 {@code file:} 临时目录。
 */
class ThemeSeedExportServiceTest {
    private static final Long SITE = 1L;
    private static final String THEME = "demo";
    private static final long CAP = 50L * 1024 * 1024;

    @TempDir
    Path tmp;

    private ThemeSeedExporter exporter;
    private SeedOriginAdopter adopter;
    private SiteService siteService;
    private ThemeSettingsService settingsService;
    private Site site;
    private ThemeSeedExportService service;
    private Path themeDir;

    @BeforeEach
    void setUp() throws IOException {
        exporter = mock(ThemeSeedExporter.class);
        adopter = mock(SeedOriginAdopter.class);
        ThemeInstallService installService = mock(ThemeInstallService.class);
        siteService = mock(SiteService.class);
        settingsService = mock(ThemeSettingsService.class);
        site = new Site();
        site.setId(SITE);
        when(siteService.select(SITE)).thenReturn(site);
        ConfigService configService = mock(ConfigService.class);
        Config config = new Config();
        config.setTemplateStorage(new Config.Storage(0, "file:" + tmp.resolve("templates"), "/themes"));
        config.setUploadStorage(new Config.Storage(0, "file:" + tmp.resolve("uploads"), "/uploads"));
        when(configService.getUnique()).thenReturn(config);
        themeDir = tmp.resolve("templates").resolve(String.valueOf(SITE)).resolve(THEME);
        when(installService.themeDir(any(), any())).thenAnswer(inv ->
                tmp.resolve("templates").resolve(String.valueOf(inv.<Long>getArgument(0)))
                        .resolve(inv.<String>getArgument(1)).toFile());
        service = new ThemeSeedExportService(exporter, adopter, installService, new ThemeLifecycleLock(),
                siteService, configService, settingsService, new PathResolver(null), Constants.MAPPER);

        // 活主题目录：渲染器模板 + 手写清单 + 旧种子（管辖内的陈旧文件 + E1 不发射的 legacy 种子）
        Files.createDirectories(themeDir.resolve("templates"));
        Files.writeString(themeDir.resolve("templates/index.html"), "<html>live</html>");
        Files.createDirectories(themeDir.resolve("seed/content/articles"));
        Files.createDirectories(themeDir.resolve("seed/regions"));
        Files.createDirectories(themeDir.resolve("seed/placements"));
        Files.writeString(themeDir.resolve("seed/content/articles/old.articles.json"), "{\"articles\":[]}");
        Files.writeString(themeDir.resolve("seed/regions/home.regions.json"), "{\"page\":\"home\"}");
        Files.writeString(themeDir.resolve("seed/placements/legacy.json"), "{\"placements\":[]}");
        Files.writeString(themeDir.resolve("theme.manifest.json"), """
                {
                  "name": "Demo Live",
                  "themeName": "demo",
                  "version": "1.2.0",
                  "seed": {
                    "policy": "non-destructive",
                    "content": { "channels": true, "articles": true, "topics": true, "assets": true }
                  }
                }
                """);
    }

    private SeedExportBundle stubBundle() {
        TreeMap<String, byte[]> files = new TreeMap<>();
        files.put("content/channels.channels.json",
                "{\"channels\":[{\"originId\":\"news\"}]}".getBytes(StandardCharsets.UTF_8));
        files.put("regions/home.regions.json",
                "{\"page\":\"home\",\"regions\":{}}".getBytes(StandardCharsets.UTF_8));
        return bundle(files, CAP, List.of());
    }

    private SeedExportBundle bundle(TreeMap<String, byte[]> files, long maxBytes,
                                    List<SeedAdoption> adoptions) {
        long total = files.values().stream().mapToLong(bytes -> bytes.length).sum();
        ExportPlan plan = new ExportPlan(ExportPlan.CategoryCounts.EMPTY, ExportPlan.CategoryCounts.EMPTY,
                ExportPlan.CategoryCounts.EMPTY, ExportPlan.CategoryCounts.EMPTY,
                ExportPlan.CategoryCounts.EMPTY, ExportPlan.CategoryCounts.EMPTY,
                ExportPlan.CategoryCounts.EMPTY, ExportPlan.CategoryCounts.EMPTY,
                ExportPlan.CategoryCounts.EMPTY, ExportPlan.CategoryCounts.EMPTY,
                ExportPlan.CategoryCounts.EMPTY, ExportPlan.CategoryCounts.EMPTY, 0, 0, 0L,
                List.of(new ExportPlan.AssetFile("img/big.png", 123L)), List.of(), List.of(),
                total, maxBytes, total > maxBytes);
        return new SeedExportBundle(files, plan, adoptions);
    }

    private void stubExport(SeedExportBundle bundle) {
        when(exporter.export(eq(SITE), eq(THEME), any(SeedExportRequest.class))).thenReturn(bundle);
    }

    // ----- refreshSeedDir -----

    @Test
    void refreshRewritesManagedSeedFamiliesOnly() throws IOException {
        stubExport(stubBundle());
        ThemeSeedExportService.RefreshReport report = service.refreshSeedDir(SITE, THEME,
                SeedExportRequest.defaults(), false, false, null, false);
        assertEquals(2, report.written());
        assertEquals(1, report.removed(), "管辖内（content/）的陈旧文件删除；regions 文件被覆盖不算删除");
        assertTrue(report.manifestUpdated());

        assertArrayEquals("{\"channels\":[{\"originId\":\"news\"}]}".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(themeDir.resolve("seed/content/channels.channels.json")));
        assertArrayEquals("{\"page\":\"home\",\"regions\":{}}".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(themeDir.resolve("seed/regions/home.regions.json")));
        assertFalse(Files.exists(themeDir.resolve("seed/content/articles/old.articles.json")),
                "束外 + 管辖内 = 陈旧，必须删除");
        assertTrue(Files.exists(themeDir.resolve("seed/placements/legacy.json")),
                "E1 不发射的种子族（placements/）不在管辖内，原样保留");
        assertEquals("<html>live</html>", Files.readString(themeDir.resolve("templates/index.html")),
                "seed/ 之外的主题文件不触碰");

        JsonNode manifest = Constants.MAPPER.readTree(themeDir.resolve("theme.manifest.json").toFile());
        assertEquals("Demo Live", manifest.path("name").asText(), "既有清单键原样保留");
        assertEquals("1.2.0", manifest.path("version").asText(), "未给版本则不动（不自动递增，§7.1）");
        assertEquals("non-destructive", manifest.path("seed").path("policy").asText());
        assertTrue(manifest.path("seed").path("content").path("channels").asBoolean());
        assertFalse(manifest.path("seed").path("content").path("articles").asBoolean(),
                "未发射的类别声明必须落为 false——过期的 true 声明误导安装");
        assertFalse(manifest.path("seed").path("content").path("assets").asBoolean());

        // 第二次刷新：内容未变——无陈旧文件、清单语义无变化不写盘
        byte[] manifestBytes = Files.readAllBytes(themeDir.resolve("theme.manifest.json"));
        ThemeSeedExportService.RefreshReport second = service.refreshSeedDir(SITE, THEME,
                SeedExportRequest.defaults(), false, false, null, false);
        assertEquals(0, second.removed());
        assertFalse(second.manifestUpdated());
        assertArrayEquals(manifestBytes, Files.readAllBytes(themeDir.resolve("theme.manifest.json")));
    }

    @Test
    void refreshCompositionScopeLeavesContentSeedAlone() throws IOException {
        TreeMap<String, byte[]> files = new TreeMap<>();
        files.put("regions/home.regions.json", "{\"page\":\"home\"}".getBytes(StandardCharsets.UTF_8));
        stubExport(bundle(files, CAP, List.of()));
        SeedExportRequest request = new SeedExportRequest(null, null, null, null, null,
                SeedExportRequest.DEFAULT_ARTICLE_CAP, false, false, true);
        ThemeSeedExportService.RefreshReport report = service.refreshSeedDir(SITE, THEME, request,
                false, false, null, false);
        assertEquals(0, report.removed(), "构成范围不管辖 content/**——内容种子不删");
        assertTrue(Files.exists(themeDir.resolve("seed/content/articles/old.articles.json")));
        JsonNode manifest = Constants.MAPPER.readTree(themeDir.resolve("theme.manifest.json").toFile());
        assertTrue(manifest.path("seed").path("content").path("articles").asBoolean(),
                "内容范围不在本次导出内：seed.content 声明不触碰");
    }

    @Test
    void refreshOnActiveThemeRequiresConfirm() {
        stubExport(stubBundle());
        site.setTheme(THEME);
        ThemeInstallException e = assertThrows(ThemeInstallException.class, () ->
                service.refreshSeedDir(SITE, THEME, SeedExportRequest.defaults(), false, false, null, false));
        assertEquals("theme.export.error.activeThemeConfirm", e.getMessage());
        // Q1：允许，但须显式确认
        assertEquals(2, service.refreshSeedDir(SITE, THEME, SeedExportRequest.defaults(),
                false, false, null, true).written());
    }

    @Test
    void refreshVersionParamUpdatesManifest() throws IOException {
        stubExport(stubBundle());
        service.refreshSeedDir(SITE, THEME, SeedExportRequest.defaults(), false, false, "2.0.0", false);
        JsonNode manifest = Constants.MAPPER.readTree(themeDir.resolve("theme.manifest.json").toFile());
        assertEquals("2.0.0", manifest.path("version").asText());
    }

    // ----- 大小预算（§7.3）-----

    @Test
    void oversizeBundleFailsFastWithOffenderList() {
        TreeMap<String, byte[]> files = new TreeMap<>();
        files.put("content/assets/img/big.png", new byte[64]);
        stubExport(bundle(files, 16, List.of()));
        ThemeInstallException e = assertThrows(ThemeInstallException.class, () ->
                service.exportZip(SITE, THEME, SeedExportRequest.defaults(), true, false, false, null));
        assertEquals("theme.export.error.oversize", e.getMessage());
        assertTrue(e.getDetail().contains("img/big.png"), "快速失败必须点名最大资源（§7.3）");
        assertThrows(ThemeInstallException.class, () ->
                service.refreshSeedDir(SITE, THEME, SeedExportRequest.defaults(), false, false, null, false));
    }

    // ----- dataOnlyZip -----

    @Test
    void dataOnlyZipSynthesizesManifestAndPassesValidator() throws IOException {
        stubExport(stubBundle());
        ThemeSeedExportService.ZipPackage zip = service.exportZip(SITE, THEME,
                SeedExportRequest.defaults(), true, false, false, null);
        assertEquals("demo-1.2.0.zip", zip.filename(), "版本缺省沿用活清单值");
        assertArrayEquals(zip.bytes(), service.exportZip(SITE, THEME, SeedExportRequest.defaults(),
                true, false, false, null).bytes(), "确定性（D4）");

        ThemeInstallReport report = new ThemeInstallValidator(Constants.MAPPER)
                .validate(new ByteArrayInputStream(zip.bytes()), zip.bytes().length, CAP);
        assertNotNull(report.getMetadata());
        assertTrue(report.getMetadata().isDataOnly(), "合成清单必须声明 kind: data-only（§8）");

        Map<String, byte[]> entries = readZip(zip.bytes());
        assertEquals(List.of("seed/content/channels.channels.json", "seed/regions/home.regions.json",
                "theme.manifest.json"), new ArrayList<>(entries.keySet()));
        JsonNode manifest = Constants.MAPPER.readTree(entries.get("theme.manifest.json"));
        assertEquals("Demo Live", manifest.path("name").asText(), "展示名沿用活清单");
        assertEquals(THEME, manifest.path("themeName").asText());
        assertEquals("data-only", manifest.path("kind").asText());
        assertTrue(manifest.path("seed").path("content").path("channels").asBoolean());
        assertFalse(manifest.path("seed").path("content").path("articles").asBoolean());
    }

    // ----- themeZip -----

    @Test
    void themeZipPacksLiveDirWithRefreshedSeed() throws IOException {
        stubExport(stubBundle());
        ThemeSeedExportService.ZipPackage zip = service.exportZip(SITE, THEME,
                SeedExportRequest.defaults(), false, false, false, "3.0.0");
        assertEquals("demo-3.0.0.zip", zip.filename());
        Map<String, byte[]> entries = readZip(zip.bytes());
        assertEquals(List.of(
                "seed/content/channels.channels.json",
                "seed/placements/legacy.json",
                "seed/regions/home.regions.json",
                "templates/index.html",
                "theme.manifest.json"), new ArrayList<>(entries.keySet()),
                "活目录 + 刷新种子；范围外 legacy 种子随包保留；管辖内陈旧文件不入包");
        assertArrayEquals("{\"page\":\"home\",\"regions\":{}}".getBytes(StandardCharsets.UTF_8),
                entries.get("seed/regions/home.regions.json"), "seed/** 取导出束而非磁盘旧文件");
        JsonNode manifest = Constants.MAPPER.readTree(entries.get("theme.manifest.json"));
        assertEquals("3.0.0", manifest.path("version").asText());
        assertFalse(manifest.path("seed").path("content").path("articles").asBoolean());
        // ZIP 模式只读：磁盘清单与旧种子不动
        JsonNode diskManifest = Constants.MAPPER.readTree(themeDir.resolve("theme.manifest.json").toFile());
        assertEquals("1.2.0", diskManifest.path("version").asText());
        assertTrue(Files.exists(themeDir.resolve("seed/content/articles/old.articles.json")));
    }

    // ----- 认领委派（§4.2）-----

    @Test
    void adoptOriginDelegatesToAdopter() {
        List<SeedAdoption> adoptions = List.of(
                new SeedAdoption(SeedAdoption.Category.ARTICLE, 9L, "a-story", "hash"));
        stubExport(bundle(new TreeMap<>(Map.of("content/channels.channels.json",
                "{}".getBytes(StandardCharsets.UTF_8))), CAP, adoptions));
        when(adopter.adopt("theme:" + THEME, adoptions)).thenReturn(1);
        ThemeSeedExportService.ZipPackage zip = service.exportZip(SITE, THEME,
                SeedExportRequest.defaults(), true, true, false, null);
        assertEquals(1, zip.adopted());
        verify(adopter).adopt("theme:" + THEME, adoptions);
    }

    @Test
    void noAdoptWithoutOptIn() {
        stubExport(stubBundle());
        assertEquals(0, service.exportZip(SITE, THEME, SeedExportRequest.defaults(),
                true, false, false, null).adopted());
        verify(adopter, never()).adopt(any(), any());
    }

    // ----- 设置快照（§7.4 / D21）-----

    /**
     * 三作用域声明齐备的清单夹具：全局 brand.slogan（text，有默认）+ brand.hero（media）、
     * 变体 channel:video 的 video.columns、页面 channel 的 listing.showSummary。
     */
    private void writeSettingsManifests() throws IOException {
        Files.writeString(themeDir.resolve("theme.manifest.json"), """
                {
                  "name": "Demo Live",
                  "themeName": "demo",
                  "version": "1.2.0",
                  "settings": { "groups": [
                    { "name": "brand", "fields": {
                      "slogan": { "type": "text", "default": "作者默认标语" },
                      "hero": { "type": "media" }
                    } }
                  ] },
                  "layoutVariants": { "channel": [
                    { "value": "video", "template": "channel_video",
                      "settings": { "groups": [
                        { "name": "video", "fields": {
                          "columns": { "type": "enum", "values": ["2", "3", "4"], "default": "3" }
                        } }
                      ] } }
                  ] }
                }
                """);
        Files.createDirectories(themeDir.resolve("pages"));
        Files.writeString(themeDir.resolve("pages/channel.manifest.json"), """
                {
                  "name": "channel",
                  "settings": { "groups": [
                    { "name": "listing", "fields": {
                      "showSummary": { "type": "boolean", "default": true }
                    } }
                  ] }
                }
                """);
        Files.writeString(themeDir.resolve("pages/home.manifest.json"), "{\n  \"name\": \"home\"\n}\n");
    }

    /**
     * 已存设置值（theme_state.settings_json_ 形）：三作用域各有已声明值 + 未声明键。
     */
    private void stubStoredSettings() {
        ThemeState state = new ThemeState();
        state.setId(99L);
        state.setSiteId(SITE);
        state.setThemeName(THEME);
        Map<String, Object> brand = new LinkedHashMap<>();
        brand.put("slogan", "配置的标语");
        brand.put("hero", Map.of("url", "/uploads/2026/07/hero.jpg"));
        brand.put("gone", "未声明字段");
        Map<String, Map<String, Object>> global = new LinkedHashMap<>();
        global.put("brand", brand);
        state.getSettings().setGlobal(global);
        Map<String, Map<String, Map<String, Object>>> pages = new LinkedHashMap<>();
        pages.put("channel", Map.of("listing", Map.of("showSummary", false)));
        pages.put("nosuch", Map.of("x", Map.of("y", 1)));
        state.getSettings().setPages(pages);
        Map<String, Map<String, Map<String, Object>>> variants = new LinkedHashMap<>();
        variants.put("channel:video", Map.of("video", Map.of("columns", "4")));
        variants.put("channel:nosuch", Map.of("x", Map.of("y", 1)));
        state.getSettings().setVariants(variants);
        when(settingsService.findState(SITE, THEME)).thenReturn(state);
    }

    @Test
    void snapshotWritesStoredValuesIntoManifestDefaults() throws IOException {
        writeSettingsManifests();
        stubStoredSettings();
        stubExport(stubBundle());
        ThemeSeedExportService.RefreshReport report = service.refreshSeedDir(SITE, THEME,
                SeedExportRequest.defaults(), false, true, null, false);
        assertTrue(report.manifestUpdated());

        JsonNode manifest = Constants.MAPPER.readTree(themeDir.resolve("theme.manifest.json").toFile());
        JsonNode brandFields = manifest.path("settings").path("groups").path(0).path("fields");
        assertEquals("配置的标语", brandFields.path("slogan").path("default").asText(),
                "全局作用域已存值写入声明默认值");
        assertEquals("/uploads/2026/07/hero.jpg",
                brandFields.path("hero").path("default").path("url").asText(),
                "媒体值原样快照（不做令牌化，D21）");
        assertFalse(brandFields.has("gone"), "未声明字段绝不写入清单");
        assertEquals("4", manifest.path("layoutVariants").path("channel").path(0)
                        .path("settings").path("groups").path(0).path("fields").path("columns")
                        .path("default").asText(), "变体作用域已存值写入声明默认值");
        assertEquals("1.2.0", manifest.path("version").asText(), "快照不自动递增版本（§7.1）");
        JsonNode pageManifest = Constants.MAPPER.readTree(
                themeDir.resolve("pages/channel.manifest.json").toFile());
        assertFalse(pageManifest.path("settings").path("groups").path(0).path("fields")
                .path("showSummary").path("default").asBoolean(), "页面作用域已存值写入页面清单");

        ExportPlan.SettingsSnapshot snapshot = report.plan().settingsSnapshot();
        assertNotNull(snapshot);
        assertEquals(4, snapshot.fields(), "slogan + hero + columns + showSummary");
        assertEquals(List.of("brand.gone", "variants.channel:nosuch", "pages.nosuch"),
                snapshot.dropped(), "未声明键丢弃 + 计数（H5 口径），绝不失败");
        assertEquals(List.of("theme.manifest.json", "pages/channel.manifest.json"),
                snapshot.manifests());
        assertEquals(1, snapshot.issues().size(), "本站上传 URL 值计可移植性提示");
        assertEquals("/uploads/2026/07/hero.jpg", snapshot.issues().get(0).ref());

        // 二次刷新：语义无变化——主题清单与页面清单都不写盘
        byte[] manifestBytes = Files.readAllBytes(themeDir.resolve("theme.manifest.json"));
        byte[] pageBytes = Files.readAllBytes(themeDir.resolve("pages/channel.manifest.json"));
        ThemeSeedExportService.RefreshReport second = service.refreshSeedDir(SITE, THEME,
                SeedExportRequest.defaults(), false, true, null, false);
        assertFalse(second.manifestUpdated(), "二次快照刷新 = 无操作");
        assertArrayEquals(manifestBytes, Files.readAllBytes(themeDir.resolve("theme.manifest.json")));
        assertArrayEquals(pageBytes, Files.readAllBytes(themeDir.resolve("pages/channel.manifest.json")));
    }

    @Test
    void snapshotOffLeavesManifestsByteIdentical() throws IOException {
        writeSettingsManifests();
        stubStoredSettings();
        stubExport(stubBundle());
        byte[] pageBytes = Files.readAllBytes(themeDir.resolve("pages/channel.manifest.json"));
        ThemeSeedExportService.RefreshReport report = service.refreshSeedDir(SITE, THEME,
                SeedExportRequest.defaults(), false, false, null, false);
        JsonNode manifest = Constants.MAPPER.readTree(themeDir.resolve("theme.manifest.json").toFile());
        assertEquals("作者默认标语", manifest.path("settings").path("groups").path(0).path("fields")
                .path("slogan").path("default").asText(), "快照关闭：声明默认值不动（无清单搅动）");
        assertArrayEquals(pageBytes, Files.readAllBytes(themeDir.resolve("pages/channel.manifest.json")),
                "快照关闭：页面清单逐字节不动");
        assertNull(report.plan().settingsSnapshot(), "快照关闭：报告不附快照节");
    }

    @Test
    void snapshotBakesIntoThemeZipWithoutTouchingDisk() throws IOException {
        writeSettingsManifests();
        stubStoredSettings();
        stubExport(stubBundle());
        byte[] diskManifest = Files.readAllBytes(themeDir.resolve("theme.manifest.json"));
        byte[] diskPage = Files.readAllBytes(themeDir.resolve("pages/channel.manifest.json"));
        byte[] diskHome = Files.readAllBytes(themeDir.resolve("pages/home.manifest.json"));
        ThemeSeedExportService.ZipPackage zip = service.exportZip(SITE, THEME,
                SeedExportRequest.defaults(), false, false, true, null);
        Map<String, byte[]> entries = readZip(zip.bytes());
        JsonNode manifest = Constants.MAPPER.readTree(entries.get("theme.manifest.json"));
        assertEquals("配置的标语", manifest.path("settings").path("groups").path(0).path("fields")
                .path("slogan").path("default").asText(), "包内清单烘焙快照默认值");
        JsonNode pageManifest = Constants.MAPPER.readTree(entries.get("pages/channel.manifest.json"));
        assertFalse(pageManifest.path("settings").path("groups").path(0).path("fields")
                .path("showSummary").path("default").asBoolean());
        assertArrayEquals(diskHome, entries.get("pages/home.manifest.json"),
                "无该页面存值：页面清单原字节随包（不产生格式噪音）");
        assertArrayEquals(diskManifest, Files.readAllBytes(themeDir.resolve("theme.manifest.json")),
                "ZIP 模式只读：磁盘清单不动");
        assertArrayEquals(diskPage, Files.readAllBytes(themeDir.resolve("pages/channel.manifest.json")));
        // 确定性（D4）：同一存值重复快照打包字节一致
        assertArrayEquals(zip.bytes(), service.exportZip(SITE, THEME, SeedExportRequest.defaults(),
                false, false, true, null).bytes());
    }

    @Test
    void previewAlwaysReportsSettingsSnapshot() throws IOException {
        writeSettingsManifests();
        stubStoredSettings();
        stubExport(stubBundle());
        byte[] diskManifest = Files.readAllBytes(themeDir.resolve("theme.manifest.json"));
        ExportPlan plan = service.preview(SITE, THEME, SeedExportRequest.defaults());
        ExportPlan.SettingsSnapshot snapshot = plan.settingsSnapshot();
        assertNotNull(snapshot, "预览恒附快照报告（§7.4：第 2 步先于第 3 步开关展示）");
        assertEquals(4, snapshot.fields());
        assertEquals(List.of("brand.gone", "variants.channel:nosuch", "pages.nosuch"), snapshot.dropped());
        assertArrayEquals(diskManifest, Files.readAllBytes(themeDir.resolve("theme.manifest.json")),
                "预览为干跑：清单不落盘");
    }

    private static Map<String, byte[]> readZip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), in.readAllBytes());
                in.closeEntry();
            }
        }
        return entries;
    }
}
