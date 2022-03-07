package com.ofwise.util.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

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

    /**
     * 工具类不需要实例化
     */
    private Strings() {
    }
}
