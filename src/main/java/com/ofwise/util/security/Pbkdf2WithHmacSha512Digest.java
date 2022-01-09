package com.ofwise.util.security;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * 现代加密算法<code>PBKDF2WithHmacSHA512</code>
 *
 * @author PONY
 */
public class Pbkdf2WithHmacSha512Digest extends AbstractHashCredentialsDigest {
    /**
     * hash迭代次数
     */
    private static final int HASH_ITERATIONS = 128;
    /**
     * hash长度。256足够安全。
     */
    private static final int HASH_KEY_LENGTH = 256;

    public Pbkdf2WithHmacSha512Digest() {
        try {
            secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see AbstractHashCredentialsDigest#digestToBytes(String, byte[])
     */
    @Override
    protected byte[] digestToBytes(String plainCredentials, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(plainCredentials.toCharArray(), salt, HASH_ITERATIONS, HASH_KEY_LENGTH);
            SecretKey key = secretKeyFactory.generateSecret(spec);
            return key.getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private SecretKeyFactory secretKeyFactory;
}
