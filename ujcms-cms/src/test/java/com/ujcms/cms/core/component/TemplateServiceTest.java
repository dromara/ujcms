package com.ujcms.cms.core.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResourceLoader;

/**
 * Unit tests for {@link TemplateService} under the structured theme layout.
 * <p>The admin template picker (used by the channel-edit screen to populate the
 * "channel template" / "article template" dropdowns) calls this service. All themes use the
 * structured layout: page templates live in {@code <theme>/templates/} (top level only —
 * {@code app/} fragments are not selectable page templates).
 */
class TemplateServiceTest {

    private TemplateService build() {
        return new TemplateService(new FileSystemResourceLoader());
    }

    /** Build the resource path used by the admin controller: {@code <prefix><themeKey>}. */
    private String themePath(Path templatesRoot, String themeKey) {
        return templatesRoot.toUri().toString() + themeKey;
    }

    @Test
    void listsHtmlFilesUnderTemplatesSubdir(@TempDir Path templates) throws IOException {
        Path theme = templates.resolve("1/default");
        Files.createDirectories(theme.resolve("templates"));
        Files.writeString(theme.resolve("templates/channel.html"), "");
        Files.writeString(theme.resolve("templates/channel_about.html"), "");
        Files.writeString(theme.resolve("templates/channel_video.html"), "");
        Files.writeString(theme.resolve("templates/article.html"), ""); // wrong prefix; filtered

        List<String> names = build().getTemplates(themePath(templates, "1/default"), "channel");

        // 按名称排序，不随文件系统的列目录顺序变化
        assertEquals(List.of("channel", "channel_about", "channel_video"), names);
    }

    @Test
    void ignoresThemeRootAndAppSubdir(@TempDir Path templates) throws IOException {
        Path theme = templates.resolve("1/default");
        Files.createDirectories(theme.resolve("templates/app"));
        Files.writeString(theme.resolve("templates/channel.html"), "");
        // 主题根与 app/ 片段目录都不是可选页面模板
        Files.writeString(theme.resolve("channel_legacy.html"), "");
        Files.writeString(theme.resolve("templates/app/channel_fragment.html"), "");

        List<String> names = build().getTemplates(themePath(templates, "1/default"), "channel");

        assertEquals(1, names.size());
        assertTrue(names.contains("channel"));
    }

    @Test
    void missingTemplatesSubdirReturnsEmpty(@TempDir Path templates) throws IOException {
        Path theme = templates.resolve("1/incomplete");
        Files.createDirectories(theme.resolve("snippets"));
        Files.writeString(theme.resolve("snippets/header.html"), "");

        List<String> names = build().getTemplates(themePath(templates, "1/incomplete"), "channel");

        assertTrue(names.isEmpty(), "no templates/ subdir → empty list");
    }

    @Test
    void missingThemeDirReturnsEmpty(@TempDir Path templates) throws IOException {
        List<String> names = build().getTemplates(themePath(templates, "1/nonexistent"), "channel");
        assertTrue(names.isEmpty());
    }
}
