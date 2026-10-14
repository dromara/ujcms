package com.ujcms.cms.core.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证 PII 规范化：写入与查询必须映射为同一规范形式，盲索引才能匹配。
 */
class PiiNormalizerTest {

    @Test
    void emailTrimsAndLowercases() {
        assertEquals("foo@bar.com", PiiNormalizer.normalizeEmail("  Foo@Bar.COM "));
        assertEquals("foo@bar.com", PiiNormalizer.normalizeEmail("foo@bar.com"));
    }

    @Test
    void emailVariantsConvergeToSameForm() {
        assertEquals(PiiNormalizer.normalizeEmail("USER@Example.com"),
                PiiNormalizer.normalizeEmail(" user@example.com "));
    }

    @Test
    void emailBlankBecomesNull() {
        assertNull(PiiNormalizer.normalizeEmail(null));
        assertNull(PiiNormalizer.normalizeEmail(""));
        assertNull(PiiNormalizer.normalizeEmail("   "));
    }

    @Test
    void mobileStripsSeparators() {
        assertEquals("13800001111", PiiNormalizer.normalizeMobile("138 0000 1111"));
        assertEquals("13800001111", PiiNormalizer.normalizeMobile("138-0000-1111"));
        assertEquals("13800001111", PiiNormalizer.normalizeMobile(" (138) 0000.1111 "));
    }

    @Test
    void mobileKeepsPlusCountryCode() {
        assertEquals("+8613800001111", PiiNormalizer.normalizeMobile("+86 138-0000-1111"));
    }

    @Test
    void mobileVariantsConvergeToSameForm() {
        assertEquals(PiiNormalizer.normalizeMobile("138 0000 1111"),
                PiiNormalizer.normalizeMobile("13800001111"));
    }

    @Test
    void mobileBlankBecomesNull() {
        assertNull(PiiNormalizer.normalizeMobile(null));
        assertNull(PiiNormalizer.normalizeMobile(""));
        assertNull(PiiNormalizer.normalizeMobile("   "));
        // 仅含分隔符，剔除后为空
        assertNull(PiiNormalizer.normalizeMobile(" - () . "));
    }
}
