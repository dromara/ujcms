package com.ujcms.cms.core.security;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;
import org.owasp.html.CssSchema;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.examples.EbayPolicyExample;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HtmlSanitizeService} 单元测试：验证各策略层级的宽严行为，以及 RICH 是迁移前旧策略的超集。
 *
 * @author PONY
 */
class HtmlSanitizeServiceTest {
    private final HtmlSanitizeService service = new HtmlSanitizeService("");

    /** 迁移前 {@code SecurityConfig.policyFactory} 的定义，用于验证 RICH 不收紧任何旧的允许项。 */
    private static final PolicyFactory LEGACY = EbayPolicyExample.POLICY_DEFINITION.and(new HtmlPolicyBuilder()
            .allowElements("video").allowStandardUrlProtocols()
            .allowAttributes("controls", "preload", "width", "height", "src").onElements("video")
            .allowElements("audio").allowStandardUrlProtocols()
            .allowAttributes("controls", "preload", "width", "height", "src").onElements("audio")
            .allowElements("source").allowStandardUrlProtocols().allowAttributes("src", "type").onElements("source")
            .allowElements("a").allowAttributes("target").matching(true, "_blank").onElements("a")
            .allowElements("code").allowAttributes("data-backticks").onElements("code").allowElements("img")
            .allowAttributes("contenteditable").onElements("img")
            .allowStyling(CssSchema.withProperties(ImmutableSet.of("float")))
            .allowStyling(CssSchema.withProperties(ImmutableMap.of("display",
                    new CssSchema.Property(0, ImmutableSet.of("block"), ImmutableMap.of()))))
            .skipRelsOnLinks("nofollow").toFactory());

    private static final Pattern TAG = Pattern.compile("<(\\w+)");

    private static Set<String> tags(String html) {
        Matcher m = TAG.matcher(html);
        return m.results().map(r -> r.group(1).toLowerCase()).collect(Collectors.toSet());
    }

    @Test
    void richKeepsEverythingLegacyKept() {
        String[] corpus = {
                "<p>hello <b>world</b> <i>x</i></p>",
                "<table><thead><tr><th>h</th></tr></thead><tbody><tr><td>c</td></tr></tbody></table>",
                "<ul><li>a</li><li>b</li></ul><ol><li>c</li></ol>",
                "<a href=\"https://example.com\" target=\"_blank\">link</a>",
                "<img src=\"https://example.com/a.png\">",
                "<video controls width=\"320\"><source src=\"https://x/a.mp4\" type=\"video/mp4\"></video>",
                "<p style=\"float:left\">f</p>",
                "<p style=\"display:block\">d</p>",
                "<h1>t</h1><h2>s</h2><blockquote>q</blockquote><pre>code</pre>",
        };
        for (String html : corpus) {
            Set<String> legacyTags = tags(LEGACY.sanitize(html));
            Set<String> richTags = tags(service.sanitize(html, PolicyTier.RICH));
            assertTrue(richTags.containsAll(legacyTags),
                    "RICH 应保留旧策略保留的全部元素。输入: " + html
                            + " 旧策略保留: " + legacyTags + " RICH 保留: " + richTags);
        }
    }

    @Test
    void richAddsModernStylingAndClassId() {
        String out = service.sanitize(
                "<p class=\"lead\" id=\"intro\" style=\"color:red;text-align:center\">x</p>", PolicyTier.RICH);
        assertTrue(out.contains("class=\"lead\""), out);
        assertTrue(out.contains("id=\"intro\""), out);
        assertTrue(out.contains("color"), out);
        assertTrue(out.contains("text-align"), out);
    }

    @Test
    void richStripsScriptAndEventHandlers() {
        assertFalse(service.sanitize("<script>alert(1)</script><p>ok</p>", PolicyTier.RICH).contains("alert"));
        assertFalse(service.sanitize("<img src=x onerror=alert(1)>", PolicyTier.RICH).contains("onerror"));
        assertFalse(service.sanitize("<a href=\"javascript:alert(1)\">x</a>", PolicyTier.RICH).contains("javascript:"));
    }

    @Test
    void richKeepsBroadSemanticFormAndAriaDataSurface() {
        String out = service.sanitize(
                "<section><figure><figcaption>cap</figcaption></figure>"
                        + "<ruby>漢<rt>kan</rt></ruby>"
                        + "<form action=\"/submit\" method=\"post\">"
                        + "<label>Name<input type=\"text\" name=\"n\" required></label>"
                        + "<button type=\"submit\">Go</button></form>"
                        + "<p role=\"note\" aria-label=\"x\" data-id=\"7\">x</p></section>", PolicyTier.RICH);
        for (String token : new String[]{"<section", "<figure", "<figcaption", "<ruby", "<rt",
                "<form", "<label", "<input", "<button", "role=\"note\"", "aria-label", "data-id=\"7\""}) {
            assertTrue(out.contains(token), "RICH should keep " + token + " — got: " + out);
        }
    }

    @Test
    void richStripsDangerousVectorsOnTheBroadenedSurface() {
        // 表单/控件允许，但 URL 协议与事件处理必须被清除：
        assertFalse(service.sanitize("<form action=\"javascript:alert(1)\"><input></form>", PolicyTier.RICH)
                .contains("javascript:"));
        assertFalse(service.sanitize("<button formaction=\"javascript:alert(1)\">x</button>", PolicyTier.RICH)
                .contains("javascript:"));
        assertFalse(service.sanitize("<input onfocus=\"alert(1)\" autofocus>", PolicyTier.RICH).contains("onfocus"));
        assertFalse(service.sanitize("<p onclick=\"alert(1)\">x</p>", PolicyTier.RICH).contains("onclick"));
        // svg / math / object / embed / base 始终被剔除：
        assertFalse(service.sanitize("<svg onload=alert(1)></svg>", PolicyTier.RICH).contains("onload"));
        assertFalse(service.sanitize("<math><mtext></mtext></math>", PolicyTier.RICH).contains("<math"));
        assertFalse(service.sanitize("<object data=\"javascript:alert(1)\"></object>", PolicyTier.RICH)
                .contains("<object"));
        assertFalse(service.sanitize("<embed src=\"javascript:alert(1)\">", PolicyTier.RICH).contains("<embed"));
        assertFalse(service.sanitize("<base href=\"javascript:alert(1)\">", PolicyTier.RICH).contains("<base"));
    }

    @Test
    void basicAllowsFormattingButNotStyleOrTables() {
        String out = service.sanitize(
                "<p style=\"color:red\"><b>x</b></p><table><tr><td>c</td></tr></table>"
                        + "<a href=\"https://e.com\">l</a><img src=\"https://e.com/a.png\">", PolicyTier.BASIC);
        assertTrue(out.contains("<b>"), out);
        assertTrue(out.contains("<img"), out);
        assertTrue(out.contains("nofollow"), out);
        assertFalse(out.contains("color"), out);
        assertFalse(out.contains("<table"), out);
    }

    @Test
    void strictAllowsOnlyBasicFormattingAndNofollowLinks() {
        String out = service.sanitize(
                "<p style=\"color:red\"><b>x</b></p><img src=\"https://e.com/a.png\">"
                        + "<a href=\"https://e.com\">l</a>", PolicyTier.STRICT);
        assertTrue(out.contains("<b>"), out);
        assertTrue(out.contains("nofollow"), out);
        assertFalse(out.contains("<img"), out);
        assertFalse(out.contains("color"), out);
    }

    @Test
    void textStripsAllTags() {
        String out = service.sanitize("<b>hi</b> <a href=\"x\">there</a><script>alert(1)</script>", PolicyTier.TEXT);
        assertTrue(out.contains("hi"), out);
        assertTrue(out.contains("there"), out);
        assertFalse(out.contains("<"), out);
        assertFalse(out.contains("alert"), out);
    }

    @Test
    void iframeDeniedByDefaultButAllowedForWhitelistedHosts() {
        // 默认（白名单为空）禁止 iframe。
        assertFalse(service.sanitize("<iframe src=\"https://www.youtube.com/embed/x\"></iframe>", PolicyTier.RICH)
                .contains("iframe"));
        // 配置白名单后，仅允许白名单域名的 iframe，其它来源的 src 被剔除。
        HtmlSanitizeService withHosts = new HtmlSanitizeService("youtube.com, player.bilibili.com");
        String allowed = withHosts.sanitize("<iframe src=\"https://www.youtube.com/embed/x\"></iframe>", PolicyTier.RICH);
        assertTrue(allowed.contains("youtube.com"), allowed);
        String evil = withHosts.sanitize("<iframe src=\"https://evil.com/x\"></iframe>", PolicyTier.RICH);
        assertFalse(evil.contains("evil.com"), evil);
    }

    @Test
    void nullInputReturnsNull() {
        assertNull(service.sanitize(null, PolicyTier.RICH));
    }

    @Test
    void nonAsciiEntitiesDecodedStructuralOnesKept() {
        String out = service.sanitize("<p>请问？，！ ａｂｃ１ 😀 1 &lt; 2 &amp; 3 &gt; 2 \"q\" 'a'</p>", PolicyTier.RICH);
        // 全角标点 / 全角字母数字 / emoji 以字面字符存储，而不是 &#xff1f; 之类实体
        assertTrue(out.contains("请问？，！ ａｂｃ１ 😀"), out);
        assertFalse(out.contains("&#x"), out);
        // ASCII 结构字符仍保持实体
        assertTrue(out.contains("1 &lt; 2 &amp; 3 &gt; 2 &#34;q&#34; &#39;a&#39;"), out);
        // 再次清理稳定（种子往返 / 未改动行重存不改写）
        assertEquals(out, service.sanitize(out, PolicyTier.RICH));
    }

    @Test
    void fullWidthAngleBracketsStayLiteralAndInert() {
        String out = service.sanitize("＜script＞alert(1)＜/script＞ ＜b＞x＜/b＞", PolicyTier.STRICT);
        assertEquals("＜script＞alert(1)＜/script＞ ＜b＞x＜/b＞", out);
    }

    @Test
    void bomStaysEncodedAndJsLineTerminatorsAreNeverDecoded() {
        // OWASP 把 BOM 编码为实体，还原步骤须保留它（字面 BOM 是不可见的垃圾字符）
        String out = service.sanitize("c\uFEFFd", PolicyTier.TEXT);
        assertEquals("c&#xfeff;d", out);
        // OWASP 不会编码 U+2028/U+2029，但若实体形式出现（外部输入），还原步骤也不把它们变成字面字符
        assertEquals("&#x2028;&#x2029;&#8232;",
                HtmlSanitizeService.decodeNonAsciiEntities("&#x2028;&#x2029;&#8232;"));
    }

    @Test
    void decodeHelperLeavesAsciiAndMalformedAlone() {
        assertEquals("&lt;&gt;&amp;&#34;&#39; &#x26; &#60; &#xff1f &#; text",
                HtmlSanitizeService.decodeNonAsciiEntities("&lt;&gt;&amp;&#34;&#39; &#x26; &#60; &#xff1f &#; text"));
        assertEquals("？，！😀", HtmlSanitizeService.decodeNonAsciiEntities("&#xff1f;&#xff0c;&#65281;&#x1f600;"));
    }
}
