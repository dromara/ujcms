package com.ujcms.common.captcha;

import com.nimbusds.jose.KeyLengthException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CaptchaTokenService} 的策略测试（docs/spec/captcha/plan.md Policy 1）：按类型路由、静默令牌默认拒绝、
 * 一次性消费、尝试次数、预检不消费、令牌篡改。
 * <p>
 * 用一个答案固定的测试提供者代替图片渲染，字符与滑块提供者各有自己的测试。
 */
class CaptchaTokenServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    /**
     * 答案固定为 {@code ok} 的可见提供者
     */
    static final class FixedProvider implements CaptchaProvider {
        private final String type;
        private final boolean visible;
        private final boolean supportsTry;

        FixedProvider(String type, boolean visible, boolean supportsTry) {
            this.type = type;
            this.visible = visible;
            this.supportsTry = supportsTry;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public boolean visible() {
            return visible;
        }

        @Override
        public boolean supportsTry() {
            return supportsTry;
        }

        @Override
        public CaptchaChallenge challenge() {
            return new CaptchaChallenge(Map.of("k", "v"), Map.of("hint", type));
        }

        @Override
        public boolean verify(Map<String, Object> claims, String answer) {
            return "v".equals(claims.get("k")) && "ok".equals(answer);
        }
    }

    private CaptchaProperties properties;
    private MapCaptchaCache cache;
    private CaptchaTokenService service;

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @BeforeEach
    void setUp() throws KeyLengthException {
        properties = new CaptchaProperties();
        properties.setSecret(SECRET);
        properties.setMaxAttempts(3);
        cache = new MapCaptchaCache();
        service = new CaptchaTokenService(List.of(
                new FixedProvider("visible", true, true),
                new FixedProvider("silent", false, true),
                new FixedProvider("vendor", true, false)), cache, properties);
    }

    @Test
    void mintsTypedTokenWithPayload() {
        CaptchaToken token = service.getCaptchaToken("visible", "203.0.113.9");
        assertEquals("visible", token.getType());
        assertEquals("visible", token.getPayload().get("hint"));
        assertEquals(properties.getExpires() * 60, token.getExpiresIn());
        assertEquals("visible", service.tokenType(token.getToken()));
        assertTrue(service.types().containsAll(List.of("visible", "silent", "vendor")));
    }

    @Test
    void unknownTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.getCaptchaToken("nope", null));
        assertFalse(service.supports("nope"));
        assertNull(service.provider("nope"));
    }

    @Test
    void validateConsumesTheTokenOnce() {
        String token = service.getCaptchaToken("visible", null).getToken();
        assertTrue(service.validateCaptcha(token, "ok"));
        // 已消费：再对也不行
        assertFalse(service.validateCaptcha(token, "ok"));
    }

    @Test
    void wrongAnswerAlsoBurnsTheToken() {
        String token = service.getCaptchaToken("visible", null).getToken();
        assertFalse(service.validateCaptcha(token, "wrong"));
        assertFalse(service.validateCaptcha(token, "ok"));
    }

    @Test
    void tryDoesNotConsumeButCountsAttempts() {
        String token = service.getCaptchaToken("visible", null).getToken();
        assertFalse(service.tryCaptcha(token, "a"));
        assertFalse(service.tryCaptcha(token, "b"));
        assertTrue(service.tryCaptcha(token, "ok"), "a correct try is reported without consuming");
        assertTrue(service.validateCaptcha(token, "ok"), "the token is still valid after tries");
    }

    @Test
    void attemptsAreBounded() {
        String token = service.getCaptchaToken("visible", null).getToken();
        for (int i = 0; i < properties.getMaxAttempts(); i++) {
            assertFalse(service.tryCaptcha(token, "x" + i));
        }
        assertFalse(service.tryCaptcha(token, "ok"), "exhausted tokens fail even with the right answer");
        assertFalse(service.validateCaptcha(token, "ok"));
    }

    @Test
    void silentTokenIsRejectedUnlessAllowed() {
        String token = service.getCaptchaToken("silent", null).getToken();
        assertFalse(service.validateCaptcha(token, "ok"), "consumers that did not opt in keep a visible challenge");
        assertEquals(CaptchaTokenService.Verdict.VISIBLE_REQUIRED, service.validate(token, "ok", false));
        // 策略拒绝不消费令牌
        assertEquals(CaptchaTokenService.Verdict.OK, service.validate(token, "ok", true));
        assertEquals(CaptchaTokenService.Verdict.INCORRECT, service.validate(token, "ok", true));
    }

    @Test
    void vendorTypeDoesNotSupportTry() {
        String token = service.getCaptchaToken("vendor", null).getToken();
        assertFalse(service.tryCaptcha(token, "ok"));
        assertTrue(service.validateCaptcha(token, "ok"));
    }

    @Test
    void tamperedOrForeignTokensFail() throws KeyLengthException {
        String token = service.getCaptchaToken("visible", null).getToken();
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertFalse(service.validateCaptcha(tampered, "ok"));
        assertNull(service.tokenType(tampered));

        CaptchaProperties other = new CaptchaProperties();
        other.setSecret("ffffffffffffffffffffffffffffffff");
        CaptchaTokenService otherService = new CaptchaTokenService(
                List.of(new FixedProvider("visible", true, true)), new MapCaptchaCache(), other);
        assertFalse(otherService.validateCaptcha(token, "ok"), "signed with another secret");
        assertFalse(service.validateCaptcha(null, "ok"));
        assertFalse(service.validateCaptcha(token, " "));
    }

    @Test
    void duplicateProviderTypesAreRefused() {
        assertThrows(IllegalStateException.class, () -> new CaptchaTokenService(List.of(
                new FixedProvider("a", true, true), new FixedProvider("a", true, true)), cache, properties));
    }

    @Test
    void charProviderRoundTripIsCaseInsensitive() throws KeyLengthException {
        CaptchaCrypto crypto = new CaptchaCrypto(SECRET);
        CaptchaRenderer renderer = new CaptchaRenderer(120, 36, 30, 5, 20, 1, CaptchaRenderer.DEFAULT_ALPHABET,
                new Color(23, 67, 172), Color.WHITE);
        // 用可读答案的渲染结果：通过 render(word) 固定答案
        CharCaptchaProvider provider = new CharCaptchaProvider(renderer, crypto, 5);
        CaptchaChallenge challenge = provider.challenge();
        assertTrue(challenge.payload().containsKey("image"));
        // 从声明无法推出答案：只有 HMAC 与随机数
        assertEquals(2, challenge.claims().size());

        CaptchaRenderer.CaptchaImage fixed = renderer.render("ABC23");
        String nonce = "n1";
        Map<String, Object> claims = Map.of(CharCaptchaProvider.CLAIM_NONCE, nonce,
                CharCaptchaProvider.CLAIM_SUBJECT, crypto.hmac("ABC23|" + nonce));
        assertTrue(provider.verify(claims, "abc23"));
        assertTrue(provider.verify(claims, fixed.word()));
        assertFalse(provider.verify(claims, "abc2"));
        assertFalse(provider.verify(claims, "abc24"));
        assertFalse(provider.verify(Map.of(CharCaptchaProvider.CLAIM_SUBJECT, "x"), "abc23"));
    }
}
