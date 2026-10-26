package com.ujcms.cms.core.support;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import freemarker.cache.StringTemplateLoader;
import freemarker.core.Environment;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 模板错误按查看者身份呈现（docs/spec/template-error-disclosure D1/D2）：匿名访客与会员拿到重新抛出的异常
 * （Spring 换成通用错误页），后台操作员在前台页面内看到带编号的错误框且渲染继续；非页面渲染（静态页生成）
 * 和 {@code [#attempt]} 块内一律重新抛出。每个错误都进入记录器并把编号挂到请求上。
 */
class TemplateErrorHandlerTest {
    private final TemplateErrorRecorder recorder = new TemplateErrorRecorder();
    private final UserService userService = mock(UserService.class);
    private final TemplateErrorHandler handler = new TemplateErrorHandler(recorder, new ErrorDisclosure(userService));
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/channel/news");

    @BeforeEach
    void bindRequest() {
        request.setQueryString("_ptoken=secret");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clear() {
        Contexts.clearCurrentUser();
        Contexts.clearCurrentSite();
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousVisitorGetsTheExceptionAndAnErrorId() {
        assertThrows(TemplateException.class, () -> render("before ${missing} after", true));

        List<TemplateErrorRecorder.TemplateError> errors = recorder.list(null);
        assertEquals(1, errors.size());
        TemplateErrorRecorder.TemplateError error = errors.get(0);
        assertTrue(error.id().matches("[0-9a-f]{8}"), error.id());
        assertEquals("page.html", error.template());
        assertEquals(1, error.line());
        assertEquals("/channel/news", error.path(), "URI only: the query string may carry a preview token");
        assertTrue(error.message().contains("missing"));
        assertEquals(error.id(), request.getAttribute(TemplateErrorHandler.ERROR_ID_ATTRIBUTE));
    }

    @Test
    void operatorSeesInlineDetailsAndRenderingContinues() throws Exception {
        Contexts.setCurrentUser(user(User.TYPE_NORMAL));

        String out = render("before ${missing} after", true);

        String id = recorder.list(null).get(0).id();
        assertTrue(out.contains("before") && out.contains("after"), out);
        assertTrue(out.contains("FREEMARKER ERROR MESSAGE"), out);
        assertTrue(out.contains("[" + id + "]"), out);
        assertTrue(out.contains("missing"), out);
    }

    @Test
    void memberIsNotAnOperator() {
        Contexts.setCurrentUser(user(User.TYPE_MEMBER));
        assertThrows(TemplateException.class, () -> render("${missing}", true));
    }

    @Test
    void nonPageRenderingNeverInlines() {
        // 静态页生成、邮件等不置 INLINE_DETAILS：即便操作员触发，细节也不能写进文件
        Contexts.setCurrentUser(user(User.TYPE_NORMAL));
        assertThrows(TemplateException.class, () -> render("${missing}", false));
        assertEquals(1, recorder.list(null).size());
    }

    @Test
    void attemptBlockStillRecovers() throws Exception {
        Contexts.setCurrentUser(user(User.TYPE_NORMAL));

        String out = render("<#attempt>${missing}<#recover>recovered</#attempt>", true);

        assertEquals("recovered", out);
        assertEquals(1, recorder.list(null).size(), "recovered errors are still recorded");
    }

    @Test
    void identityFallsBackToTheSecurityContext() throws Exception {
        // 错误页的 ERROR 转发不经前台拦截器：按 SecurityContext 用户名查库
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, List.of()));
        when(userService.selectByUsername("admin")).thenReturn(user(User.TYPE_SYSTEM));

        String out = render("${missing}", true);

        assertTrue(out.contains("FREEMARKER ERROR MESSAGE"), out);
    }

    @Test
    void recorderFiltersBySiteAndKeepsSitelessEntries() {
        Site site = new Site();
        site.setId(2L);
        Contexts.setCurrentSite(site);
        assertThrows(TemplateException.class, () -> render("${missing}", true));
        Contexts.clearCurrentSite();
        assertThrows(TemplateException.class, () -> render("${missing}", true));

        assertEquals(2, recorder.list(2L).size());
        assertEquals(1, recorder.list(3L).size(), "site-less entries (error page itself) are visible everywhere");
        assertEquals(2, recorder.list(null).size());
    }

    @Test
    void recorderIsARingBuffer() {
        for (int i = 0; i <= TemplateErrorRecorder.CAPACITY; i++) {
            recorder.record(new TemplateException("boom " + i, null));
        }
        List<TemplateErrorRecorder.TemplateError> errors = recorder.list(null);
        assertEquals(TemplateErrorRecorder.CAPACITY, errors.size());
        assertTrue(errors.get(0).message().contains("boom " + TemplateErrorRecorder.CAPACITY), "newest first");
    }

    private String render(String source, boolean inlineDetails) throws Exception {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setTemplateExceptionHandler(handler);
        configuration.setLogTemplateExceptions(false);
        StringTemplateLoader loader = new StringTemplateLoader();
        loader.putTemplate("page.html", source);
        configuration.setTemplateLoader(loader);
        Template template = configuration.getTemplate("page.html");
        StringWriter out = new StringWriter();
        Environment env = template.createProcessingEnvironment(Map.of(), out);
        if (inlineDetails) {
            env.setCustomAttribute(TemplateErrorHandler.INLINE_DETAILS, Boolean.TRUE);
        }
        env.process();
        return out.toString();
    }

    private static User user(short type) {
        User user = new User();
        user.setType(type);
        return user;
    }
}
