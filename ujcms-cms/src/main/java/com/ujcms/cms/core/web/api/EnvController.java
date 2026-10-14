package com.ujcms.cms.core.web.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.UserExt;
import com.ujcms.cms.core.security.TwoFactorGuard;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.InstallService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Commercial;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.web.Views;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
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
    private final InstallService installService;
    private final TwoFactorGuard twoFactorGuard;
    private final Props props;

    public EnvController(SiteService siteService, ConfigService configService, InstallService installService,
                         TwoFactorGuard twoFactorGuard, Props props) {
        this.twoFactorGuard = twoFactorGuard;
        this.siteService = siteService;
        this.configService = configService;
        this.installService = installService;
        this.props = props;
    }

    /**
     * 管理后台启动状态：当前登录用户身份 + 权限位 + 数据范围 + 商业版级别
     * 等管理端所需的整体上下文。
     * <p>
     * 注意：本接口面向 Vue 管理控制台 ({@code ujcms-ui-console})，
     * 不是公网会员侧用来获取登录用户资料的接口。会员资料请使用
     * {@code GET /api/user/me}（{@link UserController.MeUser}）。原有的
     * URL {@code /api/env/current-user} 仅返回了少量用户字段，名字容易让
     * 调用者误以为是通用的当前用户接口；2026-04-29 重命名为
     * {@code /api/env/backend-state} 以避免混淆。
     */
    @GetMapping("/backend-state")
    public Object backendState() {
        User user = Optional.ofNullable(Contexts.findCurrentUser()).filter(User::isEnabled).orElse(null);
        if (user == null) {
            // 匿名亦须暴露安装状态（install-flow §3）：控制台据 installed=false 渲染安装页
            // 而非登录页。username 保持缺省，不影响既有"未登录"判定。
            return new AnonymousStateResult(installService.isInstalled());
        }
        return new BackendStateResult(
                installService.isInstalled(),
                props.getDatabaseType(),
                user.getUsername(),
                user.getAvatar(),
                user.getRank(),
                user.getPermissions(),
                user.getGrantPermissions(),
                user.hasGlobalPermission(),
                user.hasAllChannelPermission(),
                user.hasAllArticlePermission(),
                user.hasAllStatusPermission(),
                user.getDataScope(),
                user.getLoginDate(),
                user.getLoginIp(),
                props.isDataMigrationEnabled(),
                Commercial.getLockedPermissions(),
                Commercial.getTier(),
                // 控制台偏好（侧栏风格、主题色、首页设置）随启动状态下发，外壳首屏即按偏好绘制（docs/spec/console-shell）；
                // 外观两项为空即跟随随行下发的控制台默认外观（全局设置 › 基础设置，D7），合成由控制台完成
                Optional.ofNullable(user.getExt()).map(UserExt::getPreferences).orElseGet(UserExt.Preferences::new),
                configService.getUnique().getConsole());
    }

    /**
     * 匿名（未登录）时的后台状态：仅暴露安装状态。{@code installed=false} 时控制台渲染
     * 安装向导（install-flow D2）。
     */
    public record AnonymousStateResult(boolean installed) {}

    public record BackendStateResult(
            boolean installed,
            String databaseType,
            String username,
            @Nullable String avatar,
            Short rank,
            List<String> permissions,
            List<String> grantPermissions,
            boolean globalPermission,
            boolean allChannelPermission,
            boolean allArticlePermission,
            boolean allStatusPermission,
            short dataScope,
            @Nullable OffsetDateTime loginDate,
            @Nullable String loginIp,
            boolean dataMigrationEnabled,
            List<String> lockedPermissions,
            int commercialTier,
            UserExt.Preferences preferences,
            Config.Console console) {}

    /**
     * 获取当前站点列表
     */
    @GetMapping("/current-site-list")
    @JsonView(Views.List.class)
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
     * 获取Config配置（公开子集）。
     * <p>
     * 该接口为匿名访问，使用<strong>白名单</strong>方式显式构造返回字段，仅返回模板渲染
     * 与会员注册/登录页面所需的内容。新增到 {@link Config} 的字段默认<strong>不会</strong>
     * 出现在这里——必须在 {@link ConfigResult} 中显式声明才会暴露，从根本上杜绝
     * “新增字段默认泄露”。
     * <p>
     * 管理员专用的运维策略（{@code security.ssrfWhiteList}、密码使用期限、用户/IP 登录
     * 尝试阈值、上传扩展名黑名单等）不在此返回；管理控制台登录后通过
     * {@code GET /backend/core/config} 单独拉取以补全 {@code sysConfigStore}。
     * 凭据类配置（短信/邮件/对象存储密钥）由 {@link Config} 的 {@code @JsonIgnore} 隔离，
     * 同样不会到达此处。完整字段清单见 docs/spec/template-spec/view-models/ConfigView.md。
     */
    @GetMapping("/config")
    public ConfigResult config() {
        Config config = configService.getUnique();
        Config.Security security = config.getSecurity();
        return new ConfigResult(
                config.getId(),
                config.getContextPath(),
                config.getPort(),
                config.getMultiDomain(),
                config.getChannelUrl(),
                config.getArticleUrl(),
                config.getTopicUrl(),
                config.getDefaultSiteId(),
                config.getUpload(),
                config.isGreyStyle(),
                config.getRegister(),
                new ConfigResult.PublicSecurity(
                        security.getPasswordMinLength(),
                        security.getPasswordMaxLength(),
                        security.getPasswordStrength(),
                        security.getPasswordPattern(),
                        twoFactorGuard.isEnabled(security)),
                config.getCustoms(),
                config.getCommercialTier(),
                config.getUploadUrlPrefix(),
                // 控制台默认外观（侧栏风格、主题色，无机密）：登录页在登录前即按安装默认着色（console-shell D7）
                config.getConsole(),
                // 可用的第三方登录服务商代码（无凭据）：前后端分离的登录页据此显示第三方登录入口
                config.getOauthProviders());
    }

    /**
     * 匿名 {@code /env/config} 的公开投影。<strong>白名单</strong>：只有在此显式声明的字段
     * 才会暴露给匿名调用者。
     * <p>
     * 刻意排除：{@code sms}/{@code email}/{@code *Storage}/{@code ai} 凭据、{@code grey} 原始对象
     * （仅保留计算属性 {@code greyStyle}）、{@code uploadsExtensionBlacklist}/
     * {@code filesExtensionBlacklist} 上传黑名单、以及 {@code security} 中的限流/密码
     * 有效期/SSRF 白名单等运维字段（仅保留密码复杂度公开子集）。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConfigResult(
            Long id,
            @Nullable String contextPath,
            @Nullable Integer port,
            Boolean multiDomain,
            @Nullable String channelUrl,
            @Nullable String articleUrl,
            @Nullable String topicUrl,
            Long defaultSiteId,
            Config.Upload upload,
            boolean greyStyle,
            Config.Register register,
            PublicSecurity security,
            Map<String, Object> customs,
            int commercialTier,
            String uploadUrlPrefix,
            Config.Console console,
            List<String> oauthProviders) {

        /**
         * 密码复杂度公开子集。登录/注册/改密页面用于前端校验提示；服务端始终独立校验，
         * 故公开这些不构成风险。运维类阈值（限流、密码有效期、SSRF）不在此。
         */
        public record PublicSecurity(
                int passwordMinLength,
                int passwordMaxLength,
                int passwordStrength,
                String passwordPattern,
                boolean twoFactor) {
        }
    }

    /**
     * csrf 参数
     */
    @GetMapping("/csrf")
    public CsrfToken csrf(HttpServletRequest request) {
        return (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    }
}
