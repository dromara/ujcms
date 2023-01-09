package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.SiteResolver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

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

    private static final String SETTINGS_PROFILE_TEMPLATE = "mem_settings_profile";
    private static final String SETTINGS_AVATAR_TEMPLATE = "mem_settings_avatar";
    private static final String SETTINGS_ACCOUNT_TEMPLATE = "mem_settings_account";

    /**
     * 重定向至 /settings/profile
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public String settings(@PathVariable(required = false) String subDir) {
        if (StringUtils.isNotBlank(subDir)) {
            return "redirect:/" + subDir + "/settings/profile";
        }
        return "redirect:/settings/profile";
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
}
