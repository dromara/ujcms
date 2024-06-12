package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.web.exception.Http401Exception;
import com.ujcms.commons.web.exception.Http403Exception;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

import static com.ujcms.cms.core.support.Constants.DEMO_USER_ID;

/**
 * 上下文拦截器
 *
 * @author PONY
 */
public class BackendInterceptor implements HandlerInterceptor {
    public BackendInterceptor(UserService userService, SiteService siteService, OrgService orgService, Props props) {
        this.userService = userService;
        this.siteService = siteService;
        this.orgService = orgService;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1、从 header 里获取站点的信息（放到sessionStorage里面，以免被其它标签页修改）
        // 2、从 域名获取站点信息（客户端无法得到当前站点的信息，不可行）
        // 3、获取默认站点（可考虑允许用户自行设置默认站点？设计稍显复杂，但基本可行）

        User user = Optional.ofNullable(userService.selectByUsername(Contexts.getCurrentUsername()))
                .orElseThrow(Http401Exception::new);
        Contexts.setCurrentUser(user);

        Site site = Optional.ofNullable(request.getHeader(BACKEND_SITE_HEADER)).map(Long::valueOf)
                .map(siteService::select).orElse(null);
        // 检测是否有站点权限
        if (site != null && !orgService.hasRelationship(user.getOrgId(), site.getOrgId())) {
            throw new SiteForbiddenException("error.siteForbidden", site.getName(), String.valueOf(site.getId()));
        }
        if (site == null) {
            site = siteService.findFirstByUserIdAndOrgId(user.getId(), user.getOrgId());
        }
        if (site == null) {
            throw new Http403Exception("error.noRelationSite", user.getOrg().getName(), String.valueOf(user.getOrg()));
        }

        Contexts.setCurrentSite(site);
        // 演示站的演示用户只能执行 GET 请求
        if (props.isDemo()) {
            String allowedMethod = "GET";
            if (DEMO_USER_ID == user.getId() && !allowedMethod.equalsIgnoreCase(request.getMethod())) {
                throw new Http403Exception("error.demoUserForbidden");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        Contexts.clearCurrentUser();
        Contexts.clearCurrentSite();
        Contexts.clearMobile();
    }

    private final UserService userService;
    private final SiteService siteService;
    private final OrgService orgService;
    private final Props props;
    /**
     * 后台站点 Header 名称
     */
    public static final String BACKEND_SITE_HEADER = "ujcms-site-id";
}
