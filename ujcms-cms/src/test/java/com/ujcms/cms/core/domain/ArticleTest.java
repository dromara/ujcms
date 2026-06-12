package com.ujcms.cms.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
