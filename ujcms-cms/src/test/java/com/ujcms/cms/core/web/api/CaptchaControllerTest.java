package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.component.CaptchaGuard;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.captcha.CaptchaToken;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.captcha.IpLoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CaptchaController}：类型经 {@link CaptchaGuard} 解析并带上访客 IP；类型探测；预检的空值；is-display 的两种语义。
 */
class CaptchaControllerTest {
    private CaptchaTokenService service;
    private CaptchaGuard guard;
    private IpLoginAttemptService ipLoginAttemptService;
    private CaptchaController controller;

    @BeforeEach
    void setUp() {
        service = mock(CaptchaTokenService.class);
        guard = mock(CaptchaGuard.class);
        ipLoginAttemptService = mock(IpLoginAttemptService.class);
        ConfigService configService = mock(ConfigService.class);
        Config config = new Config();
        Config.Security security = new Config.Security();
        security.setIpCaptchaAttempts(3);
        config.setSecurity(security);
        when(configService.getUnique()).thenReturn(config);
        controller = new CaptchaController(configService, ipLoginAttemptService, service, guard, mock(Props.class));
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.4");
        return request;
    }

    @Test
    void mintsTheResolvedTypeWithTheVisitorIp() {
        when(guard.resolveType("char")).thenReturn("char");
        CaptchaToken token = new CaptchaToken("char", "t", 900, Map.of("image", "x"));
        when(service.getCaptchaToken("char", "198.51.100.4")).thenReturn(token);
        assertSame(token, controller.captchaToken("char", request()));
    }

    @Test
    void typeIsTheConfiguredTypeAfterFallback() {
        when(guard.configuredType()).thenReturn("tencent");
        assertEquals("tencent", controller.type().type());
        verifyNoInteractions(service);
    }

    @Test
    void tryRefusesBlankInput() {
        assertFalse(controller.tryCaptcha(null, "a"));
        assertFalse(controller.tryCaptcha("t", " "));
        verifyNoInteractions(service);
        when(service.tryCaptcha("t", "a")).thenReturn(true);
        assertTrue(controller.tryCaptcha("t", "a"));
    }

    @Test
    void isDisplayWithoutScopeIsTheLoginStepUp() {
        when(ipLoginAttemptService.isExcessive("198.51.100.4", 3)).thenReturn(true);
        assertTrue(controller.isDisplayCaptcha(null, request()));
        assertTrue(controller.isDisplayCaptcha("", request()));
        verifyNoInteractions(guard);
    }

    @Test
    void isDisplayWithScopeAsksTheGuard() {
        MockHttpServletRequest request = request();
        when(guard.isVisibleRequired(request, "comment")).thenReturn(false);
        assertFalse(controller.isDisplayCaptcha("comment", request));
        when(guard.isVisibleRequired(request, "comment")).thenReturn(true);
        assertTrue(controller.isDisplayCaptcha("comment", request));
        when(ipLoginAttemptService.isExcessive(anyString(), eqAny())).thenReturn(true);
        assertEquals(true, controller.isDisplayCaptcha("comment", request), "scope wins over the login counter");
    }

    private static int eqAny() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
