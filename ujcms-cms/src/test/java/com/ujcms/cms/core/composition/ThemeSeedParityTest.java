package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 种子树一致性测试（跨轨道对等契约 §8 / Phase A5）：SSR 参考纯数据包源
 * {@code ujcms-theme-e2e/demo-data/} 的 {@code seed/content/} 是参考主题
 * （default）同一棵树的拷贝；其 ssr 轨道构成种子（home 区域）与 default 主题的拷贝
 * 逐字节一致；清单声明 {@code kind: data-only}。
 * 单一事实来源是参考主题的拷贝：改动先落参考主题，再同步 demo-data。
 * 发行版仓库不携带 webapp 模板或 SSR 仓库时跳过。
 */
class ThemeSeedParityTest {
    private static Path defaultSeed;
    private static Path demoData;

    @BeforeAll
    static void locateRoots() {
        // 从模块目录（surefire 工作目录）向上查找项目根
        Path dir = Paths.get("").toAbsolutePath();
        Path root = null;
        for (int i = 0; i < 3 && dir != null; i++, dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("ujcms-starter/src/main/webapp/themes/1/default/seed"))) {
                root = dir;
                break;
            }
        }
        Assumptions.assumeTrue(root != null, "未找到参考主题种子树（发行版仓库不携带），跳过校验");
        defaultSeed = root.resolve("ujcms-starter/src/main/webapp/themes/1/default/seed");
        demoData = root.resolve("ujcms-theme-e2e/demo-data");
        Assumptions.assumeTrue(Files.isDirectory(demoData), "未找到 ujcms-theme-e2e/demo-data，跳过校验");
    }

    private static List<Path> relativeFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .sorted()
                    .toList();
        }
    }

    private static void assertTreesByteIdentical(Path expectedRoot, Path actualRoot) throws IOException {
        List<Path> expected = relativeFiles(expectedRoot);
        assertEquals(expected, relativeFiles(actualRoot),
                expectedRoot + " 与 " + actualRoot + " 的文件清单不一致，请同步两棵树");
        for (Path rel : expected) {
            assertArrayEquals(Files.readAllBytes(expectedRoot.resolve(rel)),
                    Files.readAllBytes(actualRoot.resolve(rel)),
                    rel + " 在 " + expectedRoot + " 与 " + actualRoot + " 之间不一致");
        }
    }

    @Test
    void demoDataContentTreeMatchesReferenceThemes() throws IOException {
        assertTreesByteIdentical(defaultSeed.resolve("content"), demoData.resolve("seed/content"));
    }

    @Test
    void demoDataSsrCompositionSeedsMatchDefaultTheme() throws IOException {
        // 纯数据包携带与 default 主题逐字节一致的区域构成种子（v2 面板设置已内联富文本内容，无策展卡片
        // 种子——component-settings Phase C）：home（main）与 site（全站共享的 aside，page-regions §15）。
        // 纯数据包无页面清单，导入时这些区域按「未声明」跳过——文件的意义是参考包与主题同源
        for (String rel : List.of("regions/home.regions.json", "regions/site.regions.json")) {
            assertArrayEquals(Files.readAllBytes(defaultSeed.resolve(rel)),
                    Files.readAllBytes(demoData.resolve("seed").resolve(rel)),
                    rel + " 在 default 主题与 demo-data 之间不一致");
        }
    }

    @Test
    void demoDataSeedTreeHasNoUnsyncedExtras() throws IOException {
        // demo-data/seed = content/** + 区域构成种子，不多不少——多出的文件没有对等校验，禁止潜入
        List<Path> extras = relativeFiles(demoData.resolve("seed")).stream()
                .filter(rel -> !rel.startsWith(Paths.get("content")))
                .filter(rel -> !rel.equals(Paths.get("regions/home.regions.json")))
                .filter(rel -> !rel.equals(Paths.get("regions/site.regions.json")))
                .toList();
        assertEquals(List.of(), extras, "demo-data/seed 存在未纳入对等校验的文件");
    }

    @Test
    void demoDataManifestDeclaresDataOnlyKind() throws IOException {
        Path manifest = demoData.resolve("theme.manifest.json");
        assertTrue(Files.isRegularFile(manifest), "demo-data 缺少 theme.manifest.json");
        assertEquals("data-only",
                new ObjectMapper().readTree(manifest.toFile()).path("kind").asText(null),
                "demo-data 清单必须声明 kind: data-only");
    }
}
