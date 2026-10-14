package com.ujcms.cms.core.web.api;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.component.ArticleAccessGuard;
import com.ujcms.cms.core.component.IpInteractionCounterService;
import com.ujcms.cms.core.component.ViewCountService;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.ArticleLuceneArgs;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.service.ActionService;
import com.ujcms.cms.core.service.ArticleBufferService;
import com.ujcms.cms.core.service.ArticleRelatedService;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 前台文章公共接口（docs/spec/article-management/plan.md Phase 2）。
 * <p>
 * 上一篇、下一篇只给访问者读得到的文章、只给列表字段（H6）；浏览、顶踩、统计只对读得到的文章开放；
 * 列表不传 limit 时给 20 条而不是 1000 条；关键词经全文索引解析，不再让数据库对正文做 LIKE；
 * 正文等大字段不能出现在匿名请求的查询键里。
 */
@SuppressWarnings("unchecked")
class PublicArticleControllerTest {
    private static final long SITE = 1L;

    private ArticleService articleService;
    private ArticleBufferService bufferService;
    private ViewCountService viewCountService;
    private ArticleAccessGuard accessGuard;
    private ArticleLucene articleLucene;
    private ArticleController controller;

    @BeforeEach
    void setUp() {
        Contexts.clearCurrentUser();
        articleService = mock(ArticleService.class);
        bufferService = mock(ArticleBufferService.class);
        viewCountService = mock(ViewCountService.class);
        accessGuard = mock(ArticleAccessGuard.class);
        articleLucene = mock(ArticleLucene.class);
        SiteResolver siteResolver = mock(SiteResolver.class);
        Site site = new Site();
        site.setId(SITE);
        when(siteResolver.resolve(any(HttpServletRequest.class))).thenReturn(site);
        controller = new ArticleController(siteResolver, mock(ActionService.class), mock(ChannelService.class),
                articleService, bufferService, viewCountService, mock(Props.class),
                mock(IpInteractionCounterService.class), mock(ArticleRelatedService.class), accessGuard,
                articleLucene);
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentUser();
    }

    private static MockHttpServletRequest request(String queryString) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/article");
        request.setQueryString(queryString);
        return request;
    }

    private static Article article(long id) {
        Article article = new Article();
        article.setId(id);
        return article;
    }

    // region 上一篇、下一篇（H6）

    @Test
    void nextAndPrevReturnOnlyWhatTheVisitorMayRead() {
        Article neighbour = article(2L);
        when(articleService.findNext(1L, 10L, 5L)).thenReturn(neighbour);
        when(articleService.findPrev(1L, 10L, 5L)).thenReturn(neighbour);
        MockHttpServletRequest request = request("id=1&order=10&channelId=5");

        when(accessGuard.isReadable(neighbour, null)).thenReturn(false);
        assertNull(controller.next(true, request));
        assertNull(controller.prev(true, request));

        when(accessGuard.isReadable(neighbour, null)).thenReturn(true);
        assertSame(neighbour, controller.next(true, request));
        assertSame(neighbour, controller.prev(true, request));
    }

    @Test
    void nextAndPrevAreListViewsWithoutTheBody() throws Exception {
        for (String name : new String[]{"next", "prev"}) {
            Method method = ArticleController.class.getMethod(name, boolean.class, HttpServletRequest.class);
            JsonView view = method.getAnnotation(JsonView.class);
            assertTrue(view != null, name + " must carry @JsonView");
            assertArrayEquals(new Class<?>[]{Views.List.class}, view.value());
        }
    }

    // endregion

    // region 源文章

    /**
     * 子行（扩展栏目、推送）公开不等于源文章公开：源文章是草稿、已下线时不随子行给出；给出时也只有标识它的几个属性，
     * 没有正文、状态、退回原因
     */
    @Test
    void aChildNeverCarriesANonPublicSource() {
        Article source = article(1L);
        source.setStatus(Article.STATUS_DRAFT);
        Article child = article(2L);
        child.setStatus(Article.STATUS_PUBLISHED);
        child.setSrc(source);
        when(articleService.select(2L)).thenReturn(child);

        assertNull(controller.show(2L, false).getSrc());

        source.setStatus(Article.STATUS_PUBLISHED);
        child.setSrc(source);
        assertSame(source, controller.show(2L, false).getSrc());

        // 上一篇、下一篇同样处理
        source.setStatus(Article.STATUS_OFFLINE);
        when(articleService.findNext(1L, 10L, 5L)).thenReturn(child);
        when(accessGuard.isReadable(child, null)).thenReturn(true);
        assertNull(controller.next(true, request("id=1&order=10&channelId=5")).getSrc());
    }

    @Test
    void theSourceIsSerialisedAsAReferenceNotAsAnArticle() throws Exception {
        JsonIncludeProperties included = Article.class.getDeclaredField("src").getAnnotation(JsonIncludeProperties.class);

        assertTrue(included != null, "src must list the properties it exposes");
        assertEquals(List.of("id", "siteId", "title", "url", "site", "channel"), Arrays.asList(included.value()));
    }

    // endregion

    // region 列表

    @Test
    void listDefaultsToTwentyRowsAndHonoursAnExplicitLimit() {
        controller.list(request(null));
        verify(articleService).selectList(any(ArticleArgs.class), eq(0), eq(ArticleController.DEFAULT_LIMIT));
        assertEquals(20, ArticleController.DEFAULT_LIMIT);

        controller.list(request("limit=200&offset=40"));
        verify(articleService).selectList(any(ArticleArgs.class), eq(40), eq(200));
    }

    @Test
    void aKeywordIsResolvedThroughTheIndexNotALikeOnTheBody() {
        EsArticle hit1 = new EsArticle();
        hit1.setId(11L);
        EsArticle hit2 = new EsArticle();
        hit2.setId(12L);
        when(articleLucene.findAll(any(), isNull(), any())).thenReturn(new PageImpl<>(List.of(hit1, hit2)));

        controller.list(request("q=marathon"));

        ArgumentCaptor<ArticleLuceneArgs> search = ArgumentCaptor.forClass(ArticleLuceneArgs.class);
        verify(articleLucene).findAll(search.capture(), isNull(), any());
        assertEquals("marathon", search.getValue().getTitle());
        assertEquals("marathon", search.getValue().getBody());
        assertEquals(SITE, search.getValue().getSiteId());
        // 只在已发布的文章里搜
        assertEquals(List.of((int) Article.STATUS_PUBLISHED), search.getValue().getStatus());

        ArgumentCaptor<ArticleArgs> args = ArgumentCaptor.forClass(ArticleArgs.class);
        verify(articleService).selectList(args.capture(), anyInt(), anyInt());
        Map<String, Object> queryMap = args.getValue().getQueryMap();
        assertEquals(List.of(11L, 12L), List.copyOf((Collection<Long>) queryMap.get("In_id_Long")));
        // 没有任何落到正文、标题上的 LIKE 条件
        assertFalse(queryMap.keySet().stream().anyMatch(key -> key.contains("articleExt")), queryMap.keySet().toString());
    }

    @Test
    void textSearchesTheBodyOnlyAndNoHitMeansNoRows() {
        when(articleLucene.findAll(any(), isNull(), any())).thenReturn(new PageImpl<>(List.of()));

        controller.list(request("text=nothing"));

        ArgumentCaptor<ArticleLuceneArgs> search = ArgumentCaptor.forClass(ArticleLuceneArgs.class);
        verify(articleLucene).findAll(search.capture(), isNull(), any());
        assertNull(search.getValue().getTitle());
        assertEquals("nothing", search.getValue().getBody());
        ArgumentCaptor<ArticleArgs> args = ArgumentCaptor.forClass(ArticleArgs.class);
        verify(articleService).selectList(args.capture(), anyInt(), anyInt());
        // 没有命中：一个不存在的ID（空集合会被当作没有这个条件）
        assertEquals(List.of(-1L), List.copyOf((Collection<Long>) args.getValue().getQueryMap().get("In_id_Long")));
    }

    @Test
    void listsWithoutAKeywordNeverTouchTheIndex() {
        controller.list(request("channelId=5"));
        verify(articleLucene, never()).findAll(any(), any(), any());
    }

    @Test
    void rawQueryKeysOnTheBodyAreRefused() {
        assertThrows(Http400Exception.class, () -> controller.list(request("Q_Contains_@articleExt-text=x")));
        assertThrows(Http400Exception.class, () -> controller.list(request("Q_OrderBy=@articleExt-markdown")));
        verify(articleService, never()).selectList(any(ArticleArgs.class), anyInt(), anyInt());
    }

    // endregion

    // region 浏览、顶踩、统计

    @Test
    void viewsAreCountedOnlyForArticlesTheVisitorMayRead() {
        controller.view(1L);

        ArgumentCaptor<Predicate<Article>> readable = ArgumentCaptor.forClass(Predicate.class);
        verify(viewCountService).viewArticle(eq(1L), isNull(), readable.capture());
        Article draft = article(1L);
        when(accessGuard.isReadable(draft, null)).thenReturn(false);
        assertFalse(readable.getValue().test(draft));
        Article published = article(2L);
        when(accessGuard.isReadable(published, null)).thenReturn(true);
        assertTrue(readable.getValue().test(published));
    }

    @Test
    void upDownAndBufferIgnoreArticlesTheVisitorCannotRead() {
        Article draft = article(1L);
        when(articleService.select(1L)).thenReturn(draft);
        when(accessGuard.isReadable(draft, null)).thenReturn(false);
        MockHttpServletRequest request = request(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(0, controller.up(1L, request, response));
        assertEquals(0, controller.down(1L, request, response));
        assertThrows(Http404Exception.class, () -> controller.buffer(1L));
        verify(bufferService, never()).incrementUps(anyLong());
        verify(bufferService, never()).incrementDowns(anyLong());
        verify(bufferService, never()).select(anyLong());
    }

    @Test
    void theUnguardedDownloadCounterIsGone() {
        // POST /api/article/download/{id} 不带下载密钥、不看文章状态，谁都能给任意文章刷下载数；主题走的是
        // download-params + /download-file。该接口已删除
        assertFalse(Arrays.stream(ArticleController.class.getMethods()).anyMatch(m -> m.getName().equals("download")));
    }

    // endregion
}
