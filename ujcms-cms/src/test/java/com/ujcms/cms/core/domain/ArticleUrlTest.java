package com.ujcms.cms.core.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ArticleUrlTest {

    private Site site;

    @BeforeEach
    void setUp() {
        site = new Site();
        site.setProtocol("https");
        site.setDomain("example.com");
        // html disabled by default → uses getDynamicUrl
    }

    private Article createArticle(long id) {
        Article article = new Article();
        article.setSite(site);
        article.setId(id);
        // ext defaults to new ArticleExt(), alias is null
        return article;
    }

    @Test
    void getDynamicUrl_singleDomain_noContextPath_noDoubleSlash() {
        Article article = createArticle(123L);
        String url = article.getDynamicUrl();
        assertFalse(url.startsWith("//"), "URL must not start with //: " + url);
        assertEquals("/article/123", url);
    }

    @Test
    void getDynamicUrl_withAlias_appendsAlias() {
        Article article = createArticle(123L);
        article.getExt().setAlias("my-article");
        assertEquals("/article/123/my-article", article.getDynamicUrl());
    }

    @Test
    void getDynamicUrl_page2_appendsPageNumber() {
        Article article = createArticle(123L);
        assertEquals("/article/123/2", article.getDynamicUrl(2));
    }

    @Test
    void getDynamicUrl_withAlias_page2() {
        Article article = createArticle(123L);
        article.getExt().setAlias("my-article");
        assertEquals("/article/123/my-article/2", article.getDynamicUrl(2));
    }

    @Test
    void getDynamicUrl_withContextPath_includesContextPath() {
        site.getConfig().setContextPath("/ctx");
        Article article = createArticle(123L);
        assertEquals("/ctx/article/123", article.getDynamicUrl());
    }

    @Test
    void getDynamicUrl_customArticleUrl() {
        site.getConfig().setArticleUrl("/a");
        Article article = createArticle(123L);
        assertEquals("/a/123", article.getDynamicUrl());
    }

    @Test
    void getDynamicUrl_multiDomain_includesProtocolAndDomain() {
        site.getConfig().setMultiDomain(true);
        Article article = createArticle(123L);
        assertEquals("https://example.com/article/123", article.getDynamicUrl());
    }
}
