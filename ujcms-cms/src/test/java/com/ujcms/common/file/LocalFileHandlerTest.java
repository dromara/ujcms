package com.ujcms.common.file;

import com.ujcms.common.web.PathResolver;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 静态页生成（docs/spec/template-error-disclosure D4）：模板先整页渲染进内存再落盘，渲染失败不留下半截文件。
 */
class LocalFileHandlerTest {
    @TempDir
    Path dir;

    @Test
    void failedRenderingLeavesNoFile() throws Exception {
        LocalFileHandler handler = handler();

        assertThrows(IllegalStateException.class,
                () -> handler.store("/site/index.html", template("<html>${missing}</html>"), Map.of()));

        assertFalse(new File(dir.toFile(), "site/index.html").exists(), "no truncated static page");
    }

    @Test
    void successfulRenderingWritesTheWholePage() throws Exception {
        LocalFileHandler handler = handler();

        handler.store("/site/index.html", template("<html>${title}</html>"), Map.of("title", "Home"));

        assertEquals("<html>Home</html>", Files.readString(dir.resolve("site/index.html"), StandardCharsets.UTF_8));
    }

    private LocalFileHandler handler() {
        // file: 前缀走绝对路径，不触及 ServletContext
        return new LocalFileHandler(new PathResolver(null), PathResolver.FILE_PREFIX + dir, "/uploads");
    }

    private static Template template(String source) throws Exception {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        StringTemplateLoader loader = new StringTemplateLoader();
        loader.putTemplate("index.html", source);
        configuration.setTemplateLoader(loader);
        return configuration.getTemplate("index.html");
    }
}
