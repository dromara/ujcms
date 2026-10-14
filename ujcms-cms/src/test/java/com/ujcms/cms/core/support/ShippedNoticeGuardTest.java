package com.ujcms.cms.core.support;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 发行版随附文本守卫校验。
 * <p>
 * 发行版只携带 {@code docs/guide/}，{@code docs/spec/}、{@code docs/decisions/}、{@code docs/archive/} 与
 * {@code docs/tender/} 不随任何版本发布。客户的采购、法务与运维人员会直接阅读的随附文本——NOTICE、LICENSE、
 * {@code licenses/}、license-maven-plugin 的输入表、主题包内的许可证、第三方声明与素材来源（主题市场的包也是发行物）、应用配置、Docker 契约——
 * 因此不得引用这些目录：引用在客户手中是断链。主题包内的模板、片段、布局、页面与形状定义同样受检：主题包经主题市场独立发行，
 * 运营人员在控制台模板编辑器里直接阅读其中的注释。Java 注释与 CLAUDE.md 可以引用设计文档（面向本仓库的开发者），
 * 不在本测试范围。发行版仓库不携带被检查的文件时跳过。
 *
 * @author PONY
 */
class ShippedNoticeGuardTest {
    private static final Pattern UNSHIPPED_DOCS = Pattern.compile("docs/(spec|decisions|archive|tender)/");

    /** 根目录下的文件或目录（目录递归） */
    private static final List<String> SHIPPED_PROSE = List.of(
            "NOTICE",
            "LICENSE",
            "LICENSE-COMMERCIAL.md",
            "licenses",
            "Dockerfile",
            "docker",
            "ujcms-starter/src/license",
            "ujcms-starter/src/main/resources",
            "ujcms-starter/src/main/webapp/themes/1/default/THIRD-PARTY-NOTICES.md",
            "ujcms-starter/src/main/webapp/themes/1/default/SEED-MEDIA.md",
            "ujcms-starter/src/main/webapp/themes/1/journal/THIRD-PARTY-NOTICES.md",
            "ujcms-starter/src/main/webapp/themes/1/journal/SEED-MEDIA.md",
            "ujcms-starter/src/main/webapp/themes/1/default/LICENSE",
            "ujcms-starter/src/main/webapp/themes/1/journal/LICENSE",
            "ujcms-starter/src/main/webapp/themes/1/mobile/LICENSE",
            "ujcms-starter/src/main/webapp/themes/1/shuili/LICENSE.md",
            "ujcms-starter/src/main/webapp/themes/1/shuili/SEED-MEDIA.md",
            "ujcms-theme-e2e/demo-data/LICENSE",
            "ujcms-theme-e2e/demo-data/SEED-MEDIA.md");

    /** 主题包根目录；其下 {@link #THEME_TEXT_DIRS} 与清单文件受检（assets、node_modules 等不受检） */
    private static final List<String> THEME_ROOTS = List.of(
            "ujcms-starter/src/main/webapp/themes/1/default",
            "ujcms-starter/src/main/webapp/themes/1/journal",
            "ujcms-starter/src/main/webapp/themes/1/mobile",
            "ujcms-starter/src/main/webapp/themes/1/shuili",
            "ujcms-theme-e2e/demo-data");

    /** 主题包内运营人员直接阅读的文本目录 */
    private static final List<String> THEME_TEXT_DIRS = List.of("layout", "templates", "snippets", "pages", "shapes");

    @Test
    void shippedProseNeverCitesUnshippedDocs() throws IOException {
        Path root = locateMonorepoRoot();
        Assumptions.assumeTrue(root != null, "非单体仓库，跳过");
        List<String> offending = new ArrayList<>();
        List<String> entries = new ArrayList<>(SHIPPED_PROSE);
        for (String theme : THEME_ROOTS) {
            entries.add(theme + "/theme.manifest.json");
            for (String dir : THEME_TEXT_DIRS) {
                entries.add(theme + "/" + dir);
            }
        }
        for (String entry : entries) {
            Path path = root.resolve(entry);
            if (!Files.exists(path)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(path)) {
                for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                    if (!isText(file)) {
                        continue;
                    }
                    String[] lines = Files.readString(file, StandardCharsets.UTF_8).split("\r?\n");
                    for (int i = 0; i < lines.length; i++) {
                        if (UNSHIPPED_DOCS.matcher(lines[i]).find()) {
                            offending.add(root.relativize(file) + ":" + (i + 1) + ": " + lines[i].strip());
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), offending,
                "随发行版交付的文本引用了不随发行版发布的 docs/ 目录（客户手中是断链），改为自足的说明");
    }

    private static boolean isText(Path file) {
        String name = file.getFileName().toString();
        return !(name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".woff") || name.endsWith(".woff2")
                || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".gif") || name.endsWith(".ico")
                || name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".pdf"));
    }

    @org.jspecify.annotations.Nullable
    private static Path locateMonorepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 3 && dir != null; i++, dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("NOTICE")) && Files.isDirectory(dir.resolve("ujcms-starter"))) {
                return dir;
            }
        }
        return null;
    }
}
