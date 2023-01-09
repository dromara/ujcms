package com.ujcms.util.web;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.N;
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
    public static String bbcode(@Nullable String text) {
        if (text == null) {
            return null;
        }
        int len = text.length();
        StringBuilder html = new StringBuilder((int) (len * 1.1));
        char ch;
        for (int i = 0; i < len; i++) {
            ch = text.charAt(i);
            switch (ch) {
                case ' ': {
                    if (i > 0 && html.charAt(html.length() - 1) == ' ') {
                        html.append("&nbsp;");
                    } else {
                        html.append(' ');
                    }
                    break;
                }
                case '\r': {
                    break;
                }
                case '\n': {
                    html.append("<br>");
                    break;
                }
                case '<': {
                    html.append("&lt;");
                    break;
                }
                case '>': {
                    html.append("&gt;");
                    break;
                }
                case '&': {
                    html.append("&amp;");
                    break;
                }
                case '"': {
                    html.append("&quot;");
                    break;
                }
                case '©': {
                    html.append("&copy;");
                    break;
                }
                case '®': {
                    html.append("&reg;");
                    break;
                }
                default: {
                    html.append(ch);
                }
            }
        }
        return html.toString();
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

    public static String encodeUrlBase64(final byte[] data) {
        return new String(Base64.getUrlEncoder().encode(data), StandardCharsets.UTF_8);
    }

    public static byte[] decodeBase64(final String data) {
        return Base64.getDecoder().decode(data.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] decodeUrlBase64(final String data) {
        return Base64.getUrlDecoder().decode(data.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] toBytes(String source) {
        return source.getBytes(StandardCharsets.UTF_8);
    }

    public static String toString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String formatDuration(int duration) {
        int hours = duration / 3600;
        int minutes = (duration - hours * 3600) / 60;
        int seconds = duration - hours * 3600 - minutes * 60;
        StringBuilder buffer = new StringBuilder();
        if (hours > 0) {
            buffer.append(hours).append(":");
        }
        // 两位数
        int ten = 10;
        if (minutes < ten) {
            buffer.append("0").append(minutes).append(":");
        } else {
            buffer.append(minutes).append(":");
        }
        if (seconds < ten) {
            buffer.append("0").append(seconds);
        } else {
            buffer.append(seconds);
        }
        return buffer.toString();
    }

    /**
     * 工具类不需要实例化
     */
    private Strings() {
    }
}
