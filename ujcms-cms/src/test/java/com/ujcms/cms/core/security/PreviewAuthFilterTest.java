package com.ujcms.cms.core.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 插槽预览鉴权过滤器测试（Phase 7 / §7.0）：有效预览令牌置 SecurityContext，
 * 无 {@code _preview} / 无效令牌不置，已认证则跳过校验（不覆盖既有身份）。
 */
class PreviewAuthFilterTest {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthenticationForValidPreviewToken() throws Exception {
        PreviewTokenService tokenService = mock(PreviewTokenService.class);
        when(tokenService.verify("good")).thenReturn("admin");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setParameter("_preview", "draft");
        req.setParameter("_ptoken", "good");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new PreviewAuthFilter(tokenService).doFilter(req, resp, chain);

        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(chain).doFilter(req, resp);
    }

    @Test
    void leavesContextAnonymousForInvalidToken() throws Exception {
        PreviewTokenService tokenService = mock(PreviewTokenService.class);
        when(tokenService.verify(any())).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setParameter("_preview", "draft");
        req.setParameter("_ptoken", "bad");

        new PreviewAuthFilter(tokenService).doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doesNothingWithoutPreviewParam() throws Exception {
        PreviewTokenService tokenService = mock(PreviewTokenService.class);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setParameter("_ptoken", "good");

        new PreviewAuthFilter(tokenService).doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(tokenService, never()).verify(any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void preservesExistingAuthenticationAndSkipsTokenCheck() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("existing", null, Collections.emptyList()));
        PreviewTokenService tokenService = mock(PreviewTokenService.class);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setParameter("_preview", "draft");
        req.setParameter("_ptoken", "good");

        new PreviewAuthFilter(tokenService).doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(tokenService, never()).verify(any());
        assertEquals("existing", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void skipsNonSafeMethods() throws Exception {
        // §21 Q12(g)：预览身份只承载草稿渲染（GET/HEAD）；写请求（前台评论/表单 POST 以
        // Contexts 身份行事）不能凭泄漏的 _ptoken 重建前台身份执行
        PreviewTokenService tokenService = mock(PreviewTokenService.class);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/");
        req.setParameter("_preview", "draft");
        req.setParameter("_ptoken", "good");

        new PreviewAuthFilter(tokenService).doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(tokenService, never()).verify(any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // —— design-review P2-1 / H1：主题预览覆盖（?_theme= 同为预览标记）——

    @Test
    void setsAuthenticationForThemePreviewToken() throws Exception {
        // 主题试看默认不带 _preview=draft（呈现已发布内容，与激活后一致），_theme 自身即预览标记
        PreviewTokenService tokenService = mock(PreviewTokenService.class);
        when(tokenService.verify("good")).thenReturn("admin");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setParameter("_theme", "candidate");
        req.setParameter("_ptoken", "good");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new PreviewAuthFilter(tokenService).doFilter(req, resp, chain);

        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(chain).doFilter(req, resp);
    }

    @Test
    void blankThemeParamIsNotAPreviewMarker() throws Exception {
        PreviewTokenService tokenService = mock(PreviewTokenService.class);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setParameter("_theme", " ");
        req.setParameter("_ptoken", "good");

        new PreviewAuthFilter(tokenService).doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(tokenService, never()).verify(any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void themePreviewSkipsNonSafeMethods() throws Exception {
        PreviewTokenService tokenService = mock(PreviewTokenService.class);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/");
        req.setParameter("_theme", "candidate");
        req.setParameter("_ptoken", "good");

        new PreviewAuthFilter(tokenService).doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        verify(tokenService, never()).verify(any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
