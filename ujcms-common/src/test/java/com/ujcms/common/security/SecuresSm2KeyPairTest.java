package com.ujcms.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SM2 QD 密钥对的形制：公钥 04||X||Y（65 字节）、私钥 D 定长 32 字节，HEX 长度恒为 130 / 64——
 * 不随 BigInteger 符号字节或前导零波动，且每一对都能完成加解密往返。
 */
class SecuresSm2KeyPairTest {

    @Test
    void qdKeyPairHasFixedHexLength_andRoundTrips() {
        // 约一半的 D 值最高位为 1，未定长前会带出 33 字节；抽样 64 次足以覆盖
        for (int i = 0; i < 64; i++) {
            Secures.Pair pair = Secures.generateSm2QdKeyPair();
            assertEquals(130, pair.getPublicKey().length(), pair.getPublicKey());
            assertEquals(64, pair.getPrivateKey().length(), pair.getPrivateKey());
            String cipher = Secures.sm2Encrypt("ujcms", pair.getPublicKey());
            assertEquals("ujcms", Secures.sm2Decrypt(cipher, pair.getPrivateKey()));
        }
    }
}
