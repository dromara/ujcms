package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 主题目录扫描测试：shapes/pages 装载与命名空间、模板名绑定索引、多模板绑定、
 * 非清单文件忽略、损坏文件报错（带来源路径）。
 */
class ThemeDirScannerTest {
    private static final String SHAPE_JSON = """
            { "name": "PromoCard", "version": "1.0.0",
              "item": { "title": { "type": "text", "max": 150, "required": true } } }
            """;
    private static final String HOME_MANIFEST = """
            { "name": "home", "template": "index",
              "slots": { "promos": { "kind": "region", "allowedShapes": ["PromoCard"] } } }
            """;
    private static final String CHANNEL_MANIFEST = """
            { "name": "channel", "template": ["channel", "channel_video"],
              "slots": { "sidebar": { "kind": "region", "allowedShapes": ["PromoCard"] } } }
            """;

    @TempDir
    Path templatesRoot;

    private ThemeDirScanner scanner;

    @BeforeEach
    void setUp() {
        CompositionSchemas schemas = new CompositionSchemas();
        scanner = new ThemeDirScanner(new ShapeLoader(schemas), new ManifestLoader(schemas),
                new ThemeManifestLoader(schemas), mock(ConfigService.class), new DefaultResourceLoader());
    }

    private Path theme(String siteId, String name) throws IOException {
        Path dir = templatesRoot.resolve(siteId).resolve(name);
        Files.createDirectories(dir.resolve(ThemeDirScanner.PAGES_DIR));
        Files.createDirectories(dir.resolve(ThemeDirScanner.SHAPES_DIR));
        return dir;
    }

    @Test
    void scanLoadsShapesAndManifestsUnderThemeNamespace() throws IOException {
        Path theme = theme("1", "liquid-default");
        Files.writeString(theme.resolve("shapes/promo-card.json"), SHAPE_JSON);
        Files.writeString(theme.resolve("pages/home.manifest.json"), HOME_MANIFEST);
        Files.writeString(theme.resolve("pages/channel.manifest.json"), CHANNEL_MANIFEST);
        // pages/ 下非 *.manifest.json 的文件被忽略（清单文件后缀是约定的一部分）
        Files.writeString(theme.resolve("pages/readme.json"), "{}");

        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        scanner.scan(templatesRoot.toFile(), builder);
        RegistrySnapshot snapshot = builder.build();

        String themeKey = "1/liquid-default";
        assertNotNull(snapshot.resolveShape(themeKey, "PromoCard"));
        // 主题 Shape 只在自己的命名空间可见
        assertNull(snapshot.resolveShape("1/other", "PromoCard"));
        assertNotNull(snapshot.getThemeManifest(themeKey, "home"));
        assertNotNull(snapshot.getThemeManifest(themeKey, "channel"));
        assertNull(snapshot.getThemeManifest(themeKey, "readme"));
        assertEquals(2, snapshot.getManifestCount());
    }

    @Test
    void templateNameResolutionIsExactWithMultiTemplateBindingAndNoFallback() throws IOException {
        Path theme = theme("1", "liquid-default");
        Files.writeString(theme.resolve("shapes/promo-card.json"), SHAPE_JSON);
        Files.writeString(theme.resolve("pages/home.manifest.json"), HOME_MANIFEST);
        Files.writeString(theme.resolve("pages/channel.manifest.json"), CHANNEL_MANIFEST);

        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        scanner.scan(templatesRoot.toFile(), builder);
        RegistrySnapshot snapshot = builder.build();
        String themeKey = "1/liquid-default";

        // 精确匹配
        PageManifest home = snapshot.resolveThemeManifestByTemplate(themeKey, "index");
        assertNotNull(home);
        assertEquals("home", home.getName());
        // 多模板绑定：两个模板名解析到同一清单
        PageManifest channel = snapshot.resolveThemeManifestByTemplate(themeKey, "channel");
        PageManifest channelVideo = snapshot.resolveThemeManifestByTemplate(themeKey, "channel_video");
        assertNotNull(channel);
        assertEquals("channel", channel.getName());
        assertEquals(channel, channelVideo);
        // 未绑定模板无隐式回退（channel_news 不会回退到 channel）
        assertNull(snapshot.resolveThemeManifestByTemplate(themeKey, "channel_news"));
        // 其它主题的命名空间互不可见
        assertNull(snapshot.resolveThemeManifestByTemplate("1/other", "index"));
    }

    @Test
    void themesAreIsolatedNamespacesAndSameShapeNameMayRepeat() throws IOException {
        Path liquidTheme = theme("1", "liquid-default");
        Files.writeString(liquidTheme.resolve("shapes/promo-card.json"), SHAPE_JSON);
        Path fmTheme = theme("1", "default");
        Files.writeString(fmTheme.resolve("shapes/promo-card.json"), SHAPE_JSON);

        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        scanner.scan(templatesRoot.toFile(), builder);
        RegistrySnapshot snapshot = builder.build();

        assertNotNull(snapshot.resolveShape("1/liquid-default", "PromoCard"));
        assertNotNull(snapshot.resolveShape("1/default", "PromoCard"));
    }

    @Test
    void resolveAnyShapeFindsThemeNamespaceShapeForCards() throws IOException {
        // §21 Q12(f)：面板 Shape 可能仅以主题命名空间副本存在；
        // 与活动主题无关的解析须跨任一主题命名空间，否则目录/种子无法解析。
        Path theme = theme("1", "default");
        Files.writeString(theme.resolve("shapes/promo-card.json"), SHAPE_JSON);
        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        scanner.scan(templatesRoot.toFile(), builder);
        RegistrySnapshot snapshot = builder.build();

        // resolveAnyShape 在任一主题命名空间找到它
        assertNotNull(snapshot.resolveAnyShape("PromoCard"));
        assertNull(snapshot.resolveAnyShape("NoSuchShape"));
    }

    @Test
    void transientInstallDirsAreSkipped() throws IOException {
        // 安装/更新替换窗口内站点目录下的 <theme>.staging-…/<theme>.backup- 不应被当作主题扫描（§12.5）
        Path staging = theme("1", "default.staging-12345");
        Files.writeString(staging.resolve("shapes/promo-card.json"), SHAPE_JSON);
        Path backup = theme("1", "default.backup-67890");
        Files.writeString(backup.resolve("shapes/promo-card.json"), SHAPE_JSON);
        Path real = theme("1", "default");
        Files.writeString(real.resolve("shapes/promo-card.json"), SHAPE_JSON);

        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        scanner.scan(templatesRoot.toFile(), builder);
        RegistrySnapshot snapshot = builder.build();

        assertNotNull(snapshot.resolveShape("1/default", "PromoCard"));
        assertNull(snapshot.resolveShape("1/default.staging-12345", "PromoCard"));
        assertNull(snapshot.resolveShape("1/default.backup-67890", "PromoCard"));
        assertTrue(ThemeDirScanner.isTransientDir("default.staging-1"));
        assertTrue(ThemeDirScanner.isTransientDir("default.backup-1"));
        assertFalse(ThemeDirScanner.isTransientDir("default"));
    }

    @Test
    void themeManifestLayoutVariantsAndPageSettingsAreLoaded() throws IOException {
        // manifest 2.0（A1）：theme.manifest.json#layoutVariants 装入快照（含模板绑定与变体设置结构）；
        // 页面清单的 settings 块解析为页面作用域设置结构
        Path theme = theme("1", "liquid-default");
        Files.writeString(theme.resolve(ThemeDirScanner.THEME_MANIFEST_FILE), """
                { "name": "Demo Default",
                  "layoutVariants": { "channel": [
                    { "value": "default", "template": "channel" },
                    { "value": "channel_video", "label": { "zh-CN": "视频" }, "template": "channel_video",
                      "settings": { "groups": [ { "name": "video", "fields": {
                          "autoplay": { "type": "boolean", "default": false } } } ] } } ] } }
                """);
        Files.writeString(theme.resolve("pages/channel.manifest.json"), """
                { "name": "channel", "template": "channel",
                  "settings": { "groups": [ { "name": "listing", "fields": {
                      "columns": { "type": "enum", "values": ["1", "2"], "default": "2" } } } ] } }
                """);

        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        scanner.scan(templatesRoot.toFile(), builder);
        RegistrySnapshot snapshot = builder.build();
        String themeKey = "1/liquid-default";

        LayoutVariantCatalog catalog = snapshot.getThemeLayoutVariants(themeKey);
        assertEquals(2, catalog.forSurface("channel").size());
        LayoutVariantCatalog.Item video = catalog.forSurface("channel").get(1);
        assertEquals("channel_video", video.getValue());
        assertEquals("channel_video", video.getTemplate());
        assertFalse(video.getSettings().isEmpty());
        // 页面作用域设置结构（settings-only 亦合法，slots 自 2.0 起可省略）
        PageManifest channel = snapshot.getThemeManifest(themeKey, "channel");
        assertNotNull(channel);
        assertFalse(channel.getSettings().isEmpty());
        assertTrue(channel.getSlots().isEmpty());
    }

    @Test
    void malformedArtifactFailsWithSourcePath() throws IOException {
        Path theme = theme("1", "broken");
        Files.writeString(theme.resolve("pages/home.manifest.json"), "{ not json");

        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        CompositionLoadException e = assertThrows(CompositionLoadException.class,
                () -> scanner.scan(templatesRoot.toFile(), builder));
        assertTrue(e.getMessage().contains("home.manifest.json"));
    }

    @Test
    void themesWithoutCompositionDirsAreSkipped() throws IOException {
        Files.createDirectories(templatesRoot.resolve("1/plain-theme"));
        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        scanner.scan(templatesRoot.toFile(), builder);
        RegistrySnapshot snapshot = builder.build();
        assertEquals(0, snapshot.getShapeCount());
        assertEquals(0, snapshot.getManifestCount());
    }

    @Test
    void manifestsFollowDeclaredOrderNotFileName() throws IOException {
        Path theme = theme("1", "ordered");
        Files.writeString(theme.resolve("shapes/promo-card.json"), SHAPE_JSON);
        // 文件名序 article < channel < home < topic；声明序 home(10) < channel(20) < article(30)，topic 未声明
        Files.writeString(theme.resolve("pages/article.manifest.json"), manifest("article", 30));
        Files.writeString(theme.resolve("pages/channel.manifest.json"), manifest("channel", 20));
        Files.writeString(theme.resolve("pages/home.manifest.json"), manifest("home", 10));
        Files.writeString(theme.resolve("pages/topic.manifest.json"), manifest("topic", null));

        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        scanner.scan(templatesRoot.toFile(), builder);
        RegistrySnapshot snapshot = builder.build();

        String themeKey = "1/ordered";
        // 快照按 order 升序固定顺序，未声明者排在已声明者之后（PageManifest.DECLARED_ORDER）
        assertEquals(List.of("home", "channel", "article", "topic"),
                List.copyOf(snapshot.getThemeManifests().get(themeKey).keySet()));
        PageManifest article = snapshot.getThemeManifest(themeKey, "article");
        PageManifest topic = snapshot.getThemeManifest(themeKey, "topic");
        assertNotNull(article);
        assertNotNull(topic);
        assertEquals(30, article.getOrder());
        assertNull(topic.getOrder());
    }

    private static String manifest(String name, @Nullable Integer order) {
        return "{ \"name\": \"" + name + "\"" + (order == null ? "" : ", \"order\": " + order)
                + ", \"slots\": { \"body\": { \"kind\": \"region\", \"allowedShapes\": [\"PromoCard\"] } } }";
    }
}
