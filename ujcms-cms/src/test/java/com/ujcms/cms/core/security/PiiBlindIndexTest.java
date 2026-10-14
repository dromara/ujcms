package com.ujcms.cms.core.security;

import com.ujcms.cms.core.support.StaticProps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 PII 盲索引：确定性、规范化等价、空值为 null、对原值与密钥敏感、长度/字符集合规。
 */
class PiiBlindIndexTest {

    @BeforeEach
    void setKey() {
        StaticProps.setDataSecretKey("0123456789abcdef");
    }

    @AfterEach
    void restoreKey() {
        StaticProps.setDataSecretKey(StaticProps.DEFAULT_DATA_SECRET_KEY);
    }

    @Test
    void deterministic() {
        assertEquals(PiiBlindIndex.mobileIndex("13800001111"), PiiBlindIndex.mobileIndex("13800001111"));
        assertEquals(PiiBlindIndex.emailIndex("user@example.com"), PiiBlindIndex.emailIndex("user@example.com"));
    }

    @Test
    void normalizedVariantsShareIndex() {
        // 不同书写、相同逻辑值 → 同一索引（依赖与 PiiNormalizer 一致的规范化）
        assertEquals(PiiBlindIndex.mobileIndex("138 0000 1111"), PiiBlindIndex.mobileIndex("138-0000-1111"));
        assertEquals(PiiBlindIndex.emailIndex(" USER@Example.com "), PiiBlindIndex.emailIndex("user@example.com"));
    }

    @Test
    void differentValuesDifferentIndex() {
        assertNotEquals(PiiBlindIndex.mobileIndex("13800001111"), PiiBlindIndex.mobileIndex("13800002222"));
        assertNotEquals(PiiBlindIndex.emailIndex("a@example.com"), PiiBlindIndex.emailIndex("b@example.com"));
    }

    @Test
    void blankValueIndexesToNull() {
        assertNull(PiiBlindIndex.mobileIndex(null));
        assertNull(PiiBlindIndex.mobileIndex(""));
        assertNull(PiiBlindIndex.mobileIndex("   "));
        assertNull(PiiBlindIndex.emailIndex(null));
        assertNull(PiiBlindIndex.emailIndex(""));
        assertNull(PiiBlindIndex.emailIndex("  "));
    }

    @Test
    void indexHidesPlaintextAndIsHex64() {
        String mobile = "13800001111";
        String index = PiiBlindIndex.mobileIndex(mobile);
        assertNotEquals(mobile, index);
        assertEquals(64, index.length(), "HMAC-SM3 输出 32 字节 = 64 个十六进制字符");
        assertTrue(index.matches("[0-9a-f]{64}"), "应为小写十六进制");
    }

    @Test
    void indexDependsOnKey() {
        String withKeyA = PiiBlindIndex.mobileIndex("13800001111");
        StaticProps.setDataSecretKey("fedcba9876543210");
        String withKeyB = PiiBlindIndex.mobileIndex("13800001111");
        assertNotEquals(withKeyA, withKeyB, "更换 data-secret-key 后同一值的索引应不同（密钥派生）");
    }
}
