package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.component.ArticleAccessGuard;
import com.ujcms.cms.core.component.ArticleDataPermission;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ArticleBufferService;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.LayoutVariantService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.web.PathResolver;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.mock.web.MockHttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 前台 {@link ArticleController} 路径段解析与规范形 301（固定链接 D7，docs/spec/permalinks/plan.md）。
 * 纯 Mockito 单元测试：匿名访客（{@link Contexts} 线程变量为空）+ 匿名组默认全部访问权限，
 * 因此 {@code ArticleAccessGuard} 直接放行；各形态只看 key → 解析 → 规范形判定。
 *
 * @author PONY
 */
class ArticleControllerRedirectTest {
    private static final long SITE_ID = 1L;
    private static final long ARTICLE_ID = 123L;
    private static final long CHANNEL_ID = 10L;
    private static final String TEMPLATE = "article";
    private static final String RENDERED = "/1/default/templates/" + TEMPLATE;

    private ArticleService articleService;
    private ChannelService channelService;
    private final MockHttpServletResponse servletResponse = new MockHttpServletResponse();
    private Site site;
    private Article article;
    private ArticleController controller;

    @BeforeEach
    void setUp() {
        Contexts.clearCurrentUser();
        articleService = mock(ArticleService.class);
        channelService = mock(ChannelService.class);
        GroupService groupService = mock(GroupService.class);
        LayoutVariantService layoutVariantService = mock(LayoutVariantService.class);
        // 访问判定用真实的 ArticleAccessGuard：匿名组全部访问权限、文章已发布 → 放行
        ArticleAccessGuard accessGuard = new ArticleAccessGuard(groupService, mock(OrgService.class), articleService,
                mock(ArticleDataPermission.class));
        controller = new ArticleController(mock(ArticleBufferService.class), articleService, channelService,
                accessGuard, layoutVariantService, mock(PathResolver.class), mock(Props.class));

        site = new Site();
        site.setId(SITE_ID);
        site.setProtocol("https");
        site.setDomain("example.com");

        Channel channel = new Channel();
        channel.setId(CHANNEL_ID);
        channel.setSiteId(SITE_ID);
        channel.setSite(site);
        channel.setSlug("news");
        when(channelService.select(CHANNEL_ID)).thenReturn(channel);

        article = article(ARTICLE_ID, "hello");
        when(articleService.select(ARTICLE_ID)).thenReturn(article);
        when(articleService.findBySiteIdAndSlug(SITE_ID, "hello")).thenReturn(article);

        // 匿名组缺省 allAccessPermission=true → 放行
        when(groupService.getAnonymous()).thenReturn(new Group());
        when(layoutVariantService.effectiveArticleTemplate(any(), any(), any()))
                .thenReturn(new LayoutVariantService.EffectiveTemplate(TEMPLATE, null));
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentUser();
    }

    private Article article(long id, String slug) {
        Article bean = new Article();
        bean.setId(id);
        bean.setSiteId(SITE_ID);
        bean.setChannelId(CHANNEL_ID);
        bean.setSite(site);
        bean.setSlug(slug);
        // status 缺省 0 = 已发布 → isNormal
        return bean;
    }

    private void permalinkMode(String mode) {
        Site.Permalink permalink = new Site.Permalink();
        permalink.setArticle(mode);
        site.setPermalink(permalink);
    }

    private Object get(String key, Integer page, Map<String, Object> model) {
        return controller.article(site, key, page, false, servletResponse, model);
    }

    private Object get(String key) {
        return get(key, null, new HashMap<>());
    }

    private void assertPermanentRedirect(Object result, String location) {
        assertInstanceOf(ResponseEntity.class, result);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.MOVED_PERMANENTLY, response.getStatusCode());
        assertEquals(location, response.getHeaders().getFirst(HttpHeaders.LOCATION));
        // 形态切换后旧规范形反向 301，浏览器缓存的 301 会成环：必须 no-cache。
        // 写在 Servlet 响应上（覆盖 SecurityHeadersFilter 预设的 public, max-age=300），而非实体头（addHeader 会追加成两条）
        assertEquals("no-cache", servletResponse.getHeader(HttpHeaders.CACHE_CONTROL));
        assertNull(response.getHeaders().getCacheControl());
    }

    // region 混合型（缺省 id-slug）

    @Test
    void idSlugMode_bareIdRedirectsToCanonical() {
        assertPermanentRedirect(get("123"), "/article/123-hello");
        verify(articleService, never()).findBySiteIdAndSlug(anyLong(), anyString());
    }

    @Test
    void idSlugMode_canonicalKeyRenders() {
        Map<String, Object> model = new HashMap<>();
        assertEquals(RENDERED, get("123-hello", null, model));
        assertSame(article, model.get("article"));
        assertEquals(CHANNEL_ID, ((Channel) model.get("channel")).getId());
        assertEquals(1, model.get(Frontends.PAGE));
        assertEquals(TEMPLATE, model.get(Frontends.TEMPLATE_NAME));
        assertSame(article, model.get(Frontends.PAGE_URL_RESOLVER));
    }

    @Test
    void idSlugMode_bareSlugResolvesViaSlugAndRedirects() {
        assertPermanentRedirect(get("hello"), "/article/123-hello");
        verify(articleService).findBySiteIdAndSlug(SITE_ID, "hello");
        verify(articleService, never()).select(anyLong());
    }

    @Test
    void idSlugMode_staleSlugWithRightIdRedirectsToCanonical() {
        // {id}-{旧别名}：按 ID 命中，路径段非规范 → 301
        assertPermanentRedirect(get("123-old-title"), "/article/123-hello");
        verify(articleService, never()).findBySiteIdAndSlug(anyLong(), anyString());
    }

    @Test
    void idSlugMode_pageSuffixIsPreservedOnRedirect() {
        assertPermanentRedirect(get("123", 2, new HashMap<>()), "/article/123-hello/2");
        Map<String, Object> model = new HashMap<>();
        assertEquals(RENDERED, get("123-hello", 2, model));
        assertEquals(2, model.get(Frontends.PAGE));
    }

    @Test
    void idSlugMode_idSentinelSlugRendersAtBareId() {
        Article sentinel = article(ARTICLE_ID, "123");
        when(articleService.select(ARTICLE_ID)).thenReturn(sentinel);
        assertEquals(RENDERED, get("123"));
    }

    // endregion

    // region 别名型 / 数字型

    @Test
    void slugMode_idDashSlugRedirectsToSlug() {
        permalinkMode(Site.Permalink.ARTICLE_SLUG);
        assertPermanentRedirect(get("123-hello"), "/article/hello");
        assertPermanentRedirect(get("123"), "/article/hello");
    }

    @Test
    void slugMode_bareSlugRenders() {
        permalinkMode(Site.Permalink.ARTICLE_SLUG);
        assertEquals(RENDERED, get("hello"));
    }

    @Test
    void idMode_idDashSlugRedirectsToId() {
        permalinkMode(Site.Permalink.ARTICLE_ID);
        assertPermanentRedirect(get("123-hello"), "/article/123");
        assertPermanentRedirect(get("hello"), "/article/123");
        assertEquals(RENDERED, get("123"));
    }

    // endregion

    // region 未命中与纯数字别名

    @Test
    void unknownKeyIs404() {
        assertThrows(Http404Exception.class, () -> get("nope"));
        when(articleService.select(999L)).thenReturn(null);
        assertThrows(Http404Exception.class, () -> get("999"));
        assertThrows(Http404Exception.class, () -> get("999-anything"));
    }

    @Test
    void digitsOnlyKeyThatIsNotAnIdResolvesViaSlug() {
        Article report = article(555L, "2024");
        when(articleService.select(2024L)).thenReturn(null);
        when(articleService.findBySiteIdAndSlug(SITE_ID, "2024")).thenReturn(report);
        // 缺省混合型：规范形为 555-2024 → 301
        assertPermanentRedirect(get("2024"), "/article/555-2024");
        // 别名型：2024 即规范形 → 渲染
        permalinkMode(Site.Permalink.ARTICLE_SLUG);
        assertEquals(RENDERED, get("2024"));
    }

    @Test
    void idDashSlugKeyWhoseIdMissesFallsBackToWholeKeyAsSlug() {
        // 别名可以数字开头（如 2024-report）：ID 未命中时整段按别名解析
        Article report = article(556L, "2024-report");
        when(articleService.select(2024L)).thenReturn(null);
        when(articleService.findBySiteIdAndSlug(SITE_ID, "2024-report")).thenReturn(report);
        assertPermanentRedirect(get("2024-report"), "/article/556-2024-report");
    }

    @Test
    void articleFromAnotherSiteIs404() {
        Article foreign = article(777L, "foreign");
        foreign.setSiteId(2L);
        when(articleService.select(777L)).thenReturn(foreign);
        assertThrows(Http404Exception.class, () -> get("777"));
    }

    // endregion
}
