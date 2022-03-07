package com.ujcms.core.web.support;

import com.ujcms.core.domain.Site;
import com.ofwise.util.web.exception.Http403Exception;
import com.ujcms.core.service.SiteQueryService;
import com.ujcms.core.support.Contexts;
import com.ujcms.core.support.Props;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;

/**
 * 上下文拦截器
 *
 * @author PONY
 */
public class BackendInterceptor implements HandlerInterceptor {
    public BackendInterceptor(SiteQueryService siteService, Props props) {
        this.siteService = siteService;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1、从 header 里获取站点的信息（放到sessionStorage里面，以免被其它标签页修改）
        // 2、从 域名获取站点信息（可以考虑）
        // 3、获取默认站点（可考虑允许用户自行设置默认站点？）
        Site site = Optional.ofNullable(request.getHeader(BACKEND_SITE_HEADER)).map(Integer::valueOf).map(siteService::select).orElse(null);
        if (site == null) {
            List<Site> siteList = siteService.selectList(null, null, null, 1);
            if (!siteList.isEmpty()) {
                site = siteList.get(0);
            }
        }
        if (site == null) {
            throw new IllegalStateException("Site not found!");
        }
        Contexts.setCurrentSite(site);
        // 演示站的演示用户只能执行 GET 请求
        if (props.isDemo()) {
            Integer demoId = 10;
            String allowedMethod = "GET";
            if (demoId.equals(Contexts.findCurrentUserId()) && !allowedMethod.equalsIgnoreCase(request.getMethod())) {
                throw new Http403Exception("error.demoUserForbidden");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        Contexts.clearCurrentSite();
    }

    private SiteQueryService siteService;
    private Props props;
    /**
     * 后台站点 Header 名称
     */
    public static final String BACKEND_SITE_HEADER = "ujcms-site-id";
}
