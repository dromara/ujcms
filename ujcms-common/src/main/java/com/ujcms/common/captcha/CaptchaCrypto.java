package com.ujcms.common.captcha;

import com.ujcms.common.security.Secures;
import com.ujcms.common.web.Strings;
import org.jspecify.annotations.Nullable;

import java.security.MessageDigest;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 验证码答案的保护：由 {@code captcha.secret} 派生的 HMAC-SM3 与 SM4-GCM。
 * <p>
 * 令牌声明会随令牌发给浏览器，答案不能明文出现：能用比对的答案（字符）取 HMAC，服务端需要原值做容差比较的答案
 * （滑块偏移量）用 SM4-GCM 加密。SM4 密钥取密钥的 SM3 摘要前 16 字节，与 {@code captcha.secret} 的长度无关。
 *
 * @author PONY
 */
public final class CaptchaCrypto {
    private static final int SM4_KEY_LENGTH = 16;

    private final byte[] secret;
    private final byte[] sm4Key;

    public CaptchaCrypto(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("captcha secret must not be empty");
        }
        this.secret = secret.getBytes(UTF_8);
        this.sm4Key = Arrays.copyOf(Secures.sm3(this.secret), SM4_KEY_LENGTH);
    }

    /**
     * HMAC-SM3，URL 安全的 Base64 编码
     */
    public String hmac(String data) {
        return Strings.encodeUrlBase64(Secures.hmacSm3(secret, data.getBytes(UTF_8)));
    }

    /**
     * 恒定时间比较
     */
    public boolean hmacEquals(String data, @Nullable Object expected) {
        if (!(expected instanceof String s)) {
            return false;
        }
        return MessageDigest.isEqual(hmac(data).getBytes(UTF_8), s.getBytes(UTF_8));
    }

    /**
     * SM4-GCM 加密，Base64 编码（随机 IV，同一明文每次密文不同）
     */
    public String encrypt(String plain) {
        return Strings.encodeBase64(Secures.sm4Encrypt(plain.getBytes(UTF_8), sm4Key));
    }

    /**
     * SM4-GCM 解密。密文损坏、被篡改或不是字符串时返回 {@code null}
     */
    @Nullable
    public String decrypt(@Nullable Object cipher) {
        if (!(cipher instanceof String s) || s.isEmpty()) {
            return null;
        }
        try {
            return new String(Secures.sm4Decrypt(Strings.decodeBase64(s), sm4Key), UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
