package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujcms.cms.core.service.ConfigService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 主题 Shape 归属护栏（theme-owned-shapes D15，取代 {@code CoreShapeNamespaceTest}）。
 * 核心命名空间退役后 Shape 声明随渲染它的主题一起分发：声明（{@code shapes/*.json}）与渲染片段
 * （{@code snippets/panels/<Shape>.html}）同属一个主题，页面清单的 {@code allowedShapes} 只能引用本主题
 * 声明的 Shape——启动扫描期 {@link SnapshotValidator} 对此是硬失败（启动失败），本测试把它提前到构建期。
 * 对仓库内每个参考主题（{@code themes/1/<name>/}，带 {@code theme.manifest.json}）校验：
 * <ol>
 *   <li>每个 {@code shapes/*.json} 通过 {@link ShapeLoader} 全量校验；</li>
 *   <li>每个页面清单 {@code allowedShapes} 条目均由本主题声明；</li>
 *   <li>{@code snippets/panels/<Shape>.html} 与 Shape 声明一一对应（多余片段 = 无法配置的组件，
 *       缺失片段 = 渲染空占位）；</li>
 *   <li>Phase D 退役的核心卡片 Shape 不得复活；</li>
 *   <li>{@code presentation.thumbnail} 相对路径在主题目录下真实存在。</li>
 * </ol>
 * 发行版仓库不携带 webapp 模板时跳过。
 */
class ThemeShapeOwnershipTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> RETIRED_CARD_SHAPES = List.of("banner.json", "hero-card.json",
            "link-row.json", "ad-embed.json", "focus-slide.json");

    /** 主题键 → 主题目录 */
    private static final Map<String, Path> THEMES = new LinkedHashMap<>();
    /** 主题键 → 声明的 Shape（名 → Shape） */
    private static final Map<String, Map<String, Shape>> SHAPES = new LinkedHashMap<>();

    @BeforeAll
    static void loadThemes() throws IOException {
        Path root = locateMonorepoRoot();
        Assumptions.assumeTrue(root != null, "未找到参考主题目录（发行版仓库不携带），跳过");
        ShapeLoader loader = new ShapeLoader(new CompositionSchemas());
        Path site = root.resolve("ujcms-starter/src/main/webapp/themes/1");
        try (Stream<Path> dirs = Files.list(site)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                if (!Files.isRegularFile(dir.resolve(ThemeDirScanner.THEME_MANIFEST_FILE))) {
                    continue;
                }
                String themeKey = "1/" + dir.getFileName();
                Map<String, Shape> shapes = new LinkedHashMap<>();
                for (Path file : listFiles(dir.resolve(ThemeDirScanner.SHAPES_DIR), ThemeDirScanner.JSON_SUFFIX)) {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    Shape shape = assertDoesNotThrow(
                            () -> loader.load(json, Shape.SOURCE_THEME_PREFIX + themeKey, file.toString()),
                            file + " 未通过 ShapeLoader 校验");
                    assertTrue(shapes.putIfAbsent(shape.getName(), shape) == null,
                            themeKey + " 重复声明 Shape " + shape.getName());
                }
                THEMES.put(themeKey, dir);
                SHAPES.put(themeKey, shapes);
            }
        }
        assertFalse(THEMES.isEmpty(), "themes/1 下没有带 theme.manifest.json 的主题");
    }

    @Test
    void everyAllowedShapeIsDeclaredByTheTheme() throws IOException {
        for (Map.Entry<String, Path> entry : THEMES.entrySet()) {
            Set<String> declared = SHAPES.get(entry.getKey()).keySet();
            for (Path file : listFiles(entry.getValue().resolve(ThemeDirScanner.PAGES_DIR),
                    ThemeDirScanner.MANIFEST_SUFFIX)) {
                JsonNode manifest = MAPPER.readTree(file.toFile());
                manifest.path("slots").properties().forEach(slot ->
                        slot.getValue().path("allowedShapes").forEach(name ->
                                assertTrue(declared.contains(name.asText()), String.format(
                                        "%s 区域 %s 的 allowedShapes 引用了本主题未声明的 Shape %s（启动扫描将失败）",
                                        file, slot.getKey(), name.asText()))));
            }
        }
    }

    @Test
    void panelSnippetsAndShapeDeclarationsCorrespondOneToOne() throws IOException {
        for (Map.Entry<String, Path> entry : THEMES.entrySet()) {
            Set<String> declared = new TreeSet<>(SHAPES.get(entry.getKey()).keySet());
            Set<String> snippets = new TreeSet<>();
            for (Path file : listFiles(entry.getValue().resolve("snippets/panels"), ".html")) {
                String name = file.getFileName().toString();
                snippets.add(name.substring(0, name.length() - ".html".length()));
            }
            assertEquals(declared, snippets, entry.getKey()
                    + " 的 shapes/*.json 声明与 snippets/panels/*.html 片段不一一对应（D15：声明随渲染它的主题分发）");
        }
    }

    @Test
    void retiredCardShapesDoNotReappear() throws IOException {
        for (Map.Entry<String, Path> entry : THEMES.entrySet()) {
            for (Path file : listFiles(entry.getValue().resolve(ThemeDirScanner.SHAPES_DIR), ThemeDirScanner.JSON_SUFFIX)) {
                assertFalse(RETIRED_CARD_SHAPES.contains(file.getFileName().toString()),
                        file + " 是 Phase D 退役的核心卡片 Shape，不得复活");
            }
        }
    }

    @Test
    void relativeThumbnailsExistInTheme() {
        for (Map.Entry<String, Map<String, Shape>> entry : SHAPES.entrySet()) {
            Path themeDir = THEMES.get(entry.getKey());
            for (Shape shape : entry.getValue().values()) {
                Object thumbnail = shape.getPresentation().get("thumbnail");
                if (!(thumbnail instanceof String path) || path.isBlank() || path.startsWith("data:image/")) {
                    continue;
                }
                assertTrue(Files.isRegularFile(themeDir.resolve(path)),
                        entry.getKey() + " Shape " + shape.getName() + " 的缩略图 " + path + " 在主题目录下不存在");
            }
        }
    }

    private static List<Path> listFiles(Path dir, String suffix) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return new ArrayList<>(stream.filter(p -> p.getFileName().toString().endsWith(suffix)).sorted().toList());
        }
    }

    @org.jspecify.annotations.Nullable
    private static Path locateMonorepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 3 && dir != null; i++, dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("ujcms-starter/src/main/webapp/themes/1/default"))) {
                return dir;
            }
        }
        return null;
    }

    @Test
    void realThemeRegistryValidatesAndSharedRegionReferencesResolve() throws IOException {
        // 参考主题目录以真实扫描器装入注册表并过快照校验（启动扫描同款路径）：default 主题的 aside 在
        // 无模板的 site 宿主清单完整声明、各页面以 shared 引用（page-regions §15）——引用须解引用为宿主定义
        Path root = locateMonorepoRoot();
        Assumptions.assumeTrue(root != null);
        CompositionSchemas schemas = new CompositionSchemas();
        ThemeDirScanner scanner = new ThemeDirScanner(new ShapeLoader(schemas), new ManifestLoader(schemas),
                new ThemeManifestLoader(schemas), mock(ConfigService.class), new DefaultResourceLoader());
        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        scanner.scan(root.resolve("ujcms-starter/src/main/webapp/themes").toFile(), builder);
        RegistrySnapshot snapshot = builder.build();
        assertEquals(List.of(), new SnapshotValidator(FieldTypeMatrixTest.registry()).validate(snapshot));

        PageManifest site = snapshot.getThemeManifest("1/default", "site");
        assertTrue(site != null && site.getTemplates().isEmpty(), "default 主题须有无模板的 site 宿主清单");
        PageManifest.SlotDef owner = site.getSlots().get("aside");
        assertTrue(owner != null && owner.getSharedFrom() == null, "site.aside 须为完整声明");
        for (String page : List.of("home", "channel", "article", "topic", "system")) {
            PageManifest.SlotDef aside = snapshot.getThemeManifest("1/default", page).getSlots().get("aside");
            assertTrue(aside != null && aside.isShared(), page + ".aside 须为已解引用的共享引用");
            assertEquals("site", aside.getSharedFrom(), page);
            assertEquals(owner.getAllowedShapes(), aside.getAllowedShapes(), page);
            assertEquals(owner.getMaxRows(), aside.getMaxRows(), page);
        }
        assertEquals(7, owner.getAllowedShapes().size());
    }
}
