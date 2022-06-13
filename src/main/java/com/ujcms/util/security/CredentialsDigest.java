package com.ujcms.util.security;

import org.apache.shiro.codec.CodecSupport;
import org.springframework.lang.Nullable;

/**
 * 证书加密
 *
 * @author liufang
 */
public interface CredentialsDigest {
    /**
     * 摘要加密
     *
     * @param plainCredentials 原始密码
     * @param saltBytes        盐（字节数组格式）
     * @return 摘要加密结果
     */
    String digest(String plainCredentials, @Nullable byte[] saltBytes);

    /**
     * 摘要加密
     *
     * @param plainCredentials 原始密码
     * @param saltBytes        盐（字节数组格式）
     * @param pepper           胡椒
     * @return 摘要加密结果
     */
    String digest(String plainCredentials, @Nullable byte[] saltBytes, @Nullable String pepper);

    /**
     * 摘要加密
     *
     * @param plainCredentials 原始密码
     * @param salt             盐（字符串格式）
     * @return 摘要加密密码
     */
    default String digest(String plainCredentials, @Nullable String salt) {
        return digest(plainCredentials, salt != null ? CodecSupport.toBytes(salt) : null);
    }

    /**
     * 摘要加密
     *
     * @param plainCredentials 原始密码
     * @param salt             盐（字符串格式）
     * @param pepper           胡椒
     * @return 摘要加密密码
     */
    default String digest(String plainCredentials, @Nullable String salt, @Nullable String pepper) {
        return digest(plainCredentials, salt != null ? CodecSupport.toBytes(salt) : null, pepper);
    }

    /**
     * 判断密码是否匹配
     *
     * @param credentials      摘要加密密码
     * @param plainCredentials 原始密码
     * @param saltBytes        盐（字节数组格式）
     * @return 密码是否匹配
     */
    boolean matches(String credentials, String plainCredentials, @Nullable byte[] saltBytes);

    /**
     * 判断密码是否匹配
     *
     * @param credentials      摘要加密密码
     * @param plainCredentials 原始密码
     * @param saltBytes        盐（字节数组格式）
     * @param pepper           胡椒
     * @return 密码是否匹配
     */
    boolean matches(String credentials, String plainCredentials, @Nullable byte[] saltBytes, @Nullable String pepper);

    /**
     * 判断密码是否匹配
     *
     * @param credentials      摘要加密密码
     * @param plainCredentials 原始密码
     * @param salt             盐（字符串格式）
     * @return 密码是否匹配
     */
    default boolean matches(String credentials, String plainCredentials, @Nullable String salt) {
        return matches(credentials, plainCredentials, salt != null ? CodecSupport.toBytes(salt) : null);
    }

    /**
     * 判断密码是否匹配
     *
     * @param credentials      摘要加密密码
     * @param plainCredentials 原始密码
     * @param salt             盐（字符串格式）
     * @return 密码是否匹配
     */
    default boolean matches(String credentials, String plainCredentials, @Nullable String salt, String pepper) {
        return matches(credentials, plainCredentials, salt != null ? CodecSupport.toBytes(salt) : null, pepper);
    }
}
