package com.ujcms.core.support;

import com.ujcms.core.domain.Site;
import com.ujcms.core.domain.User;
import com.ofwise.util.web.exception.Http401Exception;
import com.ujcms.core.service.UserService;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.lang.Nullable;

import java.util.Optional;

/**
 * 上下文数据工具类
 *
 * @author PONY
 */
public final class Contexts {
    @Nullable
    public static Integer findCurrentUserId() {
        Subject subject = SecurityUtils.getSubject();
        Object principal = subject.getPrincipal();
        boolean validPrincipal = principal instanceof Integer && (subject.isRemembered() || subject.isAuthenticated());
        if (validPrincipal) {
            return (Integer) principal;
        }
        return null;
    }

    public static Integer getCurrentUserId() {
        return Optional.ofNullable(findCurrentUserId()).orElseThrow(Http401Exception::new);
    }

    @Nullable
    public static User findCurrentUser(UserService userService) {
        return Optional.ofNullable(findCurrentUserId()).map(userService::select).orElse(null);
    }

    public static User getCurrentUser(UserService userService) {
        return Optional.ofNullable(findCurrentUser(userService)).orElseThrow(Http401Exception::new);
    }

    /**
     * 站点线程变量
     */
    private static ThreadLocal<Site> siteHolder = new ThreadLocal<>();

    @Nullable
    public static Site findCurrentSite() {
        return siteHolder.get();
    }

    public static Site getCurrentSite() {
        return Optional.ofNullable(findCurrentSite()).orElseThrow(() -> new IllegalStateException("Current site not found"));
    }

    public static void setCurrentSite(Site site) {
        siteHolder.set(site);
    }

    public static void clearCurrentSite() {
        siteHolder.remove();
    }

    public static Integer getCurrentSiteId() {
        return getCurrentSite().getId();
    }

    /**
     * 是否手机访问线程变量
     */
    private static ThreadLocal<Boolean> isMobileHolder = new ThreadLocal<>();

    public static void setMobile(boolean isMobile) {
        isMobileHolder.set(isMobile);
    }

    public static boolean isMobile() {
        return isMobileHolder.get() != null && isMobileHolder.get();
    }

    public static void clearMobile() {
        isMobileHolder.remove();
    }

    /**
     * 工具类不需要生成实例
     */
    private Contexts() {
    }
}
