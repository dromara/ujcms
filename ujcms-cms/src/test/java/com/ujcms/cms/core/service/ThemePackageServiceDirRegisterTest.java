package com.ujcms.cms.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResourceLoader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujcms.cms.core.component.ThemeInstallException;
import com.ujcms.cms.core.component.ThemeInstallValidator;
import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.ThemeLifecycleLock;
import com.ujcms.cms.core.composition.ThemeSettingsService;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.ThemePackage;
import com.ujcms.cms.core.domain.ThemeState;
import com.ujcms.cms.core.mapper.ThemePackageMapper;
import com.ujcms.cms.core.service.ThemePackageService.DirRegisterReport;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.PathResolver;

/**
 * 目录登记单测（install-flow §14 / D41-D43）：真文件系统 + 真校验器 + 内存版包 mapper。
 * 覆盖登记主链（库行 + ZIP + 状态行 + 重扫）、无清单目录名兜底、不合规目录的响亮失败
 * （零写入）、幂等重登记（零写入跳过）与重打包字节确定性。
 */
class ThemePackageServiceDirRegisterTest {

    private Path templatesRoot;
    private Path packagesRoot;
    private ThemePackageService service;
    private ThemePackageMapper mapper;
    private ManifestService manifestService;
    private ThemeSettingsService themeSettingsService;

    /** 内存版包行存储（name@version → 行），insert/update 经 Answer 写入 */
    private final Map<String, ThemePackage> rows = new HashMap<>();

    @BeforeEach
    void setup(@TempDir Path tempDir) throws IOException {
        templatesRoot = tempDir.resolve("themes");
        packagesRoot = tempDir.resolve("theme-packages");
        Files.createDirectories(templatesRoot.resolve("1"));
        Files.createDirectories(packagesRoot);
        FileSystemResourceLoader resourceLoader = new FileSystemResourceLoader();
        ObjectMapper objectMapper = new ObjectMapper();

        Site site = mock(Site.class);
        when(site.getId()).thenReturn(1L);
        SiteService siteService = mock(SiteService.class);
        when(siteService.select(1L)).thenReturn(site);

        ConfigService configService = mock(ConfigService.class);
        Config config = mock(Config.class);
        when(config.getTemplateStorage()).thenReturn(new Config.Storage(0,
                templatesRoot.toUri().toString(), templatesRoot.toUri().toString()));
        when(config.getPackageStorage()).thenReturn(new Config.Storage(0,
                packagesRoot.toUri().toString(), packagesRoot.toUri().toString()));
        when(configService.getUnique()).thenReturn(config);

        PathResolver pathResolver = mock(PathResolver.class);
        when(pathResolver.getRealPath(any(), any())).thenAnswer(inv -> {
            String uri = inv.getArgument(0);
            String prefix = inv.getArgument(1);
            return prefix.substring("file:".length()) + uri.replace('/', File.separatorChar);
        });

        ThemeInstallValidator validator = new ThemeInstallValidator(objectMapper);
        manifestService = mock(ManifestService.class);
        themeSettingsService = mock(ThemeSettingsService.class);

        com.ujcms.cms.core.support.Props props = new com.ujcms.cms.core.support.Props();
        props.setVersion("11.0.0");
        ThemeInstallService installService = new ThemeInstallService(resourceLoader, validator,
                siteService, configService, pathResolver, props, objectMapper,
                manifestService, mock(com.ujcms.cms.core.composition.ThemeSeedOrchestrator.class),
                mock(com.ujcms.cms.core.composition.ThemeContentSeedRemover.class),
                themeSettingsService,
                mock(com.ujcms.cms.core.composition.PlacementService.class),
                mock(com.ujcms.cms.core.mapper.PlacementMapper.class),
                mock(com.ujcms.cms.core.mapper.PageRegionMapper.class),
                mock(com.ujcms.cms.core.mapper.ChannelMapper.class),
                mock(com.ujcms.cms.core.mapper.ArticleMapper.class),
                mock(com.ujcms.cms.core.mapper.TopicMapper.class),
                mock(com.ujcms.cms.core.mapper.BlockMapper.class),
                mock(com.ujcms.cms.ext.mapper.VoteMapper.class),
                mock(com.ujcms.cms.ext.mapper.SurveyMapper.class),
                mock(com.ujcms.cms.ext.mapper.FormTypeMapper.class),
                mock(com.ujcms.cms.ext.mapper.LinkGroupMapper.class),
                mock(com.ujcms.cms.core.mapper.ModelMapper.class),
                mock(com.ujcms.cms.core.mapper.DictTypeMapper.class),
                mock(com.ujcms.cms.core.mapper.ThemeStateMapper.class),
                new ThemeLifecycleLock(),
                mock(LayoutVariantService.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        mapper = mock(ThemePackageMapper.class);
        rows.clear();
        when(mapper.selectByNameAndVersion(any(), any())).thenAnswer(inv ->
                rows.get(inv.getArgument(0) + "@" + inv.getArgument(1)));
        doAnswer(inv -> {
            ThemePackage bean = inv.getArgument(0);
            rows.put(bean.getName() + "@" + bean.getVersion(), bean);
            return null;
        }).when(mapper).insert(any());
        doAnswer(inv -> {
            ThemePackage bean = inv.getArgument(0);
            rows.put(bean.getName() + "@" + bean.getVersion(), bean);
            return null;
        }).when(mapper).update(any());

        service = new ThemePackageService(mapper, validator, installService, configService,
                pathResolver, resourceLoader, new SnowflakeSequence(0, 0),
                new ThemeLifecycleLock(), manifestService, themeSettingsService);
    }

    private void writeTheme(String name, String manifestJson) throws IOException {
        Path dir = templatesRoot.resolve("1").resolve(name);
        Files.createDirectories(dir.resolve("templates"));
        Files.writeString(dir.resolve("templates/index.html"), "<html>hi</html>");
        if (manifestJson != null) {
            Files.writeString(dir.resolve("theme.manifest.json"), manifestJson);
        }
    }

    @Test
    void registerCreatesLibraryRowStateRowAndStoredZip() throws IOException {
        writeTheme("acme", "{\"themeName\":\"acme\",\"name\":\"Acme\",\"version\":\"1.2.3\"}");

        DirRegisterReport report = service.registerFromDirectory(1L, "acme");

        assertFalse(report.alreadyRegistered());
        assertEquals("acme", report.name());
        assertEquals("1.2.3", report.version());
        ThemePackage saved = rows.get("acme@1.2.3");
        assertEquals(ThemePackage.SOURCE_REGISTERED, saved.getSource());
        assertNull(saved.getMarketplaceSlug());
        assertTrue(Files.isRegularFile(packagesRoot.resolve("acme/1.2.3.zip")));
        // 状态行补录（来历标记）+ 注册表重扫
        verify(themeSettingsService).recordVersion(1L, "acme", "1.2.3");
        verify(manifestService).scanAll(any(), any());
    }

    @Test
    void registerManifestlessDirFallsBackToDirName() throws IOException {
        // D27 上传安装兜底口径的目录版：无清单历史目录以目录名入库、版本 0.0.0、状态行版本为 null
        writeTheme("legacy", null);

        DirRegisterReport report = service.registerFromDirectory(1L, "legacy");

        assertEquals("legacy", report.name());
        assertEquals("0.0.0", report.version());
        assertTrue(Files.isRegularFile(packagesRoot.resolve("legacy/0.0.0.zip")));
        verify(themeSettingsService).recordVersion(eq(1L), eq("legacy"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void registerFailsLoudOnCompositionJsonViolationAndWritesNothing() throws IOException {
        // 构成目录混入非 JSON：真校验器口径原样生效（规则零改动），失败即零写入
        writeTheme("bad", "{\"themeName\":\"bad\",\"version\":\"1.0.0\"}");
        Path pages = templatesRoot.resolve("1/bad/pages");
        Files.createDirectories(pages);
        Files.writeString(pages.resolve("evil.txt"), "not json");

        assertThrows(ThemeInstallException.class, () -> service.registerFromDirectory(1L, "bad"));

        assertTrue(rows.isEmpty());
        verify(themeSettingsService, never()).recordVersion(any(), any(), any());
        verify(manifestService, never()).scanAll(any(), any());
    }

    @Test
    void registerFailsLoudOnInvalidCompositionArtifacts() throws IOException {
        writeTheme("brokenart", "{\"themeName\":\"brokenart\",\"version\":\"1.0.0\"}");
        when(manifestService.validateThemeArtifacts(eq("1/brokenart"), any()))
                .thenReturn(List.of("shape x invalid"));

        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.registerFromDirectory(1L, "brokenart"));

        assertEquals("theme.install.error.compositionInvalid", ex.getMessage());
        assertTrue(rows.isEmpty());
        verify(themeSettingsService, never()).recordVersion(any(), any(), any());
    }

    @Test
    void reRegisterIsZeroWriteSkipAndRepackIsDeterministic() throws IOException {
        writeTheme("stable", "{\"themeName\":\"stable\",\"version\":\"2.0.0\"}");

        DirRegisterReport first = service.registerFromDirectory(1L, "stable");
        assertFalse(first.alreadyRegistered());

        // 状态行此后在场 → 第二次登记命中幂等分支：零写入、摘要不变（重打包确定性，D25 口径）
        when(themeSettingsService.findState(1L, "stable")).thenReturn(new ThemeState());
        DirRegisterReport second = service.registerFromDirectory(1L, "stable");

        assertTrue(second.alreadyRegistered());
        assertEquals(first.sha256(), second.sha256());
        verify(mapper, org.mockito.Mockito.times(1)).insert(any());
        verify(mapper, never()).update(any());
        // recordVersion 只发生于首次登记
        verify(themeSettingsService, org.mockito.Mockito.times(1)).recordVersion(any(), any(), any());
    }

    @Test
    void registerMissingDirFailsWithNotFound() {
        ThemeInstallException ex = assertThrows(ThemeInstallException.class,
                () -> service.registerFromDirectory(1L, "ghost"));
        assertEquals("theme.install.error.notFound", ex.getMessage());
    }
}
