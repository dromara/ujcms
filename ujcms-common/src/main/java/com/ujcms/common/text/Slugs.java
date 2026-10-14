package com.ujcms.common.text;

import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * URL 别名（slug）工具。确定性、离线、永不抛异常：
 * NFKD 规范化 → 去除组合符号（重音等）→ 小写（ROOT）→ 非 {@code [a-z0-9]} 连续段折叠为单个连字符 →
 * 去首尾连字符 → 按上限截断（尽量在连字符处）。CJK 等无 ASCII 对应的字符直接丢弃（拼音转写由调用方按站点设置决定），
 * 因此纯中文标题得到空串，由调用方回退（ID 字符串等）。
 *
 * @author PONY
 */
public final class Slugs {
    /**
     * 默认长度上限（与文章 slug_ 列宽一致）
     */
    public static final int DEFAULT_MAX_LENGTH = 160;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DASHES = Pattern.compile("^-+|-+$");
    /**
     * 与实体 {@code @Pattern(regexp = "^[\\w-]*$")} 一致
     */
    private static final Pattern VALID = Pattern.compile("^[\\w-]*$");

    private Slugs() {
    }

    public static String slugify(@Nullable String text) {
        return slugify(text, DEFAULT_MAX_LENGTH);
    }

    /**
     * 由任意文本生成 slug。
     *
     * @param text      原文（可为 {@code null}）
     * @param maxLength 长度上限（含）
     * @return slug；无可用字符时为空串
     */
    public static String slugify(@Nullable String text, int maxLength) {
        if (text == null || text.isBlank() || maxLength <= 0) {
            return "";
        }
        String s = Normalizer.normalize(text, Normalizer.Form.NFKD);
        s = MARKS.matcher(s).replaceAll("");
        s = s.toLowerCase(Locale.ROOT);
        s = NON_ALNUM.matcher(s).replaceAll("-");
        s = strip(s);
        return truncate(s, maxLength);
    }

    /**
     * 截断到 {@code maxLength} 以内：超出时优先在后半段最近的连字符处截断，避免截出半个单词；再去首尾连字符。
     */
    public static String truncate(String slug, int maxLength) {
        if (maxLength <= 0) {
            return "";
        }
        if (slug.length() <= maxLength) {
            return slug;
        }
        String cut = slug.substring(0, maxLength);
        int dash = cut.lastIndexOf('-');
        if (dash > maxLength / 2) {
            cut = cut.substring(0, dash);
        }
        return strip(cut);
    }

    /**
     * 是否为合法 slug（字母、数字、下划线、连字符；允许空串）
     */
    public static boolean isValid(@Nullable String slug) {
        return slug != null && VALID.matcher(slug).matches();
    }

    private static String strip(String s) {
        return DASHES.matcher(s).replaceAll("");
    }
}
