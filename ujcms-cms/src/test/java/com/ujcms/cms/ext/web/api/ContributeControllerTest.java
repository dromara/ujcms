package com.ujcms.cms.ext.web.api;

import com.ujcms.cms.core.component.CaptchaGuard;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.service.ContributeService;
import com.ujcms.cms.ext.service.ContributeService.Content;
import com.ujcms.common.web.exception.Http401Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import jakarta.servlet.http.HttpServletRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会员投稿接口的门禁（docs/spec/member-contribute/plan.md）：未登录 401，站点未开放 404，栏目不接受 403；
 * 通过后服务端只把白名单内容交给 Service。standalone MockMvc 无项目异常解析器：控制器异常以 ServletException 包裹抛出
 */
class ContributeControllerTest {
    private static final String URL = "/api/contribute";
    private static final String BODY = "{\"siteId\":\"1\",\"channelId\":\"5\",\"title\":\"标题\",\"text\":\"<p>x</p>\","
            + "\"tagNames\":[\"a\"],\"customs\":{\"city\":\"北京\"}}";

    private ContributeService service;
    private ChannelService channelService;
    private CaptchaGuard captchaGuard;
    private MockMvc mvc;
    private final Site site = new Site();
    private final User member = new User();

    @BeforeEach
    void setUp() {
        service = mock(ContributeService.class);
        channelService = mock(ChannelService.class);
        captchaGuard = mock(CaptchaGuard.class);
        SiteResolver siteResolver = mock(SiteResolver.class);
        site.setId(1L);
        when(siteResolver.resolve(any(HttpServletRequest.class), eq(1L))).thenReturn(site);
        when(service.isOpen(site)).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new ContributeController(service, channelService, siteResolver,
                captchaGuard)).build();
        member.setId(7L);
        Contexts.setCurrentUser(member);
    }

    @AfterEach
    void tearDown() {
        Contexts.setCurrentUser(null);
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
    void anonymousIsRefused() {
        Contexts.setCurrentUser(null);
        assertCause(Http401Exception.class, () -> mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)));
        verify(service, never()).submit(any(), any(), any(), any());
    }

    @Test
    void closedSiteAnswers404AndAClosedChannel403() {
        when(service.isOpen(site)).thenReturn(false);
        assertCause(Http404Exception.class, () -> mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)));

        when(service.isOpen(site)).thenReturn(true);
        Channel channel = new Channel();
        channel.setId(5L);
        when(channelService.selectInSite(5L, 1L)).thenReturn(channel);
        when(service.canContribute(site, member, channel)).thenReturn(false);
        assertCause(Http403Exception.class, () -> mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY)));
        verify(service, never()).submit(any(), any(), any(), any());
    }

    @Test
    void submitHandsTheWhitelistedContentToTheServiceAndReturnsTheResult() throws Exception {
        Channel channel = new Channel();
        channel.setId(5L);
        when(channelService.selectInSite(5L, 1L)).thenReturn(channel);
        when(service.canContribute(site, member, channel)).thenReturn(true);
        when(captchaGuard.verify(any(), eq(CaptchaGuard.SCOPE_CONTRIBUTE), any(), any(), any())).thenReturn(null);
        Article article = new Article();
        article.setId(100L);
        article.setStatus(Article.STATUS_PENDING);
        when(service.submit(eq(site), eq(member), eq(channel), any(Content.class))).thenReturn(article);

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value("100"))
                .andExpect(jsonPath("$.result.status").value(11));

        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(service).submit(eq(site), eq(member), eq(channel), captor.capture());
        assertEquals("标题", captor.getValue().title());
        assertEquals("北京", captor.getValue().customs().get("city"));
    }

    @Test
    void captchaFailureAnswersAFailureBodyWithoutSubmitting() throws Exception {
        Channel channel = new Channel();
        channel.setId(5L);
        when(channelService.selectInSite(5L, 1L)).thenReturn(channel);
        when(service.canContribute(site, member, channel)).thenReturn(true);
        when(captchaGuard.verify(any(), eq(CaptchaGuard.SCOPE_CONTRIBUTE), any(), any(), any()))
                .thenReturn("error.captchaRequired");

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(-1));

        verify(service, never()).submit(any(), any(), any(), any());
        verify(channelService).selectInSite(anyLong(), anyLong());
    }
}
