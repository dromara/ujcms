package com.ujcms.cms.core.composition.fields.types;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 字段值读取工具。复合类型从嵌套 JSON 对象中按键取值时使用。
 *
 * @author PONY
 */
final class FieldValues {
    /**
     * URL 校验：与 BlockItem.linkUrl 相同的安全口径
     */
    static final Pattern URL_PATTERN = Pattern.compile("^(http|/).*$");
    /**
     * 十六进制颜色
     */
    static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{3,8}$");
    /**
     * 引用ID字符串（雪花ID超出 JS 安全整数范围，控制台以字符串提交）
     */
    static final Pattern REF_ID_PATTERN = Pattern.compile("^[1-9][0-9]{0,18}$");

    private FieldValues() {
    }

    /**
     * 解析引用ID（articleRef/channelRef 的值）：正整数 Number，或正整数字符串
     * （ID 序列化为字符串是跨轨道契约的一部分，§10.1）。非法时返回 {@code null}。
     */
    @Nullable
    static Long asRefId(Object value) {
        if (value instanceof Number number && isIntegral(number) && number.longValue() > 0) {
            return number.longValue();
        }
        if (value instanceof String str && REF_ID_PATTERN.matcher(str).matches()) {
            return Long.parseLong(str);
        }
        return null;
    }

    @Nullable
    static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>(map.size());
            map.forEach((k, v) -> {
                if (k instanceof String key) {
                    result.put(key, v);
                }
            });
            return result;
        }
        return null;
    }

    @Nullable
    static String getString(Map<String, Object> map, String key) {
        return map.get(key) instanceof String str ? str : null;
    }

    @Nullable
    static Number getNumber(Map<String, Object> map, String key) {
        return map.get(key) instanceof Number number ? number : null;
    }

    static boolean isIntegral(Number number) {
        if (number instanceof Integer || number instanceof Long || number instanceof Short
                || number instanceof Byte || number instanceof java.math.BigInteger) {
            return true;
        }
        double d = number.doubleValue();
        return d == Math.rint(d) && !Double.isInfinite(d);
    }
}
