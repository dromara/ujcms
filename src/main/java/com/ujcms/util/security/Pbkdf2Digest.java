package com.ujcms.util.security;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.spec.InvalidKeySpecException;

/**
 * 现代加密算法<code>PBKDF2WithHmacSM3</code>
 *
 * @author PONY
 */
public class Pbkdf2Digest extends AbstractHashCredentialsDigest {
    public Pbkdf2Digest(String algorithm) {
        this(algorithm, null, null);
    }

    public Pbkdf2Digest(String algorithm, @Nullable String pepper) {
        this(algorithm, null, pepper);
    }

    public Pbkdf2Digest(String algorithm, @Nullable Provider provider) {
        this(algorithm, provider, null);
    }

    public Pbkdf2Digest(String algorithm, @Nullable Provider provider, @Nullable String pepper) {
        try {
            this.pepper = pepper;
            if (provider != null) {
                secretKeyFactory = SecretKeyFactory.getInstance(algorithm, Secures.PROVIDER);
            } else {
                secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see AbstractHashCredentialsDigest#digestToBytes(String, byte[])
     */
    @Override
    protected byte[] digestToBytes(String plainCredentials, @Nullable byte[] salt) {
        return digestToBytes(plainCredentials, salt, this.pepper);
    }

    /**
     * @see AbstractHashCredentialsDigest#digestToBytes(String, byte[], String)
     */
    @Override
    protected byte[] digestToBytes(String plainCredentials, @Nullable byte[] salt, @Nullable String pepper) {
        if (salt == null || salt.length <= 0) {
            salt = DEFAULT_SALT;
        }
        // 如果存在“胡椒”，则加在前面，不可加在后面。
        if (StringUtils.isNotBlank(pepper)) {
            plainCredentials = pepper + plainCredentials;
        }
        try {
            PBEKeySpec spec = new PBEKeySpec(plainCredentials.toCharArray(), salt, HASH_ITERATIONS, HASH_KEY_LENGTH);
            SecretKey key = secretKeyFactory.generateSecret(spec);
            return key.getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private String pepper;
    private SecretKeyFactory secretKeyFactory;

    /**
     * hash迭代次数
     */
    private static final int HASH_ITERATIONS = 128;
    /**
     * hash长度。256足够安全。
     */
    private static final int HASH_KEY_LENGTH = 256;
    /**
     * SALT必须存在，如不存在则用此默认SALT。
     */
    private static byte[] DEFAULT_SALT = "DEFAULT-SALT".getBytes(StandardCharsets.UTF_8);
}
