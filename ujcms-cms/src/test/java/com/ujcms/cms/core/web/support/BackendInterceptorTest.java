package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.web.exception.Http401Exception;
import com.ujcms.common.web.exception.Http403Exception;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 后台拦截器默认拒绝（backend-authorization §2）：匿名请求一律 401；已登录用户访问没有 {@code @PreAuthorize}
 * 的控制器方法在装配站点上下文之前即被 403，响应不带处理器身份；方法级、类级、父类方法、接口方法上的注解均算
 * 声明（与 Spring Security 的查找策略一致）；非 {@code @Controller} 处理器（静态资源、框架的 OPTIONS 应答）不在范围内。
 */
class BackendInterceptorTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 过滤链对匿名请求放行时留下的身份：资源服务器链总会给出一个 Authentication，拦截器据此判 401 */
    private static void anonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    }

    private static void signedIn() {
        TestingAuthenticationToken token = new TestingAuthenticationToken("alice", "n/a", "backend");
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @Test
    void anonymousCallerGets401WhetherOrNotTheHandlerIsAnnotated() throws NoSuchMethodException {
        anonymous();
        UserService userService = mock(UserService.class);
        SiteService siteService = mock(SiteService.class);
        BackendInterceptor interceptor = new BackendInterceptor(userService, siteService, mock(OrgService.class),
                mock(Props.class));
        for (Object bean : new Object[]{new Unsecured(), new MethodSecured()}) {
            HandlerMethod handler = handlerOf(bean, "list");
            assertThrows(Http401Exception.class, () -> interceptor.preHandle(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), handler));
        }
        verifyNoInteractions(siteService);
    }

    @Test
    void signedInCallerIsRejectedBeforeTheSiteContextIsBuilt() throws NoSuchMethodException {
        UserService userService = mock(UserService.class);
        SiteService siteService = mock(SiteService.class);
        OrgService orgService = mock(OrgService.class);
        signedIn();
        when(userService.selectByUsername(any())).thenReturn(new User());
        BackendInterceptor interceptor = new BackendInterceptor(userService, siteService, orgService, mock(Props.class));
        HandlerMethod handler = handlerOf(new Unsecured(), "list");

        Http403Exception ex = assertThrows(Http403Exception.class, () -> interceptor.preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), handler));
        assertFalse(ex.getCode().contains("Unsecured"), "response must not name the handler: " + ex.getCode());
        verifyNoInteractions(siteService, orgService);
    }

    @Test
    void annotationAnywhereInTheTypeHierarchyCounts() throws NoSuchMethodException {
        assertFalse(BackendInterceptor.isSecured(handlerOf(new Unsecured(), "list")));
        assertTrue(BackendInterceptor.isSecured(handlerOf(new MethodSecured(), "list")));
        assertTrue(BackendInterceptor.isSecured(handlerOf(new ClassSecured(), "list")));
        assertTrue(BackendInterceptor.isSecured(handlerOf(new InheritedSecured(), "list")));
        assertTrue(BackendInterceptor.isSecured(handlerOf(new InterfaceSecured(), "list")));
    }

    @Test
    void nonControllerHandlersAreOutOfScope() throws NoSuchMethodException {
        assertTrue(BackendInterceptor.isSecured(new ResourceHttpRequestHandler()));
        assertTrue(BackendInterceptor.isSecured(handlerOf(new NotAController(), "list")));
    }

    private static HandlerMethod handlerOf(Object bean, String name) throws NoSuchMethodException {
        Method method = bean.getClass().getMethod(name);
        return new HandlerMethod(bean, method);
    }

    @RestController
    public static class Unsecured {
        @GetMapping("list")
        public void list() {
        }
    }

    @RestController
    public static class MethodSecured {
        @GetMapping("list")
        @PreAuthorize("hasAnyAuthority('backend','*')")
        public void list() {
        }
    }

    @RestController
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public static class ClassSecured {
        @GetMapping("list")
        public void list() {
        }
    }

    @RestController
    public static class InheritedSecured extends MethodSecured {
    }

    /** 注解声明在接口方法上，实现类覆盖时未重复书写：Spring Security 照样生效，这里也必须算作已声明 */
    public interface SecuredContract {
        @GetMapping("list")
        @PreAuthorize("hasAnyAuthority('backend','*')")
        void list();
    }

    @RestController
    public static class InterfaceSecured implements SecuredContract {
        @Override
        public void list() {
        }
    }

    /** 框架自动应答 OPTIONS 的 HttpOptionsHandler 一类：有处理方法、不是控制器 */
    public static class NotAController {
        public void list() {
        }
    }
}
