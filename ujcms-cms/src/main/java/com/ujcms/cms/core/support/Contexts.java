package com.ujcms.cms.core.support;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.common.web.exception.Http401Exception;
import org.jspecify.annotations.Nullable;
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
     * 用户线程变量。
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
     * 站点线程变量。
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
     * 是否手机访问线程变量。
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
     * 插槽预览模式线程变量（页面构成 §11.2）。前台请求带 {@code _preview=draft} 且为已认证管理员时置位：
     * slot 指令据此让草稿可见并发出 {@code data-ujcms-slot} 编辑深链锚点。
     */
    private static final ThreadLocal<Boolean> SLOT_PREVIEW_HOLDER = new ThreadLocal<>();

    public static void setSlotPreview(boolean slotPreview) {
        SLOT_PREVIEW_HOLDER.set(slotPreview);
    }

    public static boolean isSlotPreview() {
        return SLOT_PREVIEW_HOLDER.get() != null && SLOT_PREVIEW_HOLDER.get();
    }

    public static void clearSlotPreview() {
        SLOT_PREVIEW_HOLDER.remove();
    }

    /**
     * 主题预览覆盖线程变量（design-review P2-1 / H1）。前台请求带 {@code ?_theme=<主题名>} 且携带有效
     * 预览身份（{@code _ptoken} 预览令牌或真实登录态）时置位：{@link com.ujcms.cms.core.domain.Site#getTheme()}
     * / {@code getMobileTheme()} 优先返回该值，使模板组装、区域清单解析、主题设置模型等全部渲染面
     * 以候选主题解析——激活前的真实渲染试看。只读渲染覆盖，绝不写库；
     * {@code FrontendInterceptor} 置位/清除，{@code SiteResolver} 在站点确定后校验其可激活性。
     */
    private static final ThreadLocal<String> THEME_PREVIEW_HOLDER = new ThreadLocal<>();

    /** 主题预览覆盖参数名（前台 URL {@code ?_theme=<主题名>}，与 {@code _preview}/{@code _ptoken} 同族）。 */
    public static final String THEME_PREVIEW_PARAM = "_theme";

    public static void setThemePreview(String themeName) {
        THEME_PREVIEW_HOLDER.set(themeName);
    }

    @Nullable
    public static String findThemePreview() {
        return THEME_PREVIEW_HOLDER.get();
    }

    public static void clearThemePreview() {
        THEME_PREVIEW_HOLDER.remove();
    }

    /**
     * 工具类不需要生成实例
     */
    private Contexts() {
    }
}
