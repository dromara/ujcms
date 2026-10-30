package com.ujcms.cms.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link AuthenticatedCacheControlFilter}：上下文非匿名时，把 {@link SecurityHeadersFilter} 预写的前台页
 * 缓存策略改为 {@code no-store}；匿名或无认证时原样保留。
 */
class AuthenticatedCacheControlFilterTest {

    private static final String CACHE_CONTROL = "Cache-Control";
    private static final String PAGE_POLICY = "private, max-age=300, must-revalidate";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletResponse run(Authentication authentication) throws Exception {
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        MockHttpServletResponse resp = new MockHttpServletResponse();
        // SecurityHeadersFilter 已在安全链之前写入前台页策略
        resp.setHeader(CACHE_CONTROL, PAGE_POLICY);
        resp.addHeader("Vary", "Cookie");
        new AuthenticatedCacheControlFilter().doFilter(
                new MockHttpServletRequest("GET", "/article/3"), resp, new MockFilterChain());
        return resp;
    }

    @Test
    void formLogin_overridesPagePolicyWithNoStore() throws Exception {
        MockHttpServletResponse resp = run(UsernamePasswordAuthenticationToken.authenticated(
                "admin", null, AuthorityUtils.NO_AUTHORITIES));
        assertEquals(List.of("no-store"), resp.getHeaders(CACHE_CONTROL),
                "setHeader replaces the page policy; never a second Cache-Control line");
    }

    @Test
    void rememberMe_overridesPagePolicyWithNoStore() throws Exception {
        MockHttpServletResponse resp = run(new RememberMeAuthenticationToken(
                "key", "admin", AuthorityUtils.NO_AUTHORITIES));
        assertEquals("no-store", resp.getHeader(CACHE_CONTROL));
    }

    @Test
    void previewTokenIdentity_overridesPagePolicyWithNoStore() throws Exception {
        // PreviewAuthFilter 置入的身份：无权限但已认证的 UsernamePasswordAuthenticationToken
        MockHttpServletResponse resp = run(new UsernamePasswordAuthenticationToken(
                "admin", null, Collections.emptyList()));
        assertEquals("no-store", resp.getHeader(CACHE_CONTROL));
    }

    @Test
    void anonymous_keepsPagePolicy() throws Exception {
        MockHttpServletResponse resp = run(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertEquals(PAGE_POLICY, resp.getHeader(CACHE_CONTROL));
        assertEquals(List.of("Cookie"), resp.getHeaders("Vary"));
    }

    @Test
    void noAuthentication_keepsPagePolicy() throws Exception {
        MockHttpServletResponse resp = run(null);
        assertEquals(PAGE_POLICY, resp.getHeader(CACHE_CONTROL));
    }
}
