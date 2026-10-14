package com.ujcms.cms.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ujcms.cms.core.support.StaticProps;

/**
 * 验证敏感配置列的 SM4-GCM 整列加解密与透明迁移语义。
 */
class Sm4StringTypeHandlerTest {

    @BeforeEach
    void setKey() {
        StaticProps.setDataSecretKey("0123456789abcdef");
    }

    @AfterEach
    void restoreKey() {
        StaticProps.setDataSecretKey(StaticProps.DEFAULT_DATA_SECRET_KEY);
    }

    @Test
    void roundTripHidesPlaintext() {
        String plain = "{\"accessKeySecret\":\"AKS-机密-123\",\"secretKey\":\"sk_live_abcdef\"}";
        String enc = Sm4StringTypeHandler.encrypt(plain);

        assertTrue(enc.startsWith(Sm4StringTypeHandler.PREFIX), "密文需带 {sm4} 前缀");
        assertNotEquals(plain, enc);
        assertFalse(enc.contains("AKS-机密"), "明文不应出现在密文中");
        assertFalse(enc.contains("sk_live_abcdef"), "明文不应出现在密文中");
        assertEquals(plain, Sm4StringTypeHandler.decrypt(enc), "解密应还原明文");
    }

    @Test
    void encryptIsIdempotent() {
        String once = Sm4StringTypeHandler.encrypt("{\"a\":1}");
        assertEquals(once, Sm4StringTypeHandler.encrypt(once), "已加密值不应被二次加密");
    }

    @Test
    void legacyPlaintextReadsThrough() {
        // 升级前的历史明文（无 {sm4} 前缀）应原样返回，实现读取期透明兼容
        String legacy = "{\"host\":\"smtp.example.com\"}";
        assertEquals(legacy, Sm4StringTypeHandler.decrypt(legacy));
    }

    @Test
    void blankAndNullPassThrough() {
        assertEquals("", Sm4StringTypeHandler.encrypt(""));
        assertNull(Sm4StringTypeHandler.decrypt(null));
    }

    @Test
    void wrongKeyFailsLoud() {
        String enc = Sm4StringTypeHandler.encrypt("{\"x\":\"y\"}");
        StaticProps.setDataSecretKey("fedcba9876543210");
        assertThrows(IllegalStateException.class, () -> Sm4StringTypeHandler.decrypt(enc),
                "密钥变更后旧密文应解密失败并抛出明确异常");
    }
}
