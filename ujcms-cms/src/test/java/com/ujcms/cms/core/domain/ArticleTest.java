package com.ujcms.cms.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleTest {
    @Test
    void getPlainText_decodesHtmlEntities() {
        Article article = new Article();
        article.setText("<p>&ldquo;Hello&rdquo; &amp; &lt;world&gt;</p>");
        assertEquals("\u201cHello\u201d & <world>", article.getPlainText());
    }

    @Test
    void getPlainText_collapsesWhitespace() {
        Article article = new Article();
        article.setText("<p>  foo   bar\u3000baz  </p>");
        assertEquals("foo bar baz", article.getPlainText());
    }

    @Test
    void getPlainText_stripsHtmlTags() {
        Article article = new Article();
        article.setText("<p>Hello <strong>world</strong></p>");
        assertEquals("Hello world", article.getPlainText());
    }

    @Test
    void getPlainText_returnsEmptyForNull() {
        Article article = new Article();
        article.setText(null);
        assertEquals("", article.getPlainText());
    }

    private static Article content(String title, String text) {
        Article article = new Article();
        article.setTitle(title);
        article.setSeoKeywords("a,b");
        article.setText(text);
        article.setMainsJson("{\"price\":42800}");
        return article;
    }

    /**
     * 内容一致判定（翻译过期锚点）：全部内容字段相同为一致；null 与空串等价；任一内容字段不同即不一致
     */
    @Test
    void isSameContent_comparesContentFieldsNullAsEmpty() {
        Article stored = content("标题", "<p>你好</p>");
        Article saved = content("标题", "<p>你好</p>");
        assertTrue(saved.isSameContent(stored));
        stored.setSubtitle(null);
        saved.setSubtitle("");
        assertTrue(saved.isSameContent(stored));
        saved.setText("<p>你好！</p>");
        assertFalse(saved.isSameContent(stored));
        assertFalse(content("标题", "<p>你好</p>").isSameContent(content("新标题", "<p>你好</p>")));
        Article customs = content("标题", "<p>你好</p>");
        customs.setMainsJson("{\"price\":42900}");
        assertFalse(customs.isSameContent(stored));
    }

    @Test
    void rejectReasonIsOnlyGivenWhileTheArticleIsRejected() {
        // 退回原因是审核人留给作者的内部意见：文章离开“已退回”之后不再随它输出（公共接口曾把它带了出去）
        Article article = new Article();
        article.getExt().setRejectReason("fix the title");
        article.setStatus(Article.STATUS_REJECTED);
        assertEquals("fix the title", article.getRejectReason());
        for (short status : new short[]{Article.STATUS_PUBLISHED, Article.STATUS_ARCHIVED, Article.STATUS_PENDING,
                Article.STATUS_DRAFT, Article.STATUS_OFFLINE, Article.STATUS_DELETED}) {
            article.setStatus(status);
            assertNull(article.getRejectReason(), "status " + status);
        }
    }

    @Test
    void staticUrlsBeyondPageOneFallBackToTheDynamicUrl() {
        // 文章静态页只生成第 1 页；其后的页码是模板分页上下文，走动态地址（栏目页同此做法）
        Site site = new Site();
        site.setProtocol("https");
        site.setDomain("example.com");
        Article article = new Article();
        article.setId(123L);
        article.setSite(site);
        assertEquals(article.getDynamicUrl(2), article.getStaticUrl(2));
        assertEquals(article.getDynamicFullUrl(3), article.getStaticFullUrl(3));
    }

    @Test
    void plainTextIsParsedOnceAndAgainOnlyWhenTheBodyChanges() {
        // 摘要、模板里的截取都要纯文本：同一个正文只解析一次（docs/spec/article-management/plan.md Phase 6）
        Article article = new Article();
        article.setText("<p>Hello <b>world</b></p>");

        String first = article.getPlainText();

        assertEquals("Hello world", first.trim());
        // 同一个对象：没有重新解析
        assertSame(first, article.getPlainText());
        // 没有 SEO 描述时，摘要取自纯文本
        assertEquals(first.trim(), article.getDescription().trim());

        article.setText("<p>Changed</p>");

        assertEquals("Changed", article.getPlainText().trim());
    }
}
