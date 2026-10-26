package com.ujcms.common.captcha;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PowCaptchaProvider} 的单元测试（docs/spec/captcha/plan.md Policy 4）：难度边界、前导零计数、nonce 规则。
 */
class PowCaptchaProviderTest {

    @Test
    void difficultyIsClamped() {
        assertEquals(PowCaptchaProvider.MIN_DIFFICULTY, new PowCaptchaProvider(() -> 1).difficulty());
        assertEquals(PowCaptchaProvider.MAX_DIFFICULTY, new PowCaptchaProvider(() -> 99).difficulty());
        assertEquals(18, new PowCaptchaProvider(() -> 18).difficulty());
        PowCaptchaProvider provider = new PowCaptchaProvider(() -> 12);
        provider.setDifficulty(() -> 20);
        assertEquals(20, provider.difficulty());
        assertFalse(provider.visible());
        assertEquals("pow", provider.type());
    }

    @Test
    void countsLeadingZeroBits() {
        assertEquals(0, PowCaptchaProvider.leadingZeroBits(new byte[]{(byte) 0x80, 0}));
        assertEquals(7, PowCaptchaProvider.leadingZeroBits(new byte[]{1, 0}));
        assertEquals(8, PowCaptchaProvider.leadingZeroBits(new byte[]{0, (byte) 0xFF}));
        assertEquals(12, PowCaptchaProvider.leadingZeroBits(new byte[]{0, 0x0F}));
        assertEquals(16, PowCaptchaProvider.leadingZeroBits(new byte[]{0, 0}));
    }

    @Test
    void challengeAndVerifyRoundTrip() {
        PowCaptchaProvider provider = new PowCaptchaProvider(() -> 10);
        CaptchaChallenge challenge = provider.challenge();
        String salt = (String) challenge.payload().get("salt");
        assertEquals(10, challenge.payload().get("difficulty"));
        assertEquals(salt, challenge.claims().get(PowCaptchaProvider.CLAIM_SALT));

        // 暴力找 nonce：难度 10 平均约千次
        String nonce = null;
        for (int i = 0; i < 1_000_000; i++) {
            String candidate = Integer.toString(i, 36);
            if (PowCaptchaProvider.leadingZeroBits(PowCaptchaProvider.sha256(salt + ':' + candidate)) >= 10) {
                nonce = candidate;
                break;
            }
        }
        assertTrue(nonce != null, "a nonce must exist");
        assertTrue(provider.verify(challenge.claims(), nonce));
        assertFalse(provider.verify(challenge.claims(), nonce + "x"), "a different nonce almost surely fails");
        assertFalse(provider.verify(challenge.claims(), "not a nonce!"));
        assertFalse(provider.verify(challenge.claims(), "a".repeat(PowCaptchaProvider.MAX_NONCE_LENGTH + 1)));
        assertFalse(provider.verify(Map.of(), nonce));
        // 声明里的难度被改大后，原 nonce 不再满足
        assertFalse(provider.verify(Map.of(PowCaptchaProvider.CLAIM_SALT, salt,
                PowCaptchaProvider.CLAIM_DIFFICULTY, 24), nonce));
    }
}
