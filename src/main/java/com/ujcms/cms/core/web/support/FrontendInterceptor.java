package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Frontends;
import org.springframework.lang.Nullable;
import org.springframework.mobile.device.Device;
import org.springframework.mobile.device.DeviceResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 前台拦截器
 *
 * @author PONY
 */
public class FrontendInterceptor implements HandlerInterceptor {
    private DeviceResolver deviceResolver;
    private ConfigService configService;

    public FrontendInterceptor(DeviceResolver deviceResolver, ConfigService configService) {
        this.deviceResolver = deviceResolver;
        this.configService = configService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 访问设备
        Device device = deviceResolver.resolveDevice(request);
        Contexts.setMobile(device.isMobile());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           @Nullable ModelAndView modelAndView) {
        if (Contexts.findCurrentSite() == null) {
            // 未找到站点，一般是因为抛出了异常，设置 templateUrl 让错误页面可以正确访问css、js等资源
            Config config = configService.getUnique();
            request.setAttribute(Frontends.TEMPLATE_URL, config.getTemplateStorage().getUrl());
            return;
        }
        Frontends.setData(request);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        Contexts.clearMobile();
        Contexts.clearCurrentSite();
    }
}
