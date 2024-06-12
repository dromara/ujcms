package com.ujcms.cms.core.support;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.commons.web.exception.Http401Exception;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 上下文数据工具类
 *
 * @author PONY
 */
public final class Contexts {
    @Nullable
    public static String findCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }
        return null;
    }

    public static String getCurrentUsername() {
        return Optional.ofNullable(findCurrentUsername()).orElseThrow(Http401Exception::new);
    }

    /**
     * 用户线程变量
     */
    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();

    @Nullable
    public static User findCurrentUser() {
        return USER_HOLDER.get();
    }

    public static User getCurrentUser() {
        return Optional.ofNullable(findCurrentUser()).orElseThrow(Http401Exception::new);
    }

    public static void setCurrentUser(User user) {
        USER_HOLDER.set(user);
    }

    public static void clearCurrentUser() {
        USER_HOLDER.remove();
    }

    /**
     * 站点线程变量
     */
    private static final ThreadLocal<Site> SITE_HOLDER = new ThreadLocal<>();

    @Nullable
    public static Site findCurrentSite() {
        return SITE_HOLDER.get();
    }

    public static Site getCurrentSite() {
        return Optional.ofNullable(findCurrentSite()).orElseThrow(() ->
                new IllegalStateException("Current site not found"));
    }

    public static void setCurrentSite(Site site) {
        SITE_HOLDER.set(site);
    }

    public static void clearCurrentSite() {
        SITE_HOLDER.remove();
    }

    public static Long getCurrentSiteId() {
        return getCurrentSite().getId();
    }

    /**
     * 是否手机访问线程变量
     */
    private static final ThreadLocal<Boolean> IS_MOBILE_HOLDER = new ThreadLocal<>();

    public static void setMobile(boolean isMobile) {
        IS_MOBILE_HOLDER.set(isMobile);
    }

    public static boolean isMobile() {
        return IS_MOBILE_HOLDER.get() != null && IS_MOBILE_HOLDER.get();
    }

    public static void clearMobile() {
        IS_MOBILE_HOLDER.remove();
    }

    /**
     * 工具类不需要生成实例
     */
    private Contexts() {
    }
}
