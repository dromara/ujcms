package com.ujcms.cms.core.support;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 错误细节的可见性（docs/spec/template-error-disclosure D2）：按<b>查看者身份</b>而不是运行环境决定。
 * <p>
 * 后台操作员（{@link User#isAdminType()}）可见细节——模板错误在页内内联呈现，错误页与 JSON 错误响应保留
 * {@code message}；匿名访客与前台会员只见通用错误页和错误编号。身份来源：前台会话登录、控制台「预览」携带的
 * {@code _ptoken} 预览身份，或控制台 API 的 Bearer 令牌——预览令牌和会话都是已有机制，不新增任何开关。
 * <p>
 * 身份优先取 {@link Contexts#findCurrentUser()}（前台 / 后台拦截器已解析）；错误页的 ERROR 转发不经这些拦截器，
 * 退而按 SecurityContext 的用户名查库（安全过滤链对 ERROR 转发同样生效，预览令牌与会话在其中再次建立上下文）。
 *
 * @author PONY
 */
@Component
public class ErrorDisclosure {
    private final UserService userService;

    public ErrorDisclosure(UserService userService) {
        this.userService = userService;
    }

    /**
     * 当前查看者是否可见错误细节。
     */
    public boolean canViewDetails() {
        User user = Contexts.findCurrentUser();
        if (user == null) {
            String username = authenticatedUsername();
            user = username != null ? userService.selectByUsername(username) : null;
        }
        return user != null && user.isAdminType();
    }

    @Nullable
    private static String authenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}
