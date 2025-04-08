package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.StaticProps;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 环境 Controller
 *
 * @author PONY
 */
@Tag(name = "环境接口")
@RestController
@RequestMapping({API + "/env", FRONTEND_API + "/env"})
public class EnvController {
    private final SiteService siteService;
    private final ConfigService configService;
    private final Props props;

    public EnvController(SiteService siteService, ConfigService configService, Props props) {
        this.siteService = siteService;
        this.configService = configService;
        this.props = props;
    }

    /**
     * 获得当前登录用户
     */
    @GetMapping("/current-user")
    public Map<String, Object> currentUser() {
        User user = Optional.ofNullable(Contexts.findCurrentUser()).filter(User::isEnabled).orElse(null);
        if (user == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new HashMap<>(16);
        result.put("databaseType", props.getDatabaseType());
        result.put("username", user.getUsername());
        result.put("avatar", user.getAvatar());
        result.put("rank", user.getRank());
        result.put("permissions", user.getPermissions());
        result.put("grantPermissions", user.getGrantPermissions());
        result.put("globalPermission", user.hasGlobalPermission());
        result.put("allChannelPermission", user.hasAllChannelPermission());
        result.put("allArticlePermission", user.hasAllArticlePermission());
        result.put("allStatusPermission", user.hasAllStatusPermission());
        result.put("dataScope", user.getDataScope());
        result.put("loginDate", user.getLoginDate());
        result.put("loginIp", user.getLoginIp());
        result.put("dataMigrationEnabled", props.isDataMigrationEnabled());
        result.put("epExcludes", StaticProps.getEpExcludes());
        result.put("epDisplay", StaticProps.isEpDisplay());
        result.put("epRank", StaticProps.getEpRank());
        result.put("epActivated", StaticProps.isEpActivated());
        return result;
    }

    /**
     * 获取当前站点列表
     */
    @GetMapping("/current-site-list")
    public List<Site> currentSiteList() {
        User user = Contexts.findCurrentUser();
        if (user == null) {
            return Collections.emptyList();
        }
        return siteService.listByUserIdAndOrgId(user.getId(), user.getOrgId());
    }

    /**
     * 获取客户端SM2加密 public key
     */
    @GetMapping("/client-public-key")
    public String clientPublicKey() {
        return props.getClientSm2PublicKey();
    }

    /**
     * 获取Config配置
     */
    @GetMapping("/config")
    public Config config() {
        return configService.getUnique();
    }

    /**
     * 是否开启双因子登录
     */
    @GetMapping("/is-mfa-login")
    public boolean isDisplayCaptcha() {
        Config.Security security = configService.getUnique().getSecurity();
        return security.isTwoFactor();
    }

    /**
     * csrf 参数
     */
    @GetMapping("/csrf")
    public CsrfToken csrf(HttpServletRequest request) {
        return (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    }
}
