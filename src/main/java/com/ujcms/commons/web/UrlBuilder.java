package com.ujcms.commons.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * URL 辅助类
 *
 * @author PONY
 */
public class UrlBuilder implements CharSequence {
    public static final String SLASH = "/";
    public static final String DOUBLE_SLASH = "//";
    public static final String HTTP_PROTOCOL = "http:";
    public static final String HTTPS_PROTOCOL = "https:";
    private final StringBuilder buff = new StringBuilder();

    private UrlBuilder() {
    }

    private UrlBuilder(String s) {
        append(s);
    }

    public static UrlBuilder of() {
        return new UrlBuilder();
    }

    public static UrlBuilder of(String s) {
        return new UrlBuilder(s);
    }

    public UrlBuilder appendProtocol(@Nullable String protocol) {
        if (StringUtils.isNotBlank(protocol)) {
            append(protocol).append(":");
        }
        return this;
    }

    public UrlBuilder appendDomain(@Nullable String domain) {
        if (StringUtils.isNotBlank(domain)) {
            append("//").append(domain);
        }
        return this;
    }

    public UrlBuilder appendPort(@Nullable Integer port) {
        if (port != null) {
            append(":").append(port);
        }
        return this;
    }

    public UrlBuilder appendPath(@Nullable String path) {
        if (StringUtils.isNotBlank(path)) {
            // 为支持不需要 / 开头的路径，先要判断当前路径是否为空
            if (StringUtils.isNotBlank(this) && !StringUtils.endsWith(this, SLASH)
                    && !StringUtils.startsWith(path, SLASH)) {
                // 没有 / 则补上
                append(SLASH);
            } else if (StringUtils.endsWith(this, SLASH) && StringUtils.startsWith(path, SLASH)) {
                // 前后都有 / 则删除
                buff.deleteCharAt(buff.length() - 1);
            }
            append(path);
        }
        return this;
    }

    public UrlBuilder append(@Nullable String s) {
        if (StringUtils.isNotBlank(s)) {
            // 不允许使用 .. 退到上级目录
            buff.append(StringUtils.remove(s, ".."));
        }
        return this;
    }

    public UrlBuilder append(Object obj) {
        return append(String.valueOf(obj));
    }


    @Override
    public int length() {
        return buff.length();
    }

    @Override
    public char charAt(int index) {
        return buff.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return buff.subSequence(start, end);
    }

    @Override
    public String toString() {
        return buff.toString();
    }
}
