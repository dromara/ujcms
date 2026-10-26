package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.captcha.CaptchaProvider;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.captcha.IpSubmitCache;
import com.ujcms.common.captcha.IpSubmitCounterService;
import com.ujcms.common.captcha.PowCaptchaProvider;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link CaptchaGuard} 的策略测试（docs/spec/captcha/plan.md Policy 2、5）：静默直至升级、蜜罐、类型策略、难度接入。
 */
class CaptchaGuardTest {
    private static final String IP = "203.0.113.7";

    /**
     * Map 实现的提交计数缓存：没有 Spring 缓存代理时注解不生效
     */
    static final class MapIpSubmitCache extends IpSubmitCache {
        private final Map<String, Integer> map = new HashMap<>();

        @Override
        public int getCount(String key) {
            return map.getOrDefault(key, 0);
        }

        @Override
        public int updateCount(String key, int count) {
            map.put(key, count);
            return count;
        }
    }

    private CaptchaTokenService tokenService;
    private Config config;
    private CaptchaGuard guard;
    private PowCaptchaProvider pow;

    @BeforeEach
    void setUp() {
        tokenService = mock(CaptchaTokenService.class);
        when(tokenService.supports("slider")).thenReturn(true);
        when(tokenService.supports("char")).thenReturn(true);
        when(tokenService.supports("pow")).thenReturn(true);
        config = new Config();
        Config.Captcha captcha = new Config.Captcha();
        captcha.setSilentSubmits(2);
        captcha.setPowDifficulty(12);
        config.setCaptcha(captcha);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(config);
        Props props = mock(Props.class);
        pow = new PowCaptchaProvider(() -> 18);
        guard = new CaptchaGuard(tokenService, new IpSubmitCounterService(new MapIpSubmitCache()), configService,
                props, pow);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(IP);
        return request;
    }

    @Test
    void bindsProofOfWorkDifficultyToSettings() {
        assertEquals(12, pow.difficulty(), "difficulty comes from the global settings, not the property default");
        config.getCaptcha().setPowDifficulty(20);
        assertEquals(20, pow.difficulty());
    }

    @Test
    void honeypotRejectsBeforeAnyTokenIsRead() {
        assertEquals(CaptchaGuard.ERROR_INCORRECT,
                guard.verify(request(), CaptchaGuard.SCOPE_COMMENT, "t", "a", "http://spam.example"));
    }

    @Test
    void silentUntilThresholdThenVisible() {
        when(tokenService.validate(eq("pow-token"), eq("nonce"), eq(true))).thenReturn(CaptchaTokenService.Verdict.OK);
        when(tokenService.validate(eq("pow-token"), eq("nonce"), eq(false)))
                .thenReturn(CaptchaTokenService.Verdict.VISIBLE_REQUIRED);
        assertFalse(guard.isVisibleRequired(request(), CaptchaGuard.SCOPE_COMMENT));
        assertNull(guard.verify(request(), CaptchaGuard.SCOPE_COMMENT, "pow-token", "nonce", null));
        assertNull(guard.verify(request(), CaptchaGuard.SCOPE_COMMENT, "pow-token", "nonce", ""));
        // 第三次：同一 IP 已被接受两次，进入可见阶段
        assertTrue(guard.isVisibleRequired(request(), CaptchaGuard.SCOPE_COMMENT));
        assertEquals(CaptchaGuard.ERROR_REQUIRED,
                guard.verify(request(), CaptchaGuard.SCOPE_COMMENT, "pow-token", "nonce", null));
        // 其它场景互不影响
        assertFalse(guard.isVisibleRequired(request(), CaptchaGuard.SCOPE_MESSAGE_BOARD));
        // 可见令牌照常通过
        when(tokenService.validate(eq("slider-token"), eq("120"), anyBoolean())).thenReturn(CaptchaTokenService.Verdict.OK);
        assertNull(guard.verify(request(), CaptchaGuard.SCOPE_COMMENT, "slider-token", "120", null));
    }

    @Test
    void zeroSilentSubmitsMeansAlwaysVisible() {
        config.getCaptcha().setSilentSubmits(0);
        when(tokenService.validate(any(), any(), eq(false))).thenReturn(CaptchaTokenService.Verdict.VISIBLE_REQUIRED);
        assertTrue(guard.isVisibleRequired(request(), CaptchaGuard.SCOPE_FORM));
        assertEquals(CaptchaGuard.ERROR_REQUIRED, guard.verify(request(), CaptchaGuard.SCOPE_FORM, "pow", "n", null));
    }

    @Test
    void incorrectAnswersDoNotCountAsSubmissions() {
        when(tokenService.validate(any(), any(), anyBoolean())).thenReturn(CaptchaTokenService.Verdict.INCORRECT);
        for (int i = 0; i < 5; i++) {
            assertEquals(CaptchaGuard.ERROR_INCORRECT, guard.verify(request(), CaptchaGuard.SCOPE_COMMENT, "t", "x", null));
        }
        assertFalse(guard.isVisibleRequired(request(), CaptchaGuard.SCOPE_COMMENT));
    }

    @Test
    void unknownScopeAlwaysRequiresVisible() {
        assertTrue(guard.isVisibleRequired(request(), "nope"));
    }

    @Test
    void typePolicy() {
        assertEquals("slider", guard.configuredType());
        assertEquals("slider", guard.resolveType(null));
        assertEquals("char", guard.resolveType("char"));
        assertEquals("pow", guard.resolveType("pow"));
        assertThrows(Http400Exception.class, () -> guard.resolveType("geetest"));
        assertThrows(Http400Exception.class, () -> guard.resolveType("<script>"));

        // 设置了未注册的厂商（社区版残留设置）：默认出题回落到滑块；显式点名未注册的类型仍是 400
        config.getCaptcha().setType("geetest");
        assertEquals("slider", guard.configuredType());
        assertEquals("slider", guard.resolveType(null));
        assertThrows(Http400Exception.class, () -> guard.resolveType("geetest"));

        // 厂商已注册：默认出厂商题，自托管类型仍可请求
        when(tokenService.supports("geetest")).thenReturn(true);
        assertEquals("geetest", guard.configuredType());
        assertEquals("geetest", guard.resolveType(null));
        assertEquals("char", guard.resolveType("char"));
    }

    @Test
    void cspSourcesFollowTheConfiguredProvider() {
        assertTrue(guard.cspSources().isEmpty());
        CaptchaProvider vendor = mock(CaptchaProvider.class);
        when(vendor.cspSources()).thenReturn(Map.of("script-src", List.of("https://challenges.cloudflare.com")));
        when(tokenService.supports("turnstile")).thenReturn(true);
        when(tokenService.provider("turnstile")).thenReturn(vendor);
        config.getCaptcha().setType("turnstile");
        assertEquals(List.of("https://challenges.cloudflare.com"), guard.cspSources().get("script-src"));
    }
}
