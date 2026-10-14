package com.ujcms.cms.core.security;

import com.ujcms.cms.core.support.StaticProps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 拒绝默认密钥（Phase 5 / G8）：关闭时不校验；开启时默认/空的 data-secret-key 或 password-pepper 抛出，
 * 二者皆为自定义值时通过。
 */
class DefaultKeyGuardTest {

    private static final String CUSTOM_KEY = "Abcdef0123456789";              // 16 chars
    private static final String CUSTOM_PEPPER = "a-very-long-custom-random-pepper-value-32+";

    @Test
    void disabled_neverThrows() {
        assertDoesNotThrow(() -> DefaultKeyGuard.check(false,
                StaticProps.DEFAULT_DATA_SECRET_KEY, StaticProps.DEFAULT_PASSWORD_PEPPER));
        assertDoesNotThrow(() -> DefaultKeyGuard.check(false, "", ""));
    }

    @Test
    void enabled_rejectsDefaultOrBlankDataSecretKey() {
        assertThrows(IllegalStateException.class, () -> DefaultKeyGuard.check(true,
                StaticProps.DEFAULT_DATA_SECRET_KEY, CUSTOM_PEPPER));
        assertThrows(IllegalStateException.class, () -> DefaultKeyGuard.check(true, "  ", CUSTOM_PEPPER));
    }

    @Test
    void enabled_rejectsDefaultOrBlankPepper() {
        assertThrows(IllegalStateException.class, () -> DefaultKeyGuard.check(true,
                CUSTOM_KEY, StaticProps.DEFAULT_PASSWORD_PEPPER));
        assertThrows(IllegalStateException.class, () -> DefaultKeyGuard.check(true, CUSTOM_KEY, null));
        assertThrows(IllegalStateException.class, () -> DefaultKeyGuard.check(true, CUSTOM_KEY, ""));
    }

    @Test
    void enabled_passesWhenBothCustom() {
        assertDoesNotThrow(() -> DefaultKeyGuard.check(true, CUSTOM_KEY, CUSTOM_PEPPER));
    }
}
