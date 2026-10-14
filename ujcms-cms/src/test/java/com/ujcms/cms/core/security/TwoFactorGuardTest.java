package com.ujcms.cms.core.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.SecurityProperties;
import com.ujcms.common.web.exception.LogicException;

/**
 * Verifies the lockout guard around two-factor authentication. Once it is on, login depends on
 * "SMS can be sent" and "the account has the right mobile on file"; if the administrator is caught
 * by either, nobody can get into the console to fix it. The guard therefore refuses to <em>turn it
 * on</em> in such a state, and the break-glass property is the way back in when it happens anyway.
 */
class TwoFactorGuardTest {
    private SecurityProperties securityProperties;
    private UserService userService;
    private TwoFactorGuard guard;

    private Config.Security security;
    private Config.Sms sms;
    private User actor;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        userService = mock(UserService.class);
        guard = new TwoFactorGuard(securityProperties, userService);

        security = new Config.Security();
        security.setTwoFactor(true);
        security.setUserMaxAttempts(5);
        sms = new Config.Sms();
        sms.setProvider(Config.Sms.PROVIDER_ALIYUN);
        actor = new User();
        actor.setMobile("13800138000");
    }

    private String refusal(Runnable action) {
        return assertThrows(LogicException.class, action::run).getMessage();
    }

    @Test
    void readyWhenSmsIsConfiguredAndTheActorHasAMobile() {
        when(userService.countBackendUsersWithoutMobile()).thenReturn(3L);

        TwoFactorGuard.Readiness readiness = guard.check(security, sms, actor);

        assertTrue(readiness.ready());
        // 其他后台用户未登记手机号码不阻止开启，只作为数量提示
        assertEquals(3L, readiness.usersWithoutMobile());
        assertDoesNotThrow(() -> guard.requireReady(security, sms, actor));
    }

    @Test
    void refusesToEnableWithoutAnSmsProvider() {
        sms.setProvider(Config.Sms.PROVIDER_NONE);

        assertFalse(guard.check(security, sms, actor).ready());
        assertEquals("error.twoFactorRequiresSms", refusal(() -> guard.requireReady(security, sms, actor)));
    }

    @Test
    void refusesToEnableWhenTheActorHasNoMobile() {
        actor.setMobile(null);
        assertEquals("error.twoFactorRequiresOwnMobile", refusal(() -> guard.requireReady(security, sms, actor)));

        // 只有分隔符的号码规范化后为空，同样视为未登记
        actor.setMobile(" - ");
        assertEquals("error.twoFactorRequiresOwnMobile", refusal(() -> guard.requireReady(security, sms, actor)));
    }

    @Test
    void twoFactorRequiresUserLockoutSoCodesCannotBeGuessedForever() {
        security.setUserMaxAttempts(0);

        assertFalse(guard.check(security, sms, actor).ready());
        assertEquals("error.twoFactorRequiresUserMaxAttempts", refusal(() -> guard.requireConsistent(security)));

        // 未开启双因子时，不限制用户最大尝试次数
        security.setTwoFactor(false);
        assertDoesNotThrow(() -> guard.requireConsistent(security));
    }

    @Test
    void breakGlassPropertyMakesTwoFactorIneffectiveWithoutTouchingTheSetting() {
        assertTrue(guard.isEnabled(security));
        assertFalse(guard.isBypassed(security));

        securityProperties.setDisableTwoFactor(true);

        assertFalse(guard.isEnabled(security));
        assertTrue(guard.isBypassed(security));
        assertTrue(security.isTwoFactor(), "后台设置保持不变，去掉启动参数即恢复");
        assertDoesNotThrow(guard::afterPropertiesSet);
    }

    @Test
    void breakGlassIsNotABypassWhenTwoFactorIsOff() {
        security.setTwoFactor(false);
        securityProperties.setDisableTwoFactor(true);

        assertFalse(guard.isEnabled(security));
        assertFalse(guard.isBypassed(security));
    }
}
