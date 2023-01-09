package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Frontends;
import org.springframework.lang.Nullable;
import org.springframework.mobile.device.Device;
import org.springframework.mobile.device.DeviceResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * 前台拦截器
 *
 * @author PONY
 */
public class FrontendInterceptor implements HandlerInterceptor {
    private final UserService userService;
    private final SiteService siteService;
    private final ConfigService configService;
    private final DeviceResolver deviceResolver;

    public FrontendInterceptor(UserService userService, SiteService siteService, ConfigService configService,
                               DeviceResolver deviceResolver) {
        this.userService = userService;
        this.siteService = siteService;
        this.configService = configService;
        this.deviceResolver = deviceResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 当前用户
        Optional.ofNullable(Contexts.findCurrentUsername()).map(userService::selectByUsername)
                .ifPresent(Contexts::setCurrentUser);
        // 访问设备
        Device device = deviceResolver.resolveDevice(request);
        Contexts.setMobile(device.isMobile());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           @Nullable ModelAndView modelAndView) {
        Site site = Contexts.findCurrentSite();
        Config config = configService.getUnique();
        if (site == null) {
            // 未找到站点，一般是因为抛出了异常，设置 templateUrl 让错误页面可以正确访问css、js等资源
            request.setAttribute(Frontends.TEMPLATE_URL, config.getTemplateStorage().getUrl());
            return;
        }
        Site defaultSite = siteService.getDefaultSite(config.getDefaultSiteId());
        Frontends.setData(request, defaultSite);
        Frontends.setUser(request, Contexts.findCurrentUser());
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        Contexts.clearCurrentUser();
        Contexts.clearCurrentSite();
        Contexts.clearMobile();
    }
}
