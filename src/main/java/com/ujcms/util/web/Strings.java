package com.ujcms.util.web;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 字符串工具类
 *
 * @author PONY
 */
public class Strings {
    /**
     * 字符串截断。编码大于127的字符作为占两个位置，否则占一个位置。
     */
    public static String substring(@Nullable String text, int length, @Nullable String append) {
        if (text == null) {
            return "";
        }
        int textLength = text.length();
        if (textLength < length) {
            return text;
        }
        // 编码大于127的字符作为占两个位置
        int doubleSpaceChar = 127, doubleLength = length * 2;
        int actualLength = 0, i = 0;
        for (; i < textLength && actualLength < doubleLength; i++) {
            if (text.charAt(i) > doubleSpaceChar) {
                actualLength += 2;
            } else {
                actualLength += 1;
            }
        }
        int minLength = 2;
        if (i > minLength && i + 1 < textLength && StringUtils.isNotBlank(append)) {
            if (text.charAt(i) > doubleSpaceChar || text.charAt(i - 1) > doubleSpaceChar) {
                return StringUtils.substring(text, 0, i - 1) + append;
            }
            return StringUtils.substring(text, 0, i - 2) + append;
        }
        return StringUtils.substring(text, 0, i);
    }

    public static String substring(String text, int length) {
        return substring(text, length, null);
    }

    @Nullable
    public static String htmlEscape(@Nullable String input) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        return HtmlUtils.htmlEscape(input);
    }

    @Nullable
    public static String htmlUnescape(@Nullable String input) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        return HtmlUtils.htmlUnescape(input);
    }

    public static String encodeHex(final byte[] data) {
        return Hex.encodeHexString(data);
    }

    public static byte[] decodeHex(final String data) {
        try {
            return Hex.decodeHex(data);
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
    }

    public static String encodeBase64(final byte[] data) {
        return new String(Base64.getEncoder().encode(data), StandardCharsets.UTF_8);
    }

    public static byte[] decodeBase64(final String data) {
        return Base64.getDecoder().decode(data.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] toBytes(String source) {
        return source.getBytes(StandardCharsets.UTF_8);
    }

    public static String toString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 工具类不需要实例化
     */
    private Strings() {
    }
}
