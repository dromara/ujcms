package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.web.exception.Http401Exception;
import com.ujcms.common.web.exception.Http403Exception;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;

import static com.ujcms.cms.core.support.Constants.DEMO_USER_ID;

/**
 * 上下文拦截器
 *
 * @author PONY
 */
public class BackendInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(BackendInterceptor.class);

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
        // 后台接口默认拒绝（backend-authorization §2）：过滤链对 /api/** 整体放行，动作级授权只靠 @PreAuthorize，
        // 漏写注解的处理方法不得落在授权之外。放在登录校验之后（匿名请求一律 401，不因注解与否而异）、
        // 上下文装配之前（抛出不留现场）；处理器身份只进日志，响应体不带内部类名。
        if (!isSecured(handler)) {
            logger.error("Backend handler without @PreAuthorize rejected: {}", handler);
            throw new Http403Exception("Backend handler is not secured by @PreAuthorize");
        }
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

    /**
     * 处理方法或其所在类是否声明了 {@link PreAuthorize}。查找走完整类型层次（父类、接口、元注解），与
     * Spring Security 解析该注解的策略一致，不然接口上声明的注解会被这里误判。非 {@code @Controller}
     * 的处理器（CORS 预检、框架自动应答 OPTIONS 的 HttpOptionsHandler、静态资源等）不承载动作语义，
     * 不在本检查范围内；Spring 也只在 {@code @Controller} 类上发现处理方法。
     */
    static boolean isSecured(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || !AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), Controller.class)) {
            return true;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), PreAuthorize.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), PreAuthorize.class) != null;
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
