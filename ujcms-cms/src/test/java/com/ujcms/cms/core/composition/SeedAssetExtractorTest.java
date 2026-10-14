package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.common.web.PathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 反向资源遍历器测试（theme-seed-export §5.2/§5.3/§5.4）：URL 四分类（上传/主题/外链/他站点）、
 * 往返稳定的 relpath（theme-seed 路径保留原样）、内容散列铸造与同字节去重、缺文件保留原 URL、
 * 富文本内嵌改写与外链计数。
 */
class SeedAssetExtractorTest {
    private static final Long SITE = 1L;
    private static final String THEME = "demo";

    @TempDir
    Path uploads;

    private AttachmentService attachmentService;
    private SeedAssetExtractor.Session session;

    @BeforeEach
    void setUp() {
        ConfigService configService = mock(ConfigService.class);
        Config config = new Config();
        config.setUploadStorage(new Config.Storage(0, "file:" + uploads, "/uploads"));
        config.setTemplateStorage(new Config.Storage(0, "file:" + uploads + "/templates", "/themes"));
        when(configService.getUnique()).thenReturn(config);
        attachmentService = mock(AttachmentService.class);
        session = new SeedAssetExtractor(configService, new PathResolver(null), attachmentService)
                .open(SITE, THEME);
    }

    private void storeFile(String pathname, String content) throws IOException {
        Path file = uploads.resolve(pathname);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void priorImportPathKeepsRelpath() throws IOException {
        // 先前导入的确定性路径（theme-seed/<siteId>/<theme>/…）→ 保留原 relpath（往返稳定，§5.3）
        storeFile("theme-seed/1/demo/img/a.svg", "<svg/>");
        String token = session.rewriteUrl("/uploads/theme-seed/1/demo/img/a.svg", "article:a-1.image");
        assertEquals("{{asset:img/a.svg}}", token);
        assertTrue(session.assets().containsKey("img/a.svg"));
    }

    @Test
    void operatorUploadMintsContentHashedRelpathAndDedupes() throws IOException {
        storeFile("2026/07/photo.jpg", "JPEGBYTES");
        storeFile("2026/07/copy.txt", "JPEGBYTES");
        String token = session.rewriteUrl("/uploads/2026/07/photo.jpg", "x");
        assertTrue(token.matches("\\{\\{asset:img/[0-9a-f]{8}-photo\\.jpg}}"), token);
        // 同 URL 二次改写走缓存（会话内恒同结果）
        assertEquals(token, session.rewriteUrl("/uploads/2026/07/photo.jpg", "y"));
        // 非图片扩展名归 media/；不同文件名不共 relpath（散列相同也各自成文件）
        String other = session.rewriteUrl("/uploads/2026/07/copy.txt", "z");
        assertTrue(other.matches("\\{\\{asset:media/[0-9a-f]{8}-copy\\.txt}}"), other);
        assertEquals(2, session.assets().size());
    }

    @Test
    void themeDirUrlBecomesThemeTokenWithoutByteCopy() {
        String token = session.rewriteUrl("/themes/1/demo/assets/logo.svg", "x");
        assertEquals("{{theme}}/assets/logo.svg", token);
        // 同名前缀主题不误伤（demo2 非 demo）
        assertEquals("/themes/1/demo2/assets/logo.svg",
                session.rewriteUrl("/themes/1/demo2/assets/logo.svg", "x"));
        assertTrue(session.assets().isEmpty());
    }

    @Test
    void externalUrlKeptVerbatimAndCounted() {
        String url = "https://cdn.example.com/pic.png";
        assertEquals(url, session.rewriteUrl(url, "x"));
        assertEquals(List.of(url), session.externalUrls());
    }

    @Test
    void missingFileKeepsUrlWithIssue() {
        String url = "/uploads/2026/07/gone.jpg";
        assertEquals(url, session.rewriteUrl(url, "x"));
        assertTrue(session.assets().isEmpty());
        assertFalse(session.issues().isEmpty());
    }

    @Test
    void otherSiteAttachmentKeptVerbatimWithIssue() throws IOException {
        storeFile("2026/07/foreign.jpg", "X");
        Attachment foreign = new Attachment(9L, 1L, "foreign.jpg", "/2026/07/foreign.jpg",
                "/uploads/2026/07/foreign.jpg", 1L);
        when(attachmentService.findByUrl("/uploads/2026/07/foreign.jpg")).thenReturn(foreign);
        assertEquals("/uploads/2026/07/foreign.jpg",
                session.rewriteUrl("/uploads/2026/07/foreign.jpg", "x"));
        assertFalse(session.issues().isEmpty());
        assertTrue(session.assets().isEmpty());
    }

    @Test
    void siteRelativePageLinksUntouched() {
        assertEquals("/news/123", session.rewriteUrl("/news/123", "x"));
        assertTrue(session.externalUrls().isEmpty());
    }

    @Test
    void inlineRewriteHandlesHtmlBodies() throws IOException {
        storeFile("theme-seed/1/demo/img/inline.svg", "<svg/>");
        String html = "<p><img src=\"/uploads/theme-seed/1/demo/img/inline.svg\">"
                + "<a href=\"https://example.com/doc\">链接</a>"
                + "<img src=\"/themes/1/demo/assets/deco.png\"></p>";
        String rewritten = session.rewriteInline(html, "article:a-1.text");
        assertTrue(rewritten.contains("src=\"{{asset:img/inline.svg}}\""), rewritten);
        assertTrue(rewritten.contains("src=\"{{theme}}/assets/deco.png\""), rewritten);
        // 外链原样保留并计数（src/href 口径）
        assertTrue(rewritten.contains("https://example.com/doc"));
        assertEquals(List.of("https://example.com/doc"), session.externalUrls());
        // 无前缀命中的文本原样返回
        assertEquals("<p>plain</p>", session.rewriteInline("<p>plain</p>", "x"));
    }

    @Test
    void largestAssetsReportsBytesDescending() throws IOException {
        storeFile("theme-seed/1/demo/img/big.svg", "x".repeat(500));
        storeFile("theme-seed/1/demo/img/small.svg", "x");
        session.rewriteUrl("/uploads/theme-seed/1/demo/img/small.svg", "x");
        session.rewriteUrl("/uploads/theme-seed/1/demo/img/big.svg", "x");
        List<ExportPlan.AssetFile> largest = session.largestAssets(10);
        assertEquals("img/big.svg", largest.get(0).relpath());
        assertEquals(500, largest.get(0).bytes());
        assertEquals(501, session.assetBytes());
    }
}
