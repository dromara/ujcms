package com.ujcms.cms.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujcms.cms.core.service.ThemeInstallService.ActivateTarget;
import com.ujcms.cms.core.component.ThemeInfo;
import com.ujcms.cms.core.component.ThemeInstallException;
import com.ujcms.cms.core.component.ThemeInstallReport;
import com.ujcms.cms.core.component.ThemeInstallValidator;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;

/**
 * Integration tests for {@link ThemeInstallService} using real filesystem extraction with
 * mocked Site/Config services. These tests exercise the install / uninstall / activate / list
 * flows end-to-end.
 */
class ThemeInstallServiceTest {

    private Path templatesRoot;
    private FileSystemResourceLoader resourceLoader;
    private SiteService siteService;
    private ConfigService configService;
    private ThemeInstallService service;

    private Site site;

    @BeforeEach
    void setup(@TempDir Path tempDir) {
        templatesRoot = tempDir.resolve("themes");
        try {
            Files.createDirectories(templatesRoot.resolve("1"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        resourceLoader = new FileSystemResourceLoader();
        ObjectMapper objectMapper = new ObjectMapper();

        // Site stub — id=1, theme="default", mobileTheme="default" (bare names, R2).
        site = mock(Site.class);
        when(site.getId()).thenReturn(1L);
        when(site.getName()).thenReturn("Site One");
        when(site.getTheme()).thenReturn("default");
        when(site.getMobileTheme()).thenReturn("default");
        // Theme keys derive from id + bare name (mirrors the real Site.getThemeKey()).
        when(site.getThemeKey()).thenAnswer(inv -> site.getId() + "/" + site.getTheme());
        when(site.getMobileThemeKey()).thenAnswer(inv -> site.getId() + "/" + site.getMobileTheme());
        // setTheme/setMobileTheme — capture the value back into the mock so subsequent reads
        // reflect the change.
        doAnswer(inv -> {
            String v = inv.getArgument(0);
            when(site.getTheme()).thenReturn(v);
            return null;
        }).when(site).setTheme(any());
        doAnswer(inv -> {
            String v = inv.getArgument(0);
            when(site.getMobileTheme()).thenReturn(v);
            return null;
        }).when(site).setMobileTheme(any());

        siteService = mock(SiteService.class);
        when(siteService.select(1L)).thenReturn(site);

        configService = mock(ConfigService.class);
        Config config = mock(Config.class);
        Config.Storage storage = new Config.Storage(0, templatesRoot.toUri().toString(),
                                                     templatesRoot.toUri().toString());
        when(config.getTemplateStorage()).thenReturn(storage);
        when(configService.getUnique()).thenReturn(config);

        ThemeInstallValidator validator = new ThemeInstallValidator(objectMapper);
        // Phase 5 composition collaborators — mocked; these tests cover install/uninstall/activate/list
        // file mechanics + the canonical /{siteId}/{themeName} activation path, not seed/impact flows.
        manifestService = mock(com.ujcms.cms.core.composition.ManifestService.class);
        seedOrchestrator = mock(com.ujcms.cms.core.composition.ThemeSeedOrchestrator.class);
        contentSeedRemover = mock(com.ujcms.cms.core.composition.ThemeContentSeedRemover.class);
        when(seedOrchestrator.importSeed(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any()))
                .thenReturn(new com.ujcms.cms.core.composition.ThemeSeedOrchestrator.SeedRunResult(
                        com.ujcms.cms.core.composition.ThemeContentSeedImporter.ContentSeedResult.EMPTY,
                        com.ujcms.cms.core.composition.ThemeSeedImporter.SeedImportResult.EMPTY));
        // install() 走 6 参重载（缺省 fillOnly）——安装报告并入 undeclaredSlots 警告（H5）需非空结果
        when(seedOrchestrator.importSeed(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new com.ujcms.cms.core.composition.ThemeSeedOrchestrator.SeedRunResult(
                        com.ujcms.cms.core.composition.ThemeContentSeedImporter.ContentSeedResult.EMPTY,
                        com.ujcms.cms.core.composition.ThemeSeedImporter.SeedImportResult.EMPTY));
        when(seedOrchestrator.plan(any(), any(), any(), any()))
                .thenReturn(new com.ujcms.cms.core.composition.SeedPlan(
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        com.ujcms.cms.core.composition.SeedPlan.CategoryPlan.EMPTY,
                        0L, java.util.List.of()));
        when(seedOrchestrator.declaredContent(any()))
                .thenReturn(com.ujcms.cms.core.composition.ThemeContentSeedImporter.DeclaredContent.EMPTY);
        themeSettingsService = mock(com.ujcms.cms.core.composition.ThemeSettingsService.class);
        placementService = mock(com.ujcms.cms.core.composition.PlacementService.class);
        placementMapper = mock(com.ujcms.cms.core.mapper.PlacementMapper.class);
        pageRegionMapper = mock(com.ujcms.cms.core.mapper.PageRegionMapper.class);
        channelMapper = mock(com.ujcms.cms.core.mapper.ChannelMapper.class);
        articleMapper = mock(com.ujcms.cms.core.mapper.ArticleMapper.class);
        topicMapper = mock(com.ujcms.cms.core.mapper.TopicMapper.class);
        blockMapper = mock(com.ujcms.cms.core.mapper.BlockMapper.class);
        voteMapper = mock(com.ujcms.cms.ext.mapper.VoteMapper.class);
        surveyMapper = mock(com.ujcms.cms.ext.mapper.SurveyMapper.class);
        formTypeMapper = mock(com.ujcms.cms.ext.mapper.FormTypeMapper.class);
        linkGroupMapper = mock(com.ujcms.cms.ext.mapper.LinkGroupMapper.class);
        themeStateMapper = mock(com.ujcms.cms.core.mapper.ThemeStateMapper.class);
        // Sane defaults so the orphan-aware activate path (findOrphans) doesn't NPE in legacy tests:
        // empty snapshot → no declared slots; empty placement sets → no orphans.
        when(manifestService.getSnapshot())
                .thenReturn(com.ujcms.cms.core.composition.RegistrySnapshot.empty());
        when(placementService.listBySiteId(any())).thenReturn(java.util.List.of());
        layoutVariantService = mock(LayoutVariantService.class);
        when(layoutVariantService.countOrphanedTemplateSelections(any(), any(), any(), any())).thenReturn(0);
        // PathResolver 桩：模板存储 path 为 file: URI（上方 Storage 构造），LocalFileHandler 走
        // 前缀剥离分支——与生产 file: 配置同一路径解析语义（copyThemeInstance 用）
        pathResolver = mock(com.ujcms.common.web.PathResolver.class);
        when(pathResolver.getRealPath(any(), any())).thenAnswer(inv -> {
            String uri = inv.getArgument(0);
            String prefix = inv.getArgument(1);
            return prefix.substring("file:".length()) + uri.replace('/', java.io.File.separatorChar);
        });
        // 运行版本（H2 版本兼容门槛）：测试固定为 11.0.0；单测可自行改写以覆盖边界
        props = new com.ujcms.cms.core.support.Props();
        props.setVersion("11.0.0");
        service = new ThemeInstallService(resourceLoader, validator,
                                          siteService, configService, pathResolver, props, objectMapper,
                                          manifestService, seedOrchestrator, contentSeedRemover,
                                          themeSettingsService,
                                          placementService, placementMapper,
                                          pageRegionMapper, channelMapper, articleMapper, topicMapper,
                                          blockMapper, voteMapper, surveyMapper, formTypeMapper,
                                          linkGroupMapper,
                                          org.mockito.Mockito.mock(com.ujcms.cms.core.mapper.ModelMapper.class),
                                          org.mockito.Mockito.mock(com.ujcms.cms.core.mapper.DictTypeMapper.class),
                                          themeStateMapper,
                                          new com.ujcms.cms.core.composition.ThemeLifecycleLock(),
                                          layoutVariantService,
                                          new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    private com.ujcms.cms.core.support.Props props;
    private com.ujcms.common.web.PathResolver pathResolver;
    private com.ujcms.cms.core.composition.ManifestService manifestService;
    private LayoutVariantService layoutVariantService;
    private com.ujcms.cms.core.composition.ThemeSeedOrchestrator seedOrchestrator;
    private com.ujcms.cms.core.composition.ThemeContentSeedRemover contentSeedRemover;
    private com.ujcms.cms.core.composition.ThemeSettingsService themeSettingsService;
    private com.ujcms.cms.core.composition.PlacementService placementService;
    private com.ujcms.cms.core.mapper.PlacementMapper placementMapper;
    private com.ujcms.cms.core.mapper.PageRegionMapper pageRegionMapper;
    private com.ujcms.cms.core.mapper.ChannelMapper channelMapper;
    private com.ujcms.cms.core.mapper.ArticleMapper articleMapper;
    private com.ujcms.cms.core.mapper.TopicMapper topicMapper;
    private com.ujcms.cms.core.mapper.BlockMapper blockMapper;
    private com.ujcms.cms.ext.mapper.VoteMapper voteMapper;
    private com.ujcms.cms.ext.mapper.SurveyMapper surveyMapper;
    private com.ujcms.cms.ext.mapper.FormTypeMapper formTypeMapper;
    private com.ujcms.cms.ext.mapper.LinkGroupMapper linkGroupMapper;
    private com.ujcms.cms.core.mapper.ThemeStateMapper themeStateMapper;

    private byte[] zipOf(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                zos.putNextEntry(entry);
                if (e.getValue() != null) zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private MultipartFile multipart(byte[] zip) {
        return new TestMultipart(zip, "theme.zip");
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void installThemeExtractsFiles() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("snippets/header.html", bytes("hdr"));
        entries.put("theme.manifest.json",
                bytes("{\"name\":\"Acme\",\"version\":\"1.0.0\"}"));
        byte[] zip = zipOf(entries);

        ThemeInstallReport report = service.install(multipart(zip), 1L, "acme", null);

        assertNotNull(report);
        assertEquals(1, report.getTemplateFileCount());
        assertTrue(Files.exists(templatesRoot.resolve("1/acme/templates/index.html")));
        assertTrue(Files.exists(templatesRoot.resolve("1/acme/snippets/header.html")));
    }

    @Test
    void installRejectsCollidingThemeName() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/acme"));
        Files.writeString(templatesRoot.resolve("1/acme/.placeholder"), "");

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.install(multipart(zip), 1L, "acme", null));
        assertEquals("theme.install.error.themeExists", ex.getMessage());
    }

    @Test
    void updateSwapsInPlaceAndLeavesNoTransientDirs() throws IOException {
        // 装一个版本，再更新到新版本：新内容就位、旧文件消失、且站点目录下不留 .staging-/.backup- 瞬态目录
        service.install(multipart(zipOf(Map.of(
                "templates/index.html", bytes("v1"),
                "theme.manifest.json", bytes("{\"name\":\"Acme\",\"version\":\"1.0.0\"}")))),
                1L, "acme", null);
        assertTrue(Files.exists(templatesRoot.resolve("1/acme/templates/index.html")));

        service.update(multipart(zipOf(Map.of(
                "templates/index.html", bytes("v2"),
                "templates/about.html", bytes("about"),
                "theme.manifest.json", bytes("{\"name\":\"Acme\",\"version\":\"2.0.0\"}")))),
                1L, "acme", com.ujcms.cms.core.service.ThemeInstallService.SeedScope.NONE);

        // 新内容就位
        assertEquals("v2", Files.readString(templatesRoot.resolve("1/acme/templates/index.html")));
        assertTrue(Files.exists(templatesRoot.resolve("1/acme/templates/about.html")));
        // 无遗留瞬态目录（移旧到一旁 → 移入 → 删备份，§21 Q12(d)）
        try (var entries = Files.list(templatesRoot.resolve("1"))) {
            assertTrue(entries.map(p -> p.getFileName().toString())
                    .noneMatch(com.ujcms.cms.core.composition.ThemeDirScanner::isTransientDir));
        }
    }

    @Test
    void installRejectsInvalidThemeName() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.install(multipart(zip), 1L, "bad/name", null));
        assertEquals("theme.install.error.invalidName", ex.getMessage());
    }

    @Test
    void installWithActivateUpdatesSiteTheme() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("snippets/header.html", bytes("hdr"));
        byte[] zip = zipOf(entries);

        service.install(multipart(zip), 1L, "newtheme", ActivateTarget.DESKTOP);

        verify(siteService, atLeastOnce()).update(site);
        assertEquals("newtheme", site.getTheme());
        assertEquals("default", site.getMobileTheme(), "mobile theme should be untouched");
    }

    @Test
    void installWithActivateMobileUpdatesBothThemes() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        byte[] zip = zipOf(entries);

        service.install(multipart(zip), 1L, "newtheme", ActivateTarget.BOTH);

        assertEquals("newtheme", site.getTheme());
        assertEquals("newtheme", site.getMobileTheme());
    }

    /** 仅手机激活（R8）：安装后只改 mobileTheme，桌面主题原样——手机参考主题的日常入径 */
    @Test
    void installWithActivateMobileOnlyLeavesDesktopUntouched() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        byte[] zip = zipOf(entries);

        service.install(multipart(zip), 1L, "newtheme", ActivateTarget.MOBILE);

        assertEquals("default", site.getTheme(), "desktop theme should be untouched");
        assertEquals("newtheme", site.getMobileTheme());
    }

    @Test
    void installRollsBackOnExtractionFailure() throws IOException {
        // Construct a zip whose final entry has a path that the validator accepts but extraction
        // would fail on. The defense-in-depth check in extractTo catches it. To simulate this
        // without bypassing validator, we instead test that a non-existent siteId fails before
        // any extraction occurs.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        byte[] zip = zipOf(entries);

        when(siteService.select(99L)).thenReturn(null);
        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.install(multipart(zip), 99L, "acme", null));
        assertEquals("theme.install.error.siteNotFound", ex.getMessage());
        assertFalse(Files.exists(templatesRoot.resolve("99/acme")));
    }

    @Test
    void uninstallDeletesDirectory() throws IOException {
        Path themeDir = templatesRoot.resolve("1/acme");
        Files.createDirectories(themeDir.resolve("templates"));
        Files.writeString(themeDir.resolve("templates/index.html"), "x");

        service.uninstall(1L, "acme");

        assertFalse(Files.exists(themeDir));
    }

    @Test
    void uninstallBlockedWhenThemeIsActive() throws IOException {
        Path themeDir = templatesRoot.resolve("1/inuse");
        Files.createDirectories(themeDir);
        Files.writeString(themeDir.resolve("templates-placeholder.txt"), "x");
        when(site.getTheme()).thenReturn("inuse");

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.uninstall(1L, "inuse"));
        assertEquals("theme.install.error.themeInUse", ex.getMessage());
        assertTrue(Files.exists(themeDir), "theme should still exist after rejection");
    }

    @Test
    void uninstallBlockedWhenThemeIsActiveMobile() throws IOException {
        Path themeDir = templatesRoot.resolve("1/mobile");
        Files.createDirectories(themeDir);
        when(site.getMobileTheme()).thenReturn("mobile");

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.uninstall(1L, "mobile"));
        assertEquals("theme.install.error.themeInUse", ex.getMessage());
    }

    @Test
    void uninstallNonexistentThrows() {
        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.uninstall(1L, "ghost"));
        assertEquals("theme.install.error.notFound", ex.getMessage());
    }

    @Test
    void listThemesReturnsMetadataAndActiveFlags() throws IOException {
        Path acmeTheme = templatesRoot.resolve("1/acme");
        Files.createDirectories(acmeTheme.resolve("templates"));
        Files.writeString(acmeTheme.resolve("templates/index.html"), "");
        Files.writeString(acmeTheme.resolve("theme.manifest.json"),
                "{\"name\":\"Acme Theme\",\"version\":\"1.0.0\"}");

        Path defaultTheme = templatesRoot.resolve("1/default");
        Files.createDirectories(defaultTheme.resolve("templates"));
        Files.writeString(defaultTheme.resolve("templates/index.html"), "");

        when(site.getTheme()).thenReturn("default");

        List<ThemeInfo> themes = service.listThemes(1L);
        assertEquals(2, themes.size());
        ThemeInfo def = themes.stream().filter(t -> "default".equals(t.getThemeName()))
                .findFirst().orElseThrow();
        assertTrue(def.isActive());

        ThemeInfo acme = themes.stream().filter(t -> "acme".equals(t.getThemeName()))
                .findFirst().orElseThrow();
        assertEquals("Acme Theme", acme.getDisplayName());
        assertEquals("1.0.0", acme.getVersion());
        assertFalse(acme.isActive());
    }

    @Test
    void listUnregisteredThemeDirsWalksDirsMinusStateRows() throws IOException {
        // 检测面（install-flow §14 / D43）：站点目录减 theme_state 行；瞬态目录跳过
        Files.createDirectories(templatesRoot.resolve("1/acme"));
        Files.createDirectories(templatesRoot.resolve("1/billy"));
        Files.createDirectories(templatesRoot.resolve("1/acme.staging-42"));
        Files.createDirectories(templatesRoot.resolve("1/acme.backup-42"));
        when(themeStateMapper.selectByUnique(1L, "billy"))
                .thenReturn(new com.ujcms.cms.core.domain.ThemeState());

        List<String> unregistered = service.listUnregisteredThemeDirs(1L);
        assertEquals(List.of("acme"), unregistered);

        // 画廊标记同口径：billy 已登记、acme 未登记
        Files.createDirectories(templatesRoot.resolve("1/acme/templates"));
        Files.writeString(templatesRoot.resolve("1/acme/templates/index.html"), "");
        Map<String, ThemeInfo> byName = new LinkedHashMap<>();
        service.listThemes(1L).forEach(info -> byName.put(info.getThemeName(), info));
        assertFalse(byName.get("acme").isRegistered());
        assertTrue(byName.get("billy").isRegistered());
    }

    @Test
    void activateUpdatesSite() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/newtheme"));
        service.activate(1L, "newtheme", ActivateTarget.DESKTOP);
        assertEquals("newtheme", site.getTheme());
        verify(siteService).update(site);
    }

    /** 仅手机激活（R8）：只改 mobileTheme，桌面主题原样 */
    @Test
    void activateMobileOnlyLeavesDesktopUntouched() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/newtheme"));
        service.activate(1L, "newtheme", ActivateTarget.MOBILE);
        assertEquals("default", site.getTheme());
        assertEquals("newtheme", site.getMobileTheme());
        verify(siteService).update(site);
    }

    @Test
    void activateNonexistentThemeThrows() {
        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.activate(1L, "ghost", ActivateTarget.DESKTOP));
        assertEquals("theme.install.error.notFound", ex.getMessage());
    }

    /**
     * §6.1 安全不变量 3：激活永不删除任何东西——孤儿一律归档、种子导入只增不删，
     * 「删除旧主题演示数据」是切换后的独立显式动作（removeSeed），激活自身绝不触发。
     */
    @Test
    void activationNeverDeletesAnything() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/newtheme"));
        service.activate(1L, "newtheme", ActivateTarget.BOTH,
                         ThemeInstallService.SeedScope.ALL,
                         com.ujcms.cms.core.composition.SeedOverwrite.SYNC_UNMODIFIED);

        // 内容行：移除器完全不被触碰（无 remove/keep/archive 调用）
        org.mockito.Mockito.verifyNoInteractions(contentSeedRemover);
        // 构成行：不存在任何删除路径（孤儿一律归档；删除是健康页的逐行显式动作）
        verify(placementService, never()).delete(any());
        verify(pageRegionMapper, never()).delete(any());
    }

    /**
     * End-to-end smoke test: package the reference {@code default} theme into a ZIP
     * (matching the marketplace contract) and run it through the full install pipeline.
     * Skipped automatically when the reference theme isn't on disk so this test stays useful
     * locally without breaking CI in environments that prune the webapp tree.
     */
    @Test
    void referenceThemeInstallsCleanly(@TempDir Path zipDir) throws IOException {
        Path source = Path.of("..", "ujcms-starter", "src", "main", "webapp",
                              "templates", "1", "default").toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) return; // reference theme not present; skip
        Path zip = zipDir.resolve("default.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip),
                                                       StandardCharsets.UTF_8)) {
            Files.walk(source).forEach(p -> writeIntoZip(p, source, zos));
        }
        byte[] bytes = Files.readAllBytes(zip);

        ThemeInstallReport report = service.install(multipart(bytes), 1L, "ref-test", null);

        assertTrue(report.getTemplateFileCount() > 0);
        assertTrue(Files.isDirectory(templatesRoot.resolve("1/ref-test/templates")));
        assertTrue(Files.isDirectory(templatesRoot.resolve("1/ref-test/snippets")));
        // theme.manifest.json is preserved
        assertTrue(Files.isRegularFile(templatesRoot.resolve("1/ref-test/theme.manifest.json")));
    }

    private void writeIntoZip(Path file, Path root, ZipOutputStream zos) {
        if (Files.isDirectory(file)) return;
        String relative = root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
        // Skip noise that real marketplace ZIPs would also exclude: build inputs/outputs that
        // are gitignored in the repo (node_modules, vendored assets) plus dev-only dirs.
        if (relative.startsWith("node_modules/") || relative.startsWith("_src/")
                || relative.startsWith("assets/vendor/") || relative.startsWith("_files/")
                || relative.equals("package-lock.json")
                || relative.startsWith(".") || relative.contains("/.")) return;
        try {
            zos.putNextEntry(new ZipEntry(relative));
            Files.copy(file, zos);
            zos.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void installRejectsPartiallyMaliciousZipBeforeWritingFiles() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("../escape.html", bytes("nope"));
        byte[] zip = zipOf(entries);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.install(multipart(zip), 1L, "newtheme", null));
        assertEquals("theme.install.error.zipSlip", ex.getMessage());
        assertFalse(Files.exists(templatesRoot.resolve("1/newtheme")));
    }

    // ===== Phase 5: composition lifecycle (seed on install, impact analysis, orphan switch) =====

    private com.ujcms.cms.core.composition.RegistrySnapshot compositionSnapshot() {
        com.ujcms.cms.core.composition.CompositionSchemas schemas =
                new com.ujcms.cms.core.composition.CompositionSchemas();
        com.ujcms.cms.core.composition.ShapeLoader sl = new com.ujcms.cms.core.composition.ShapeLoader(schemas);
        com.ujcms.cms.core.composition.ManifestLoader ml = new com.ujcms.cms.core.composition.ManifestLoader(schemas);
        String promo = "{\"name\":\"PromoCard\",\"version\":\"1.0.0\",\"item\":{\"title\":{\"type\":\"text\"}}}";
        String hero = "{\"name\":\"HeroCard\",\"version\":\"1.0.0\",\"item\":{\"title\":{\"type\":\"text\"}}}";
        String homeA = "{\"name\":\"home\",\"template\":\"index\","
                + "\"slots\":{\"promos\":{\"kind\":\"region\",\"allowedShapes\":[\"PromoCard\"]}}}";
        String homeB = "{\"name\":\"home\",\"template\":\"index\","
                + "\"slots\":{\"hero\":{\"kind\":\"region\",\"allowedShapes\":[\"HeroCard\"]}}}";
        String homeM = "{\"name\":\"home\",\"template\":\"index\","
                + "\"slots\":{\"promos\":{\"kind\":\"region\",\"allowedShapes\":[\"PromoCard\"]}}}";
        return com.ujcms.cms.core.composition.RegistrySnapshot.builder()
                .addThemeShape("1/themeA", sl.load(promo, "theme:1/themeA", "p"), promo, "p")
                .addThemeShape("1/themeB", sl.load(hero, "theme:1/themeB", "h"), hero, "h")
                .addThemeShape("1/themeMobile", sl.load(promo, "theme:1/themeMobile", "pm"), promo, "pm")
                .addThemeManifest("1/themeA", ml.load(homeA, "a"), homeA, "a")
                .addThemeManifest("1/themeB", ml.load(homeB, "b"), homeB, "b")
                .addThemeManifest("1/themeMobile", ml.load(homeM, "m"), homeM, "m")
                .build();
    }

    private Site siteUsing(String desktop, String mobile) {
        Site s = mock(Site.class);
        when(s.getId()).thenReturn(1L);
        when(s.getName()).thenReturn("Site One");
        when(s.getTheme()).thenReturn(desktop);
        when(s.getMobileTheme()).thenReturn(mobile);
        when(s.getThemeKey()).thenAnswer(inv -> s.getId() + "/" + s.getTheme());
        when(s.getMobileThemeKey()).thenAnswer(inv -> s.getId() + "/" + s.getMobileTheme());
        doAnswer(inv -> {
            when(s.getTheme()).thenReturn((String) inv.getArgument(0));
            return null;
        }).when(s).setTheme(any());
        doAnswer(inv -> {
            when(s.getMobileTheme()).thenReturn((String) inv.getArgument(0));
            return null;
        }).when(s).setMobileTheme(any());
        return s;
    }

    /** 面板形按区域取 compositionSnapshot 中该区域声明的形（sidebar 无主题声明，任取一个可解析的形——由区域规则判孤立） */
    private com.ujcms.cms.core.domain.Placement placement(long id, String slot) {
        return placement(id, slot, "hero".equals(slot) ? "HeroCard" : "PromoCard");
    }

    private com.ujcms.cms.core.domain.Placement placement(long id, String slot, String shape) {
        com.ujcms.cms.core.domain.Placement p = new com.ujcms.cms.core.domain.Placement();
        p.setId(id);
        p.setSiteId(1L);
        p.setPageRef("home");
        p.setSlotName(slot);
        p.setPanelShape(shape);
        return p;
    }

    private com.ujcms.cms.core.domain.Placement archivedPlacement(long id, String slot) {
        com.ujcms.cms.core.domain.Placement p = placement(id, slot);
        p.setArchivedAt(java.time.OffsetDateTime.now());
        return p;
    }

    @Test
    void previewActivateMobileThemeStillDeclaringSlotIsNotOrphaned() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/themeB"));
        Site s1 = siteUsing("themeA", "themeMobile");
        when(siteService.select(1L)).thenReturn(s1);
        when(manifestService.getSnapshot()).thenReturn(compositionSnapshot());
        when(placementService.listBySiteId(1L)).thenReturn(List.of(placement(10, "promos"), placement(11, "sidebar")));

        // Desktop-only switch A→B; mobile stays themeMobile (which still declares home/promos).
        com.ujcms.cms.core.composition.ThemeImpactReport report =
                service.previewActivate(1L, "themeB", ActivateTarget.DESKTOP);

        // promos is saved by the mobile theme (site-level contract); only sidebar is orphaned.
        assertEquals(1, report.orphanedPlacements().size());
        assertEquals("sidebar", report.orphanedPlacements().get(0).slotName());
    }

    /** 仅手机切换（R8）：结果主题对 = {当前桌面主题, 目标}——桌面主题仍声明的插槽不算孤儿 */
    @Test
    void previewActivateMobileOnlyKeepsDesktopSlotsCovered() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/themeB"));
        Site s1 = siteUsing("themeMobile", "themeA");
        when(siteService.select(1L)).thenReturn(s1);
        when(manifestService.getSnapshot()).thenReturn(compositionSnapshot());
        when(placementService.listBySiteId(1L)).thenReturn(List.of(placement(10, "promos"), placement(11, "sidebar")));

        // Mobile-only switch A→B; desktop stays themeMobile (declares home/hero + home/promos).
        com.ujcms.cms.core.composition.ThemeImpactReport report =
                service.previewActivate(1L, "themeB", ActivateTarget.MOBILE);

        assertEquals(1, report.orphanedPlacements().size());
        assertEquals("sidebar", report.orphanedPlacements().get(0).slotName());
        assertEquals("themeMobile", s1.getTheme(), "preview is read-only");
        assertEquals("themeA", s1.getMobileTheme(), "preview is read-only");
    }

    /** 仅手机切换（R8）：桌面槽位不动，孤儿按 {桌面现主题, 目标} 结果对判定 */
    @Test
    void switchMobileOnlyArchivesOrphansAgainstResultingPair() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/themeB"));
        Site s = siteUsing("themeA", "themeMobile");
        when(siteService.select(1L)).thenReturn(s);
        when(manifestService.getSnapshot()).thenReturn(compositionSnapshot());
        when(placementService.listBySiteId(1L)).thenReturn(List.of(placement(10, "promos"), placement(11, "sidebar")));

        service.activate(1L, "themeB", ActivateTarget.MOBILE,
                com.ujcms.cms.core.service.ThemeInstallService.SeedScope.NONE);

        // promos still declared by desktop themeA → kept; sidebar declared by neither → archived
        verify(placementService, never()).archive(10L);
        verify(placementService).archive(11L);
        assertEquals("themeA", s.getTheme(), "desktop slot untouched");
        assertEquals("themeB", s.getMobileTheme());
    }

    @Test
    void switchBothThemesArchivesOrphanedPlacements() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/themeB"));
        Site s = siteUsing("themeA", "themeMobile");
        when(siteService.select(1L)).thenReturn(s);
        when(manifestService.getSnapshot()).thenReturn(compositionSnapshot());
        when(placementService.listBySiteId(1L)).thenReturn(List.of(placement(10, "promos"), placement(11, "sidebar")));

        // Switch BOTH desktop + mobile to themeB (declares only home/hero) → promos no longer covered.
        service.activate(1L, "themeB", ActivateTarget.BOTH,
                com.ujcms.cms.core.service.ThemeInstallService.SeedScope.NONE);

        verify(placementService).archive(10L);
        verify(placementService).archive(11L);
        assertEquals("themeB", s.getTheme());
        assertEquals("themeB", s.getMobileTheme());
    }

    /** 切回自动恢复（D21）：页面/区域重新被声明的归档投放在激活时取消归档——切换往返零手工 */
    @Test
    void switchBackRestoresArchivedPlacementsWhoseSlotIsDeclaredAgain() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/themeA"));
        Site s = siteUsing("themeB", "themeB");
        when(siteService.select(1L)).thenReturn(s);
        when(manifestService.getSnapshot()).thenReturn(compositionSnapshot());
        // 30: archived promos (declared by themeA again) → restore; 31: archived sidebar
        // (declared by neither) → stays archived, never re-archived; 32: active hero
        // (declared only by themeB) → orphaned by the switch back → archive.
        when(placementService.listBySiteId(1L)).thenReturn(List.of(
                archivedPlacement(30, "promos"), archivedPlacement(31, "sidebar"), placement(32, "hero")));

        service.activate(1L, "themeA", ActivateTarget.BOTH,
                com.ujcms.cms.core.service.ThemeInstallService.SeedScope.NONE);

        verify(placementService).restore(30L);
        verify(placementService, never()).restore(31L);
        verify(placementService, never()).archive(31L);
        verify(placementService).archive(32L);
    }

    /** 形不被目标主题对解析的投放同样孤立（D23）：区域虽被声明，面板在新主题下只会渲染成「未知面板类型」 */
    @Test
    void switchArchivesPlacementWhoseShapeResolvesInNeitherResultingTheme() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/themeB"));
        Site s = siteUsing("themeA", "themeA");
        when(siteService.select(1L)).thenReturn(s);
        when(manifestService.getSnapshot()).thenReturn(compositionSnapshot());
        // 40: home/hero 由 themeB 声明但形 PromoCard 仅 themeA/themeMobile 有 → 归档；41: 同区域 HeroCard → 保留
        when(placementService.listBySiteId(1L)).thenReturn(List.of(
                placement(40, "hero", "PromoCard"), placement(41, "hero", "HeroCard")));

        service.activate(1L, "themeB", ActivateTarget.BOTH,
                com.ujcms.cms.core.service.ThemeInstallService.SeedScope.NONE);

        verify(placementService).archive(40L);
        verify(placementService, never()).archive(41L);
    }

    /** 自动恢复同样要求形可解析（D23）：仅区域重新被声明不够 */
    @Test
    void switchRestoresArchivedPlacementOnlyWhenSlotDeclaredAndShapeResolves() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/themeB"));
        Site s = siteUsing("themeA", "themeA");
        when(siteService.select(1L)).thenReturn(s);
        when(manifestService.getSnapshot()).thenReturn(compositionSnapshot());
        com.ujcms.cms.core.domain.Placement restorable = placement(42, "hero", "HeroCard");
        restorable.setArchivedAt(java.time.OffsetDateTime.now());
        com.ujcms.cms.core.domain.Placement wrongShape = placement(43, "hero", "PromoCard");
        wrongShape.setArchivedAt(java.time.OffsetDateTime.now());
        when(placementService.listBySiteId(1L)).thenReturn(List.of(restorable, wrongShape));

        service.activate(1L, "themeB", ActivateTarget.BOTH,
                com.ujcms.cms.core.service.ThemeInstallService.SeedScope.NONE);

        verify(placementService).restore(42L);
        verify(placementService, never()).restore(43L);
        verify(placementService, never()).archive(43L);
    }

    /** 影响预览携带自动恢复计数（D21）：restorablePlacements = 重新被声明的归档投放数 */
    @Test
    void previewActivateCountsRestorablePlacements() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/themeA"));
        Site s = siteUsing("themeB", "themeB");
        when(siteService.select(1L)).thenReturn(s);
        when(manifestService.getSnapshot()).thenReturn(compositionSnapshot());
        when(placementService.listBySiteId(1L)).thenReturn(List.of(
                archivedPlacement(30, "promos"), archivedPlacement(31, "sidebar"), placement(32, "hero")));

        com.ujcms.cms.core.composition.ThemeImpactReport report =
                service.previewActivate(1L, "themeA", ActivateTarget.BOTH);

        assertEquals(1, report.restorablePlacements());
        assertEquals(1, report.orphanedPlacements().size());
        assertEquals("hero", report.orphanedPlacements().get(0).slotName());
        // preview is read-only
        verify(placementService, never()).restore(any());
        verify(placementService, never()).archive(any());
    }

    @Test
    void installWithSeedTriggersSeedImport() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("seed/regions/home.regions.json", bytes("{}"));
        byte[] zip = zipOf(entries);

        service.install(multipart(zip), 1L, "seedtheme", null,
                com.ujcms.cms.core.service.ThemeInstallService.SeedScope.COMPOSITION);

        // scope=composition：经编排器运行，includeContent=false（内容种子仅 scope=all，R5/A2）
        verify(seedOrchestrator).importSeed(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("seedtheme"), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void installSurfacesUndeclaredSlotWarningInReport() throws IOException {
        // 种子里未声明的区域插槽（跳过明细）并入安装报告 warnings（H5）——控制台既有警告展示直接呈现
        when(seedOrchestrator.importSeed(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new com.ujcms.cms.core.composition.ThemeSeedOrchestrator.SeedRunResult(
                        com.ujcms.cms.core.composition.ThemeContentSeedImporter.ContentSeedResult.EMPTY,
                        new com.ujcms.cms.core.composition.ThemeSeedImporter.SeedImportResult(
                                0, 0, 0, 0, 0, 0, 1, List.of("home#ghost#shared"), List.of(), List.of())));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("seed/regions/home.regions.json", bytes("{}"));
        byte[] zip = zipOf(entries);

        com.ujcms.cms.core.component.ThemeInstallReport report = service.install(multipart(zip), 1L,
                "ghosttheme", null,
                com.ujcms.cms.core.service.ThemeInstallService.SeedScope.COMPOSITION);

        assertTrue(report.getWarnings().stream().anyMatch(w -> w.contains("home#ghost#shared")));
    }

    @Test
    void installWithSeedScopeAllIncludesContent() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("seed/content/channels.channels.json", bytes("{}"));
        byte[] zip = zipOf(entries);

        service.install(multipart(zip), 1L, "fulltheme", null,
                com.ujcms.cms.core.service.ThemeInstallService.SeedScope.ALL);

        verify(seedOrchestrator).importSeed(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("fulltheme"), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(true));
    }

    // ----- §8 data-only packages (A5): installable, never activatable; lifecycle unchanged -----

    private static final String DATA_ONLY_MANIFEST =
            "{\"name\":\"SSR Demo\",\"themeName\":\"ssrdemo\",\"version\":\"1.0.0\","
                    + "\"templateSpecVersion\":\"2.0\",\"kind\":\"data-only\"}";

    private byte[] dataOnlyZip() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("theme.manifest.json", bytes(DATA_ONLY_MANIFEST));
        entries.put("seed/content/channels.channels.json", bytes("{}"));
        entries.put("seed/regions/home.regions.json", bytes("{}"));
        return zipOf(entries);
    }

    /** 就地写一个已装好的纯数据包目录（绕过 install，供激活门槛测试用）。 */
    private void writeDataOnlyThemeDir(String themeName) throws IOException {
        Path dir = templatesRoot.resolve("1/" + themeName);
        Files.createDirectories(dir.resolve("seed/content"));
        Files.writeString(dir.resolve("theme.manifest.json"), DATA_ONLY_MANIFEST);
        Files.writeString(dir.resolve("seed/content/channels.channels.json"), "{}");
    }

    @Test
    void installDataOnlyPackageSucceedsWithoutActivate() throws IOException {
        ThemeInstallReport report = service.install(multipart(dataOnlyZip()), 1L, "ssrdemo", null);

        assertTrue(report.getMetadata().isDataOnly());
        assertEquals(0, report.getTemplateFileCount());
        assertTrue(Files.exists(templatesRoot.resolve("1/ssrdemo/seed/content/channels.channels.json")));
        // 画廊无需额外往返：listThemes 内嵌 metadata（含 kind），种子状态照常
        ThemeInfo info = service.listThemes(1L).stream()
                .filter(t -> "ssrdemo".equals(t.getThemeName())).findFirst().orElseThrow();
        assertTrue(info.getMetadata().isDataOnly());
        assertTrue(info.isSeedAvailable());
    }

    @Test
    void installDataOnlyWithActivateRejectedBeforeExtraction() throws IOException {
        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.install(multipart(dataOnlyZip()), 1L, "ssrdemo", ActivateTarget.DESKTOP));

        assertEquals("theme.install.error.notActivatable", ex.getMessage());
        assertFalse(Files.exists(templatesRoot.resolve("1/ssrdemo")), "解包前拦下，不留半成品目录");
        assertEquals("default", site.getTheme());
    }

    @Test
    void activateDataOnlyThemeRejected() throws IOException {
        writeDataOnlyThemeDir("ssrdemo");

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.activate(1L, "ssrdemo", ActivateTarget.DESKTOP));

        assertEquals("theme.install.error.notActivatable", ex.getMessage());
        assertEquals("default", site.getTheme());
        verify(siteService, never()).update(site);
    }

    @Test
    void previewActivateDataOnlyThemeRejected() throws IOException {
        writeDataOnlyThemeDir("ssrdemo");

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.previewActivate(1L, "ssrdemo", ActivateTarget.DESKTOP));
        assertEquals("theme.install.error.notActivatable", ex.getMessage());
    }

    @Test
    void updateActiveThemeToDataOnlyRejected() throws IOException {
        // 不变量：纯数据包永不处于激活态——生效中的主题不可被 update 换成纯数据包
        Path activeDir = templatesRoot.resolve("1/default");
        Files.createDirectories(activeDir);
        Files.writeString(activeDir.resolve("index.html"), "live");

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.update(multipart(dataOnlyZip()), 1L, "default",
                        ThemeInstallService.SeedScope.NONE));

        assertEquals("theme.install.error.notActivatable", ex.getMessage());
        assertEquals("live", Files.readString(activeDir.resolve("index.html")), "旧文件原样保留");
    }

    @Test
    void updateInactiveThemeToDataOnlyAllowed() throws IOException {
        // 非生效主题换成纯数据包合法（包身份换代）；反向（数据包换主题包）走普通 update 路径
        Path dir = templatesRoot.resolve("1/spare");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("index.html"), "old");

        service.update(multipart(dataOnlyZip()), 1L, "spare", ThemeInstallService.SeedScope.NONE);

        assertFalse(Files.exists(dir.resolve("index.html")));
        assertTrue(Files.exists(dir.resolve("seed/content/channels.channels.json")));
    }

    @Test
    void validateThemeAssignableRejectsInstalledDataOnly() throws IOException {
        // 站点保存是激活的等价写入面（D15 补遗）：绑定到已装纯数据包目录一律 400
        writeDataOnlyThemeDir("ssrdemo");

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.validateThemeAssignable(1L, "ssrdemo"));
        assertEquals("theme.install.error.notActivatable", ex.getMessage());
    }

    @Test
    void validateThemeAssignableKeepsLegacyLenience() throws IOException {
        // 不存在的主题名（建站复制流程先写名后装包）、普通主题、空值：一律放行
        Files.createDirectories(templatesRoot.resolve("1/normal"));
        Files.writeString(templatesRoot.resolve("1/normal/index.html"), "x");

        service.validateThemeAssignable(1L, "ghost");
        service.validateThemeAssignable(1L, "normal");
        service.validateThemeAssignable(1L, null);
    }

    @Test
    void dataOnlySeedLifecycleUnchanged() throws IOException {
        // 解耦的 seed/import 与卸载 dataPolicy 对纯数据包原样生效（§6.1 政策不因 kind 而变）
        service.install(multipart(dataOnlyZip()), 1L, "ssrdemo", null);

        service.importSeed(1L, "ssrdemo", ThemeInstallService.SeedScope.ALL,
                com.ujcms.cms.core.composition.SeedOverwrite.FILL_ONLY);
        verify(seedOrchestrator).importSeed(eq(1L), eq("ssrdemo"), any(), any(), any(),
                eq(true), eq(com.ujcms.cms.core.composition.SeedOverwrite.FILL_ONLY));

        service.uninstall(1L, "ssrdemo", ThemeInstallService.UninstallDataPolicy.DELETE);
        verify(contentSeedRemover).remove(eq(1L), eq("ssrdemo"), eq(false), any());
        assertFalse(Files.exists(templatesRoot.resolve("1/ssrdemo")));
    }

    // ----- H2 / P2-2: minUjcmsVersion 版本兼容门槛（安装 / 更新 / 激活面三处强制） -----

    private byte[] themeZipWithMinVersion(String minUjcmsVersion) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("templates/index.html", bytes("hi"));
        entries.put("theme.manifest.json", bytes("{\"name\":\"Acme\",\"version\":\"1.0.0\","
                + "\"minUjcmsVersion\":\"" + minUjcmsVersion + "\"}"));
        return zipOf(entries);
    }

    /** 就地写一个已装好的主题目录（模拟磁盘拷入 / 早于门槛安装的主题，供激活面测试用）。 */
    private void writeThemeDirWithMinVersion(String themeName, String minUjcmsVersion) throws IOException {
        Path dir = templatesRoot.resolve("1/" + themeName);
        Files.createDirectories(dir.resolve("templates"));
        Files.writeString(dir.resolve("templates/index.html"), "hi");
        Files.writeString(dir.resolve("theme.manifest.json"),
                "{\"name\":\"Acme\",\"version\":\"1.0.0\",\"minUjcmsVersion\":\"" + minUjcmsVersion + "\"}");
    }

    @Test
    void installRejectsIncompatibleMinUjcmsVersion() throws IOException {
        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.install(multipart(themeZipWithMinVersion("99.0.0")), 1L, "future", null));

        assertEquals("theme.install.error.versionIncompatible", ex.getMessage());
        // 报错点名「要求版本 vs 当前版本」（经 MessageSource {0}/{1} 替换后抵达控制台）
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[]{"99.0.0", "11.0.0"}, ex.getArgs());
        assertTrue(ex.getDetail().contains("99.0.0") && ex.getDetail().contains("11.0.0"));
        assertFalse(Files.exists(templatesRoot.resolve("1/future")), "解包前拦下，不留半成品目录");
    }

    @Test
    void updateRejectsIncompatibleMinUjcmsVersion() throws IOException {
        service.install(multipart(zipOf(Map.of(
                "templates/index.html", bytes("v1"),
                "theme.manifest.json", bytes("{\"name\":\"Acme\",\"version\":\"1.0.0\"}")))),
                1L, "acme", null);

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.update(multipart(themeZipWithMinVersion("99.0.0")), 1L, "acme",
                        ThemeInstallService.SeedScope.NONE));

        assertEquals("theme.install.error.versionIncompatible", ex.getMessage());
        assertEquals("v1", Files.readString(templatesRoot.resolve("1/acme/templates/index.html")),
                "替换文件前拦下，旧版本原样保留");
    }

    @Test
    void activateRejectsIncompatibleMinUjcmsVersion() throws IOException {
        // 磁盘拷入的主题目录（未经 install 门槛）：激活时仍被拦截
        writeThemeDirWithMinVersion("future", "99.0.0");

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.activate(1L, "future", ActivateTarget.DESKTOP));

        assertEquals("theme.install.error.versionIncompatible", ex.getMessage());
        assertEquals("default", site.getTheme());
        verify(siteService, never()).update(site);

        // 激活的等价写入面同样拦截：preview-activate 与站点表单绑定（D15 补遗）
        assertEquals("theme.install.error.versionIncompatible",
                assertThrows(ThemeInstallException.class,
                        () -> service.previewActivate(1L, "future", ActivateTarget.DESKTOP)).getMessage());
        assertEquals("theme.install.error.versionIncompatible",
                assertThrows(ThemeInstallException.class,
                        () -> service.validateThemeAssignable(1L, "future")).getMessage());
    }

    @Test
    void versionGatePassesWhenRequirementMet() throws IOException {
        // 低于当前（1.0.0）、恰好等于（11.0.0）、预发布后缀按 §13.5 忽略（11.0.0-beta ≡ 11.0.0）
        service.install(multipart(themeZipWithMinVersion("1.0.0")), 1L, "older", null);
        service.install(multipart(themeZipWithMinVersion("11.0.0")), 1L, "equal", null);
        service.install(multipart(themeZipWithMinVersion("11.0.0-beta")), 1L, "prerelease", null);
        assertTrue(Files.exists(templatesRoot.resolve("1/older/templates/index.html")));
        assertTrue(Files.exists(templatesRoot.resolve("1/equal/templates/index.html")));
        assertTrue(Files.exists(templatesRoot.resolve("1/prerelease/templates/index.html")));

        // 仅主版本段（"12" ≡ 12.0.0）同样参与比较：高于 11.0.0 → 拒绝
        assertEquals("theme.install.error.versionIncompatible",
                assertThrows(ThemeInstallException.class, () -> service.install(
                        multipart(themeZipWithMinVersion("12")), 1L, "major-only", null)).getMessage());
    }

    @Test
    void versionGateNoConstraintWhenMissingOrBlank() throws IOException {
        // 无清单（既有测试已覆盖）、清单无 minUjcmsVersion、空白串：一律不约束
        service.install(multipart(themeZipWithMinVersion("")), 1L, "blank", null);
        assertTrue(Files.exists(templatesRoot.resolve("1/blank/templates/index.html")));

        service.install(multipart(zipOf(Map.of(
                "templates/index.html", bytes("hi"),
                "theme.manifest.json", bytes("{\"name\":\"Acme\",\"version\":\"1.0.0\"}")))),
                1L, "unconstrained", null);
        assertTrue(Files.exists(templatesRoot.resolve("1/unconstrained/templates/index.html")));
    }

    @Test
    void versionGateSkippedWhenRuntimeVersionUnparseable() throws IOException {
        // 运行版本无法解析（IDE 直跑未过滤资源 → "@project.version@"；Props 缺省 "none"）：
        // 门槛是防呆护栏而非安全边界，记警告放行，不因构建配置问题瘫痪主题运维
        props.setVersion("none");
        service.install(multipart(themeZipWithMinVersion("99.0.0")), 1L, "lenient", null);
        assertTrue(Files.exists(templatesRoot.resolve("1/lenient/templates/index.html")));
    }

    // ----- H1 / P2-1: 主题预览覆盖门槛（?_theme= 渲染试看，SiteResolver 调用） -----

    @Test
    void themePreviewGateRejectsMissingInvalidDataOnlyAndIncompatible() throws IOException {
        // 目录不存在 → 响亮 400（绝不静默回退活动主题渲染）
        assertEquals("theme.install.error.notFound",
                assertThrows(ThemeInstallException.class,
                        () -> service.requireThemePreviewable(1L, "ghost")).getMessage());
        // 名称不合法（路径穿越等）在目录解析前拦截
        assertEquals("theme.install.error.invalidName",
                assertThrows(ThemeInstallException.class,
                        () -> service.requireThemePreviewable(1L, "../evil")).getMessage());
        // 激活不了的主题也不该试看：纯数据包与 minUjcmsVersion（P2-2）经 requireActivatable 同一口径
        writeDataOnlyThemeDir("ssrdemo");
        assertEquals("theme.install.error.notActivatable",
                assertThrows(ThemeInstallException.class,
                        () -> service.requireThemePreviewable(1L, "ssrdemo")).getMessage());
        writeThemeDirWithMinVersion("future", "99.0.0");
        assertEquals("theme.install.error.versionIncompatible",
                assertThrows(ThemeInstallException.class,
                        () -> service.requireThemePreviewable(1L, "future")).getMessage());
    }

    @Test
    void themePreviewGatePassesForInstalledActivatableTheme() throws IOException {
        writeThemeDirWithMinVersion("candidate", "1.0.0");
        service.requireThemePreviewable(1L, "candidate");
        // 活动主题自身同样可预览（覆盖为同值，渲染无差别）
        Files.createDirectories(templatesRoot.resolve("1/default"));
        service.requireThemePreviewable(1L, "default");
    }

    @Test
    void uninstallWithKeepPolicyClearsThemeOrigin() throws IOException {
        Path themeDir = templatesRoot.resolve("1/gone");
        Files.createDirectories(themeDir);
        Files.writeString(themeDir.resolve("placeholder.txt"), "x");

        service.uninstall(1L, "gone", com.ujcms.cms.core.service.ThemeInstallService.UninstallDataPolicy.KEEP);

        verify(placementMapper).clearOrigin(1L, "theme:gone");
        verify(pageRegionMapper).clearOrigin(1L, "theme:gone");
        assertFalse(Files.exists(themeDir));
    }

    // ----- copyThemeInstance（sandbox-preview-site §3.2 / D5/D6） -----

    @Test
    void copyThemeInstanceCopiesDirRevalidatesAndCopiesSettings() throws IOException {
        // 源站点装一个带 seed 的主题
        service.install(multipart(zipOf(Map.of(
                "templates/index.html", bytes("hi"),
                "seed/regions/home.json", bytes("{}"),
                "theme.manifest.json", bytes("{\"name\":\"Acme\",\"version\":\"1.0.0\"}")))),
                1L, "acme", null);
        // 源站点 theme_state 行带设置值（D6）
        com.ujcms.cms.core.domain.ThemeState fromState = new com.ujcms.cms.core.domain.ThemeState();
        fromState.setSiteId(1L);
        fromState.setThemeName("acme");
        fromState.setSettingsJson("{\"global\":{\"palette\":{\"primary\":\"#123456\"}}}");
        when(themeStateMapper.selectByUnique(1L, "acme")).thenReturn(fromState);
        com.ujcms.cms.core.domain.ThemeState toState = new com.ujcms.cms.core.domain.ThemeState();
        toState.setSiteId(2L);
        toState.setThemeName("acme");
        when(themeSettingsService.getOrCreate(2L, "acme")).thenReturn(toState);

        service.copyThemeInstance(1L, 2L, "acme");

        // 目录（含 seed/**）逐字节就位；制品校验按目标站点命名空间重跑；版本入册
        assertEquals("hi", Files.readString(templatesRoot.resolve("2/acme/templates/index.html")));
        assertTrue(Files.exists(templatesRoot.resolve("2/acme/seed/regions/home.json")));
        verify(manifestService).validateThemeArtifacts(eq("2/acme"), any());
        verify(themeSettingsService).recordVersion(2L, "acme", "1.0.0");
        // 设置值行复制（D6）
        verify(themeStateMapper).update(toState);
        assertEquals(fromState.getSettingsJson(), toState.getSettingsJson());
    }

    @Test
    void copyThemeInstanceRejectsMissingSourceAndExistingTarget() throws IOException {
        ThemeInstallException missing = assertThrows(ThemeInstallException.class,
                () -> service.copyThemeInstance(1L, 2L, "nope"));
        assertEquals("theme.install.error.notFound", missing.getMessage());

        Files.createDirectories(templatesRoot.resolve("1/dup"));
        Files.createDirectories(templatesRoot.resolve("2/dup"));
        ThemeInstallException exists = assertThrows(ThemeInstallException.class,
                () -> service.copyThemeInstance(1L, 2L, "dup"));
        assertEquals("theme.install.error.themeExists", exists.getMessage());
    }

    @Test
    void copyThemeInstanceRollsBackCopyOnInvalidArtifacts() throws IOException {
        service.install(multipart(zipOf(Map.of(
                "templates/index.html", bytes("hi"),
                "theme.manifest.json", bytes("{\"name\":\"Acme\",\"version\":\"1.0.0\"}")))),
                1L, "acme", null);
        when(manifestService.validateThemeArtifacts(eq("2/acme"), any()))
                .thenReturn(java.util.List.of("bad shape"));

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.copyThemeInstance(1L, 2L, "acme"));
        assertEquals("theme.install.error.compositionInvalid", ex.getMessage());
        // 副本回滚：目标目录不留半成品；源目录不受影响
        assertFalse(Files.exists(templatesRoot.resolve("2/acme")));
        assertTrue(Files.exists(templatesRoot.resolve("1/acme/templates/index.html")));
    }

    /** Lightweight MultipartFile for tests; only methods used by the installer are implemented. */
    private static class TestMultipart implements MultipartFile {
        private final byte[] bytes;
        private final String name;

        TestMultipart(byte[] bytes, String name) {
            this.bytes = bytes;
            this.name = name;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return "application/zip"; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }
        @Override public org.springframework.core.io.Resource getResource() {
            return new org.springframework.core.io.ByteArrayResource(bytes);
        }
        @Override public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), bytes);
        }
    }
}
