package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * 前台 API 拦截器
 *
 * @author PONY
 */
public class FrontendApiInterceptor implements HandlerInterceptor {
    private final UserService userService;

    public FrontendApiInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 当前用户
        Optional.ofNullable(Contexts.findCurrentUsername()).map(userService::selectByUsername)
                .ifPresent(Contexts::setCurrentUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        Contexts.clearCurrentUser();
        Contexts.clearCurrentSite();
        Contexts.clearMobile();
    }
}
