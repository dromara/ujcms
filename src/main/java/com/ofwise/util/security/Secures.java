package com.ofwise.util.security;

import org.apache.commons.lang3.RandomStringUtils;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 安全工具类
 *
 * @author PONY
 */
public class Secures {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    /**
     * 16字节是salt最低的要求
     */
    private static final int NEXT_SALT_SIZE = 16;

    /**
     * 获取下一个salt字符串
     */
    public static String nextSalt() {
        byte[] bytes = new byte[NEXT_SALT_SIZE];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 所有字符随机数
     */
    public static String random(final int count) {
        return RandomStringUtils.random(count, 0, 0, false, false, null, SECURE_RANDOM);
    }

    /**
     * Ascii码随机数
     */
    public static String randomAscii(final int count) {
        return RandomStringUtils.random(count, 32, 127, false, false, null, SECURE_RANDOM);
    }

    /**
     * 字母随机数
     */
    public static String randomAlphabetic(final int count) {
        return RandomStringUtils.random(count, 0, 0, true, false, null, SECURE_RANDOM);
    }

    /**
     * 数字随机数
     */
    public static String randomNumeric(final int count) {
        return RandomStringUtils.random(count, 0, 0, false, true, null, SECURE_RANDOM);
    }

    /**
     * 字母数字随机数
     */
    public static String randomAlphanumeric(final int count) {
        return RandomStringUtils.random(count, 0, 0, true, true, null, SECURE_RANDOM);
    }
}
