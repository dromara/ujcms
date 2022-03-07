package com.ujcms.core.web.support;

import com.ujcms.core.support.Contexts;
import com.ujcms.core.support.Frontends;
import com.ujcms.core.support.Props;
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
    private Props props;

    public FrontendInterceptor(DeviceResolver deviceResolver, Props props) {
        this.deviceResolver = deviceResolver;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 访问设备
        Device device = deviceResolver.resolveDevice(request);
        Contexts.setMobile(device.isMobile());
        request.setAttribute(Frontends.TEMPLATE_URL, props.getTemplateUrl());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           @Nullable ModelAndView modelAndView) {
        if (Contexts.findCurrentSite() != null) {
            Frontends.setData(request);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        Contexts.clearMobile();
        Contexts.clearCurrentSite();
    }
}
