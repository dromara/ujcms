package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.SiteResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 会员设置 Controller
 *
 * @author PONY
 */
@Controller("frontendSettingController")
@RequestMapping({"/settings", "/{subDir:[\\w-]+}/settings"})
public class SettingController {
    private final SiteResolver siteResolver;

    public SettingController(SiteResolver siteResolver) {
        this.siteResolver = siteResolver;
    }

    private static final String SETTINGS_HOME_TEMPLATE = "mem_settings_home";
    private static final String SETTINGS_PROFILE_TEMPLATE = "mem_settings_profile";
    private static final String SETTINGS_AVATAR_TEMPLATE = "mem_settings_avatar";
    private static final String SETTINGS_ACCOUNT_TEMPLATE = "mem_settings_account";
    private static final String SETTINGS_COMMENT_TEMPLATE = "mem_settings_comment";
    private static final String SETTINGS_FAVORITE_TEMPLATE = "mem_settings_favorite";
    private static final String SETTINGS_HISTORY_TEMPLATE = "mem_settings_history";
    private static final String SETTINGS_FOLLOW_TEMPLATE = "mem_settings_follow";
    private static final String SETTINGS_FRIEND_TEMPLATE = "mem_settings_friend";
    private static final String SETTINGS_MESSAGE_TEMPLATE = "mem_settings_message";
    private static final String SETTINGS_NOTIFICATION_TEMPLATE = "mem_settings_notification";

    /**
     * 个人中心首页（仪表盘）
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public String settings(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(SETTINGS_HOME_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/comment")
    public String comment(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(SETTINGS_COMMENT_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/profile")
    public String profile(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(SETTINGS_PROFILE_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/avatar")
    public String avatar(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(SETTINGS_AVATAR_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/account")
    public String account(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(SETTINGS_ACCOUNT_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/favorite")
    public String favorite(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(SETTINGS_FAVORITE_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/history")
    public String history(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(SETTINGS_HISTORY_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/follow")
    public String follow(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(SETTINGS_FOLLOW_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/friend")
    public String friend(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(SETTINGS_FRIEND_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/message")
    public String message(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(SETTINGS_MESSAGE_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/notification")
    public String notification(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(SETTINGS_NOTIFICATION_TEMPLATE);
    }
}
