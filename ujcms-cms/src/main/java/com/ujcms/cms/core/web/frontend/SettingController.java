package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.FrontendController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 会员设置 Controller
 *
 * @author PONY
 */
@FrontendController("frontendSettingController")
@RequestMapping("/settings")
public class SettingController {
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
    public String settings(Site site) {
        return site.assembleTemplate(SETTINGS_HOME_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/comment")
    public String comment(Site site) {
        return site.assembleTemplate(SETTINGS_COMMENT_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/profile")
    public String profile(Site site) {
        return site.assembleTemplate(SETTINGS_PROFILE_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/avatar")
    public String avatar(Site site) {
        return site.assembleTemplate(SETTINGS_AVATAR_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/account")
    public String account(Site site) {
        return site.assembleTemplate(SETTINGS_ACCOUNT_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/favorite")
    public String favorite(Site site) {
        return site.assembleTemplate(SETTINGS_FAVORITE_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/history")
    public String history(Site site) {
        return site.assembleTemplate(SETTINGS_HISTORY_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/follow")
    public String follow(Site site) {
        return site.assembleTemplate(SETTINGS_FOLLOW_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/friend")
    public String friend(Site site) {
        return site.assembleTemplate(SETTINGS_FRIEND_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/message")
    public String message(Site site) {
        return site.assembleTemplate(SETTINGS_MESSAGE_TEMPLATE);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/notification")
    public String notification(Site site) {
        return site.assembleTemplate(SETTINGS_NOTIFICATION_TEMPLATE);
    }
}
