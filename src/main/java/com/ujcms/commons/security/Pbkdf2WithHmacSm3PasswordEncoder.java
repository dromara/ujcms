package com.ujcms.commons.security;

import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.util.EncodingUtils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 编码方式由默认的 hex 改为 base64，缩短密码的存储长度
 *
 * @author PONY
 */
public class Pbkdf2WithHmacSm3PasswordEncoder implements PasswordEncoder {
    private static final int DEFAULT_SALT_LENGTH = 8;

    private static final int DEFAULT_HASH_WIDTH = 256;

    /**
     * 加密迭代次数。默认为 185000。考虑到云服务器的性能可能不是很好，改为 8192 次。
     */
    private static final int DEFAULT_ITERATIONS = 8192;

    private final BytesKeyGenerator saltGenerator;

    private final byte[] secret;

    private final int hashWidth;

    private final int iterations;

    /**
     * 默认改为 base64 编码
     */
    private boolean encodeHashAsBase64 = true;

    public Pbkdf2WithHmacSm3PasswordEncoder() {
        this("");
    }

    public Pbkdf2WithHmacSm3PasswordEncoder(CharSequence secret) {
        this(secret, DEFAULT_SALT_LENGTH, DEFAULT_ITERATIONS, DEFAULT_HASH_WIDTH);
    }

    public Pbkdf2WithHmacSm3PasswordEncoder(CharSequence secret, int saltLength) {
        this(secret, saltLength, DEFAULT_ITERATIONS, DEFAULT_HASH_WIDTH);
    }

    public Pbkdf2WithHmacSm3PasswordEncoder(CharSequence secret, int iterations, int hashWidth) {
        this(secret, DEFAULT_SALT_LENGTH, iterations, hashWidth);
    }

    public Pbkdf2WithHmacSm3PasswordEncoder(CharSequence secret, int saltLength, int iterations, int hashWidth) {
        this.secret = Utf8.encode(secret);
        this.saltGenerator = KeyGenerators.secureRandom(saltLength);
        this.iterations = iterations;
        this.hashWidth = hashWidth;
    }

    public void setEncodeHashAsBase64(boolean encodeHashAsBase64) {
        this.encodeHashAsBase64 = encodeHashAsBase64;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        byte[] salt = this.saltGenerator.generateKey();
        byte[] encoded = encode(rawPassword, salt);
        return encode(encoded);
    }

    private String encode(byte[] bytes) {
        if (this.encodeHashAsBase64) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return String.valueOf(Hex.encode(bytes));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        byte[] digested = decode(encodedPassword);
        byte[] salt = EncodingUtils.subArray(digested, 0, this.saltGenerator.getKeyLength());
        return MessageDigest.isEqual(digested, encode(rawPassword, salt));
    }

    private byte[] decode(String encodedBytes) {
        if (this.encodeHashAsBase64) {
            return Base64.getDecoder().decode(encodedBytes);
        }
        return Hex.decode(encodedBytes);
    }

    private byte[] encode(CharSequence rawPassword, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toString().toCharArray(),
                    EncodingUtils.concatenate(salt, this.secret), this.iterations, this.hashWidth);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM, Secures.PROVIDER);
            return EncodingUtils.concatenate(salt, skf.generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not create hash", ex);
        }
    }

    public static final String ALGORITHM = "PBKDF2WithHmacSM3";
}
