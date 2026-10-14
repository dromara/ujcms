package com.ujcms.cms.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 插槽预览鉴权过滤器（Phase 7 / §7.0；P2-1 扩展至主题预览）。
 * 请求带预览标记（{@code _preview=draft} / {@code _theme=<候选主题>}）
 * + 有效预览令牌（{@link PreviewTokenService}，取自 {@code _ptoken} 查询参数）且当前匿名时，
 * 以令牌 subject 置 {@code SecurityContext}，令
 * {@code FrontendInterceptor} / {@code FrontendApiInterceptor}
 * 的既有草稿门禁（{@code Contexts.findCurrentUser() != null}）自然通过——这些既有门禁均不改一行（§7.2）。
 *
 * <p>装配于前台链（{@code SecurityConfig#frontendFilterChain}，无路径限制）——模板轨道页面以
 * 浏览器导航 {@code ?_preview=draft&_ptoken=} 携带令牌（浏览器无法设置请求头）。
 * （原 /api 链的 SSR 解析端点装配随 ssr-reduction P4 移除。）
 *
 * <p>位于 {@code AuthorizationFilter} 之前、{@code AnonymousAuthenticationFilter} 与 OAuth2
 * {@code BearerTokenAuthenticationFilter} 之后：真实 Bearer 已认证则上下文非匿名，本过滤器自然跳过
 * （真实身份优先）；置入的 authentication 仅供据用户名查库重建用户（用户已删除 / 锁定则草稿不可见，
 * 失败即安全），不携带任何权限（{@code emptyList}），不参与授权。
 *
 * <p><b>仅安全方法</b>（GET/HEAD，§21 Q12(g)）：预览身份只服务页面/数据的草稿渲染；前台链的写请求
 * （评论、表单、留言等 POST 会以 {@code Contexts} 身份行事）绝不能凭一枚泄漏的 {@code _ptoken}
 * 重建完整前台身份执行。非安全方法直接跳过，写路径回归真实登录态。
 *
 * @author PONY
 */
public class PreviewAuthFilter extends OncePerRequestFilter {
    /** 前台预览参数名（浏览器导航；与 {@code FrontendInterceptor} 一致）。 */
    private static final String PREVIEW_PARAM = "_preview";
    /** 预览参数值：草稿可见。 */
    private static final String PREVIEW_DRAFT = "draft";
    /**
     * 主题预览覆盖参数名（design-review P2-1 / H1）。带 {@code _theme=<候选主题>} 的请求同样以预览
     * 令牌置身份——主题试看默认<b>不带</b> {@code _preview=draft}（呈现已发布内容，与激活后完全一致）；
     * 两参数可叠加（草稿 + 候选主题）。
     */
    private static final String THEME_PARAM = "_theme";
    /** 预览令牌查询参数名（前台浏览器导航用）。 */
    private static final String TOKEN_PARAM = "_ptoken";

    private final PreviewTokenService tokenService;

    public PreviewAuthFilter(PreviewTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isSafeMethod(request) && isPreviewRequest(request) && isAnonymous()) {
            String username = tokenService.verify(resolveToken(request));
            if (username != null) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        username, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 仅安全方法承载预览身份（§21 Q12(g)）：草稿渲染只有读取；写请求回归真实登录态。
     */
    private static boolean isSafeMethod(HttpServletRequest request) {
        String method = request.getMethod();
        return "GET".equals(method) || "HEAD".equals(method);
    }

    private static boolean isPreviewRequest(HttpServletRequest request) {
        return PREVIEW_DRAFT.equals(request.getParameter(PREVIEW_PARAM))
                || hasThemePreview(request);
    }

    private static boolean hasThemePreview(HttpServletRequest request) {
        String theme = request.getParameter(THEME_PARAM);
        return theme != null && !theme.isBlank();
    }

    @Nullable
    private static String resolveToken(HttpServletRequest request) {
        return request.getParameter(TOKEN_PARAM);
    }

    private static boolean isAnonymous() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }
}
