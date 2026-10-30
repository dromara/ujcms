package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.component.ArticleDataPermission;
import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.component.WordScreeningService;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.service.ArticleExportService;
import com.ujcms.cms.core.service.ArticleRelatedService;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文章管理里的退回与待审投稿计数（docs/spec/member-contribute/plan.md D6）：退回只对待审核且未进入审批流程的文章，
 * 进入了流程的由“文章审核”处置；待审投稿计数只算投稿、待审核、无流程实例的，并走数据权限过滤。
 * 站点/用户上下文经 {@link Contexts} ThreadLocal 注入（standalone MockMvc 同线程执行）。
 */
class ArticleControllerRejectTest {
    private static final long SITE = 1L;
    private static final String URL = "/api/backend/core/article";

    private ArticleService service;
    private ArticleDataPermission dataPermission;
    private MockMvc mvc;
    private User user;

    @BeforeEach
    void setUp() {
        service = mock(ArticleService.class);
        dataPermission = mock(ArticleDataPermission.class);
        mvc = MockMvcBuilders.standaloneSetup(new ArticleController(mock(HtmlGenerator.class), service,
                mock(WordScreeningService.class), mock(SlugGenerator.class), mock(ArticleRelatedService.class),
                mock(TagService.class), mock(ChannelService.class), dataPermission,
                mock(ArticleExportService.class))).build();
        Site site = new Site();
        site.setId(SITE);
        Contexts.setCurrentSite(site);
        user = new User();
        user.setId(2L);
        Contexts.setCurrentUser(user);
        // 数据权限放行：这里只测退回自身的规则
        when(dataPermission.scopeOf(eq(user), anyLong()))
                .thenReturn(new ArticleDataPermission.Scope(2L, Role.DATA_SCOPE_ALL, null, null));
    }

    @AfterEach
    void tearDown() {
        Contexts.setCurrentSite(null);
        Contexts.setCurrentUser(null);
    }

    private Article pending(long id, String processInstanceId) {
        Article article = new Article();
        article.setId(id);
        article.setSiteId(SITE);
        article.setStatus(Article.STATUS_PENDING);
        article.setProcessInstanceId(processInstanceId);
        article.setTitle("t" + id);
        when(service.select(id)).thenReturn(article);
        return article;
    }

    private static Throwable assertCause(Class<?> type, Executable executable) {
        Throwable thrown = assertThrows(Throwable.class, executable);
        Throwable cause = thrown;
        while (cause.getCause() != null && !type.isInstance(cause)) {
            cause = cause.getCause();
        }
        assertInstanceOf(type, cause);
        return cause;
    }

    @Test
    void rejectsPendingArticlesOutsideAWorkflowWithTheReason() throws Exception {
        Article article = pending(100L, null);

        mvc.perform(put(URL + "/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[100],\"reason\":\"再改改\"}"))
                .andExpect(status().isOk());

        verify(service).reject(article, "再改改");
    }

    @Test
    void refusesAnArticleInsideAWorkflowAndWritesNothing() {
        pending(100L, null);
        pending(101L, "p1");

        Throwable cause = assertCause(Http400Exception.class, () -> mvc.perform(put(URL + "/reject")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[100,101],\"reason\":\"x\"}")));

        assertEquals("error.article.rejectInProcess", ((Http400Exception) cause).getCode());
        // 一篇不合格整批不执行
        verify(service, never()).reject(any(), any());
    }

    @Test
    void refusesANonPendingArticle() {
        Article article = pending(100L, null);
        article.setStatus(Article.STATUS_PUBLISHED);

        assertCause(Http400Exception.class, () -> mvc.perform(put(URL + "/reject")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[100],\"reason\":\"x\"}")));
        verify(service, never()).reject(any(), any());
    }

    @Test
    void contributePendingCountCountsOnlyHeldContributionsWithinTheDataPermission() throws Exception {
        when(service.count(any(ArticleArgs.class))).thenReturn(3L);

        mvc.perform(get(URL + "/contribute-pending-count")).andExpect(status().isOk()).andExpect(content().string("3"));

        ArgumentCaptor<ArticleArgs> captor = ArgumentCaptor.forClass(ArticleArgs.class);
        verify(dataPermission).filter(captor.capture(), eq(user));
        ArticleArgs args = captor.getValue();
        assertEquals(SITE, args.getQueryMap().get("EQ_siteId_Long"));
        assertEquals(Article.INPUT_TYPE_CONTRIBUTE, args.getQueryMap().get("EQ_inputType_Short"));
        assertTrue(args.getQueryMap().containsKey("IsNull_@articleExt-processInstanceId"));
        assertTrue(args.getQueryMap().containsKey("In_status_Short"));
    }
}
