package com.ujcms.cms.core.security;

import java.io.IOException;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 已认证响应一律 {@code Cache-Control: no-store}。
 *
 * <p>{@link SecurityHeadersFilter} 先于安全链运行，给前台页写入 {@code private, max-age=300} 时会话尚未解析
 * （容器层的 {@code getUserPrincipal()} 恒为 null）。本过滤器装配在前台安全链内、{@link PreviewAuthFilter} 之后
 * ——表单登录、记住我、预览令牌此时都已置入上下文——上下文非匿名即改写为 {@code no-store}：登录态页面带会员菜单、
 * 评论框、用户名等个性化内容，既不能进共享缓存（CDN、反向代理），也不该留在浏览器缓存里给下一位使用者。
 * 自带 Cache-Control 的资源处理器（{@code /_ujcms/**}、T4 的 {@code /static/easebar/**}）在其后仍会覆盖本值。
 * 契约：{@code docs/spec/http-cache/plan.md}。
 *
 * @author PONY
 */
public class AuthenticatedCacheControlFilter extends OncePerRequestFilter {
    private static final String CACHE_NO_STORE = "no-store";

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (trustResolver.isAuthenticated(authentication)) {
            response.setHeader("Cache-Control", CACHE_NO_STORE);
        }
        chain.doFilter(request, response);
    }
}
