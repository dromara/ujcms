package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.StaticProps;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.support.UrlConstants.API;

/**
 * 环境 Controller
 *
 * @author PONY
 */
@RestController
@RequestMapping(API + "/env")
public class EnvController {
    private SiteService siteService;
    private UserService userService;
    private ConfigService configService;
    private Props props;

    public EnvController(SiteService siteService, UserService userService, ConfigService configService, Props props) {
        this.siteService = siteService;
        this.userService = userService;
        this.configService = configService;
        this.props = props;
    }

    /**
     * 获得当前登录用户
     */
    @GetMapping("/current-user")
    public Map<String, Object> currentUser() {
        User user = Optional.ofNullable(Contexts.findCurrentUser(userService)).filter(User::isNormal).orElse(null);
        if (user == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>(16);
        result.put("username", user.getUsername());
        result.put("avatar", user.getAvatar());
        result.put("rank", user.getRank());
        result.put("permissions", user.getPermissions());
        result.put("grantPermissions", user.getGrantPermissions());
        result.put("globalPermission", user.hasGlobalPermission());
        result.put("loginDate", user.getLoginDate());
        result.put("loginIp", user.getLoginIp());
        result.put("epExcludes", StaticProps.getEpExcludes());
        result.put("epDisplay", StaticProps.isEpDisplay());
        result.put("epRank", StaticProps.getEpRank());
        result.put("epActivated", StaticProps.isEpActivated());
        return result;
    }

    @GetMapping("/current-site-list")
    public List<Site> currentSiteList() {
        User user = Contexts.findCurrentUser(userService);
        if (user == null) {
            return Collections.emptyList();
        }
        return siteService.listByOrgId(user.getOrgId());
    }

    @GetMapping("/client-public-key")
    public String clientPublicKey() {
        return props.getClientSm2PublicKey();
    }

    @GetMapping("/config")
    public Config config() {
        return configService.getUnique();
    }

    @GetMapping("/is-mfa-login")
    public boolean isDisplayCaptcha() {
        Config.Security security = configService.getUnique().getSecurity();
        return security.isTwoFactor();
    }
}
