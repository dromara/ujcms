package com.ofwise.util.security;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.Base64;

/**
 * Hash 证书加密
 *
 * @author liufang
 */
public abstract class AbstractHashCredentialsDigest implements CredentialsDigest {
    /**
     * 将明文密码和salt，加密成string
     *
     * @param plainCredentials 明文密码
     * @param salt             盐
     */
    @Override
    public String digest(String plainCredentials, byte[] salt) {
        byte[] hashPassword = digestToBytes(plainCredentials, salt);
        return Base64.getEncoder().encodeToString(hashPassword);
    }

    /**
     * 判断明文密码与加密密码是否匹配
     *
     * @param credentials      加密密码
     * @param plainCredentials 明文密码
     * @param salt             盐
     */
    @Override
    public boolean matches(String credentials, String plainCredentials, byte[] salt) {
        return StringUtils.equals(credentials, digest(plainCredentials, salt));
    }

    /**
     * 加密方法，由子类提供实现，返回byte[]
     *
     * @param plainCredentials 明文密码
     * @param salt             盐
     * @return 加密结果
     */
    protected abstract byte[] digestToBytes(String plainCredentials, byte[] salt);
}
