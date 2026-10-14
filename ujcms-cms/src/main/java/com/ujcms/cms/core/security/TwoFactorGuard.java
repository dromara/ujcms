package com.ujcms.cms.core.security;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.SecurityProperties;
import com.ujcms.common.web.exception.LogicException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 双因子认证的防锁死守卫。
 * <p>
 * 双因子认证一旦开启，登录就依赖“短信能发出去”和“账号登记了正确的手机号码”。任何一环缺失，受影响的人都进不了后台；
 * 若连管理员自己也进不去，就无人能在后台纠正。本组件在<b>开启之前</b>拦住这类配置，并提供进不去之后的应急出口：
 * <ul>
 *   <li>{@link #requireReady}：开启双因子前校验短信服务已配置、操作者本人已登记手机号码；</li>
 *   <li>{@link #requireConsistent}：开启双因子时必须同时开启用户登录失败锁定，否则验证码可被无限次猜测；</li>
 *   <li>{@link #isEnabled}：应急开关 {@code ujcms.security.disable-two-factor} 生效时，视为未开启双因子。</li>
 * </ul>
 *
 * @author PONY
 */
@Component
public class TwoFactorGuard implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(TwoFactorGuard.class);

    private final SecurityProperties securityProperties;
    private final UserService userService;

    public TwoFactorGuard(SecurityProperties securityProperties, UserService userService) {
        this.securityProperties = securityProperties;
        this.userService = userService;
    }

    /**
     * 启动时提示应急开关处于打开状态。只读配置、不修改任何状态。
     */
    @Override
    public void afterPropertiesSet() {
        if (securityProperties.isDisableTwoFactor()) {
            logger.warn("ujcms.security.disable-two-factor 已开启：双因子认证被停用，登录只校验口令。"
                    + "该开关仅用于应急恢复（短信服务故障、管理员手机号码有误等导致无人能登录），问题解决后请立即关闭并重启。");
        }
    }

    /**
     * 双因子认证是否实际生效：后台已开启，且未被应急开关停用
     */
    public boolean isEnabled(Config.Security security) {
        return security.isTwoFactor() && !securityProperties.isDisableTwoFactor();
    }

    /**
     * 后台已开启双因子认证，但被应急开关停用
     */
    public boolean isBypassed(Config.Security security) {
        return security.isTwoFactor() && securityProperties.isDisableTwoFactor();
    }

    /**
     * 检查开启双因子认证的就绪情况
     *
     * @param security 即将保存的安全设置
     * @param sms      当前的短信设置
     * @param actor    操作者
     */
    public Readiness check(Config.Security security, Config.Sms sms, User actor) {
        return new Readiness(
                sms.getProvider() != Config.Sms.PROVIDER_NONE,
                PiiNormalizer.normalizeMobile(actor.getMobile()) != null,
                security.getUserMaxAttempts() > 0,
                userService.countBackendUsersWithoutMobile());
    }

    /**
     * 由“未开启”改为“开启”双因子认证时调用。短信服务未配置、或操作者本人未登记手机号码时拒绝：
     * 前者会让所有人无法登录，后者会让操作者退出后再也进不来、无法在后台纠正。
     * 其他后台用户未登记手机号码不拒绝（只影响其本人，管理员可随后补登），由前台以数量提示。
     */
    public void requireReady(Config.Security security, Config.Sms sms, User actor) {
        Readiness readiness = check(security, sms, actor);
        if (!readiness.smsConfigured()) {
            throw new LogicException("error.twoFactorRequiresSms");
        }
        if (!readiness.actorHasMobile()) {
            throw new LogicException("error.twoFactorRequiresOwnMobile");
        }
    }

    /**
     * 保存安全设置时调用。开启双因子认证必须同时开启用户登录失败锁定（最大尝试次数大于 0）：
     * 验证码输错计入用户登录失败次数，但该次数只有在开启锁定时才起作用；否则六位验证码可被无限次猜测。
     */
    public void requireConsistent(Config.Security security) {
        if (security.isTwoFactor() && security.getUserMaxAttempts() <= 0) {
            throw new LogicException("error.twoFactorRequiresUserMaxAttempts");
        }
    }

    /**
     * 开启双因子认证的就绪情况
     *
     * @param smsConfigured       短信服务是否已配置
     * @param actorHasMobile      操作者本人是否已登记手机号码
     * @param userMaxAttemptsSet  是否已开启用户登录失败锁定
     * @param usersWithoutMobile  尚未登记手机号码的后台用户数量。开启后这些用户将无法登录
     */
    public record Readiness(boolean smsConfigured, boolean actorHasMobile, boolean userMaxAttemptsSet,
                            long usersWithoutMobile) {
        public boolean ready() {
            return smsConfigured && actorHasMobile && userMaxAttemptsSet;
        }
    }
}
