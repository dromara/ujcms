package com.ujcms.util.security;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Hash 证书加密
 *
 * @author liufang
 */
public abstract class AbstractHashCredentialsDigest implements CredentialsDigest {
    /**
     * @see CredentialsDigest#digest(String, byte[])
     */
    @Override
    public String digest(String plainCredentials, @Nullable byte[] salt) {
        byte[] hashPassword = digestToBytes(plainCredentials, salt);
        return new String(Base64.getEncoder().encode(hashPassword), StandardCharsets.UTF_8);
    }

    /**
     * @see CredentialsDigest#digest(String, byte[], String)
     */
    @Override
    public String digest(String plainCredentials, @Nullable byte[] salt, @Nullable String pepper) {
        byte[] hashPassword = digestToBytes(plainCredentials, salt, pepper);
        return new String(Base64.getEncoder().encode(hashPassword), StandardCharsets.UTF_8);
    }

    /**
     * @see CredentialsDigest#matches(String, String, byte[])
     */
    @Override
    public boolean matches(String credentials, String plainCredentials, @Nullable byte[] salt) {
        return StringUtils.equals(credentials, digest(plainCredentials, salt));
    }

    /**
     * @see CredentialsDigest#matches(String, String, byte[], String)
     */
    @Override
    public boolean matches(String credentials, String plainCredentials, @Nullable byte[] salt, @Nullable String pepper) {
        return StringUtils.equals(credentials, digest(plainCredentials, salt, pepper));
    }

    /**
     * 加密方法，由子类提供实现，返回byte[]
     *
     * @param plainCredentials 明文密码
     * @param salt             盐
     * @return 加密结果
     */
    protected abstract byte[] digestToBytes(String plainCredentials, @Nullable byte[] salt);

    /**
     * 加密方法，由子类提供实现，返回byte[]
     *
     * @param plainCredentials 明文密码
     * @param salt             盐
     * @param pepper           胡椒
     * @return 加密结果
     */
    protected abstract byte[] digestToBytes(String plainCredentials, @Nullable byte[] salt, @Nullable String pepper);
}
