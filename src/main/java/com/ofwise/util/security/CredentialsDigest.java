package com.ofwise.util.security;

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
    String digest(String plainCredentials, byte[] saltBytes);

    /**
     * 摘要加密
     *
     * @param plainCredentials 原始密码
     * @param salt             盐（字符串格式）
     * @return 摘要加密密码
     */
    default String digest(String plainCredentials, String salt) {
        return digest(plainCredentials, CodecSupport.toBytes(salt));
    }

    /**
     * 判断密码是否匹配
     *
     * @param credentials      摘要加密密码
     * @param plainCredentials 原始密码
     * @param saltBytes        盐（字节数组格式）
     * @return 密码是否匹配
     */
    boolean matches(String credentials, String plainCredentials, byte[] saltBytes);

    /**
     * 判断密码是否匹配
     *
     * @param credentials      摘要加密密码
     * @param plainCredentials 原始密码
     * @param salt             盐（字符串格式）
     * @return 密码是否匹配
     */
    default boolean matches(String credentials, String plainCredentials, String salt) {
        return matches(credentials, plainCredentials, CodecSupport.toBytes(salt));
    }
}
