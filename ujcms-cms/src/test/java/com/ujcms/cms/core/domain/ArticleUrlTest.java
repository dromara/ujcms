package com.ujcms.cms.core.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleUrlTest {

    private Site site;

    @BeforeEach
    void setUp() {
        site = new Site();
        site.setProtocol("https");
        site.setDomain("example.com");
        // html disabled by default → uses getDynamicUrl；permalink 缺省为混合型 id-slug
    }

    private Article createArticle(long id) {
        Article article = new Article();
        article.setSite(site);
        article.setId(id);
        // slug 缺省为空串（未设别名）
        return article;
    }

    private void permalinkMode(String mode) {
        Site.Permalink permalink = new Site.Permalink();
        permalink.setArticle(mode);
        site.setPermalink(permalink);
    }

    @Test
    void getDynamicUrl_singleDomain_noContextPath_noDoubleSlash() {
        Article article = createArticle(123L);
        String url = article.getDynamicUrl();
        assertFalse(url.startsWith("//"), "URL must not start with //: " + url);
        assertEquals("/article/123", url);
    }

    @Test
    void getDynamicUrl_withSlug_defaultModeIsIdDashSlug() {
        Article article = createArticle(123L);
        article.setSlug("my-article");
        assertEquals("/article/123-my-article", article.getDynamicUrl());
    }

    @Test
    void getDynamicUrl_page2_appendsPageNumber() {
        Article article = createArticle(123L);
        assertEquals("/article/123/2", article.getDynamicUrl(2));
    }

    @Test
    void getDynamicUrl_withSlug_page2() {
        Article article = createArticle(123L);
        article.setSlug("my-article");
        assertEquals("/article/123-my-article/2", article.getDynamicUrl(2));
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

    // region 固定链接形态（docs/spec/permalinks/plan.md D6/D7）

    @Test
    void idSlugMode_idSentinelSlugDegradesToId() {
        // 别名等于 ID 字符串 = “未设别名”哨兵（服务端生成回退）
        Article article = createArticle(123L);
        article.setSlug("123");
        assertEquals("/article/123", article.getDynamicUrl());
        assertEquals("/article/123/2", article.getDynamicUrl(2));
        assertEquals("123", article.urlKey());
    }

    @Test
    void slugMode_customSlugOnly() {
        permalinkMode(Site.Permalink.ARTICLE_SLUG);
        Article article = createArticle(123L);
        article.setSlug("my-article");
        assertEquals("/article/my-article", article.getDynamicUrl());
        assertEquals("/article/my-article/2", article.getDynamicUrl(2));
        assertEquals("my-article", article.urlKey());
    }

    @Test
    void slugMode_withoutCustomSlugFallsBackToId() {
        permalinkMode(Site.Permalink.ARTICLE_SLUG);
        Article blank = createArticle(123L);
        assertEquals("/article/123", blank.getDynamicUrl());
        assertEquals("123", blank.urlKey());
        Article sentinel = createArticle(123L);
        sentinel.setSlug("123");
        assertEquals("/article/123", sentinel.getDynamicUrl());
        assertEquals("/article/123/2", sentinel.getDynamicUrl(2));
    }

    @Test
    void idMode_ignoresSlug() {
        permalinkMode(Site.Permalink.ARTICLE_ID);
        Article article = createArticle(123L);
        article.setSlug("my-article");
        assertEquals("/article/123", article.getDynamicUrl());
        assertEquals("/article/123/2", article.getDynamicUrl(2));
        assertEquals("123", article.urlKey());
        Article sentinel = createArticle(123L);
        sentinel.setSlug("123");
        assertEquals("/article/123", sentinel.getDynamicUrl());
    }

    @Test
    void urlKey_followsPermalinkMode() {
        Article article = createArticle(123L);
        article.setSlug("my-article");
        assertEquals("123-my-article", article.urlKey());
        permalinkMode(Site.Permalink.ARTICLE_SLUG);
        assertEquals("my-article", article.urlKey());
        permalinkMode(Site.Permalink.ARTICLE_ID);
        assertEquals("123", article.urlKey());
        permalinkMode(Site.Permalink.ARTICLE_ID_SLUG);
        assertEquals("123-my-article", article.urlKey());
    }

    @Test
    void hasCustomSlug_falseForBlankAndIdSentinel() {
        Article blank = createArticle(123L);
        assertFalse(blank.hasCustomSlug());
        Article sentinel = createArticle(123L);
        sentinel.setSlug("123");
        assertFalse(sentinel.hasCustomSlug());
        Article custom = createArticle(123L);
        custom.setSlug("my-article");
        assertTrue(custom.hasCustomSlug());
        // 纯数字别名但不等于自身 ID：仍是自定义别名
        Article numeric = createArticle(123L);
        numeric.setSlug("2024");
        assertTrue(numeric.hasCustomSlug());
        assertEquals("/article/123-2024", numeric.getDynamicUrl());
    }

    @Test
    void dynamicFullUrl_sharesPathWithDynamicUrl() {
        Article article = createArticle(123L);
        article.setSlug("my-article");
        assertEquals("https://example.com/article/123-my-article", article.getDynamicFullUrl());
        assertEquals("https://example.com/article/123-my-article/2", article.getDynamicFullUrl(2));
        assertEquals("https://example.com" + article.getDynamicUrl(2), article.getDynamicFullUrl(2));

        permalinkMode(Site.Permalink.ARTICLE_SLUG);
        assertEquals("https://example.com" + article.getDynamicUrl(), article.getDynamicFullUrl());
        assertEquals("https://example.com/article/my-article", article.getDynamicFullUrl());

        permalinkMode(Site.Permalink.ARTICLE_ID);
        assertEquals("https://example.com" + article.getDynamicUrl(3), article.getDynamicFullUrl(3));
        assertEquals("https://example.com/article/123/3", article.getDynamicFullUrl(3));
    }

    @Test
    void dynamicFullUrl_withContextPathAndSubDir() {
        site.getConfig().setContextPath("/ctx");
        site.setSubDir("en");
        Article article = createArticle(123L);
        article.setSlug("my-article");
        assertEquals("/ctx/en/article/123-my-article", article.getDynamicUrl());
        assertEquals("https://example.com/ctx/en/article/123-my-article", article.getDynamicFullUrl());
    }

    // endregion
}
