package com.ujcms.common.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 正文附件 URL 提取：附件引用登记（AttachmentRefer）依赖此方法，遗漏即导致文件被判为“未使用”而可被清理。
 *
 * @author PONY
 */
class HtmlParserUtilsTest {
    @Test
    void getUrlsCoversImageSourceAnchorVideoAudio() {
        String html = "<p><img src=\"/uploads/1/image/a.jpg\" alt=\"a\"></p>"
                + "<p><video controls=\"controls\" poster=\"/uploads/1/image/poster.jpg\">"
                + "<source src=\"/uploads/1/video/v.mp4\" type=\"video/mp4\"></video></p>"
                + "<p><video src=\"/uploads/1/video/direct.mp4\"></video></p>"
                + "<p><audio controls=\"controls\"><source src=\"/uploads/1/audio/s.mp3\"></audio></p>"
                + "<p><audio src=\"/uploads/1/audio/direct.mp3\"></audio></p>"
                + "<p><a href=\"/uploads/1/file/doc.pdf\">doc</a></p>";
        List<String> urls = HtmlParserUtils.getUrls(html);
        assertTrue(urls.contains("/uploads/1/image/a.jpg"));
        assertTrue(urls.contains("/uploads/1/image/poster.jpg"), "video poster must be pinned");
        assertTrue(urls.contains("/uploads/1/video/v.mp4"));
        assertTrue(urls.contains("/uploads/1/video/direct.mp4"), "video[src] without <source> must be pinned");
        assertTrue(urls.contains("/uploads/1/audio/s.mp3"));
        assertTrue(urls.contains("/uploads/1/audio/direct.mp3"), "audio[src] without <source> must be pinned");
        assertTrue(urls.contains("/uploads/1/file/doc.pdf"));
        assertEquals(7, urls.size());
    }

    @Test
    void getUrlsIgnoresBlankAndEmptyAttributes() {
        assertTrue(HtmlParserUtils.getUrls(null).isEmpty());
        assertTrue(HtmlParserUtils.getUrls("  ").isEmpty());
        assertTrue(HtmlParserUtils.getUrls("<img src=\"\"><video></video><a>x</a>").isEmpty());
    }
}
