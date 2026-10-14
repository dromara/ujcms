package com.ujcms.cms.core.composition.fields;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段定义。对 Shape JSON 中单个字段定义对象（{@code {"type":"text","max":150,...}}）的只读包装，
 * 提供带类型的参数读取。
 *
 * @author PONY
 */
public final class FieldDef {
    /**
     * 通用属性：字段类型
     */
    public static final String PROP_TYPE = "type";
    /**
     * 通用属性：是否必填
     */
    public static final String PROP_REQUIRED = "required";

    private final Map<String, Object> raw;

    public FieldDef(Map<String, Object> raw) {
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    public String getType() {
        Object type = raw.get(PROP_TYPE);
        return type instanceof String str ? str : "";
    }

    public boolean isRequired() {
        return Boolean.TRUE.equals(raw.get(PROP_REQUIRED));
    }

    @Nullable
    public Object getDefaultValue() {
        return raw.get("default");
    }

    @Nullable
    public String getString(String name) {
        Object value = raw.get(name);
        return value instanceof String str ? str : null;
    }

    @Nullable
    public Integer getInteger(String name) {
        Object value = raw.get(name);
        return value instanceof Number number ? number.intValue() : null;
    }

    @Nullable
    public Long getLong(String name) {
        Object value = raw.get(name);
        return value instanceof Number number ? number.longValue() : null;
    }

    @Nullable
    public Double getDouble(String name) {
        Object value = raw.get(name);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        Object value = raw.get(name);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    /**
     * 读取字符串列表参数（如 enum 的 {@code values}、themeColor 的 {@code palette}）
     */
    public List<String> getStringList(String name) {
        Object value = raw.get(name);
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return Collections.emptyList();
    }

    /**
     * 读取嵌套对象参数（如 media 的 {@code constraints}）
     */
    public Map<String, Object> getMap(String name) {
        Object value = raw.get(name);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>(map.size());
            map.forEach((k, v) -> {
                if (k instanceof String key) {
                    result.put(key, v);
                }
            });
            return result;
        }
        return Collections.emptyMap();
    }

    /**
     * 读取嵌套字段定义（list 的 {@code of}）。非对象时返回 {@code null}。
     */
    @Nullable
    public FieldDef getFieldDef(String name) {
        Map<String, Object> map = getMap(name);
        return map.isEmpty() ? null : new FieldDef(map);
    }

    /**
     * 读取嵌套字段定义集合（object 的 {@code fields}）。保持声明顺序。
     */
    public Map<String, FieldDef> getFieldDefs(String name) {
        Map<String, Object> map = getMap(name);
        Map<String, FieldDef> result = new LinkedHashMap<>(map.size());
        map.forEach((key, value) -> {
            if (value instanceof Map<?, ?> defMap) {
                Map<String, Object> def = new LinkedHashMap<>(defMap.size());
                defMap.forEach((k, v) -> {
                    if (k instanceof String s) {
                        def.put(s, v);
                    }
                });
                result.put(key, new FieldDef(def));
            }
        });
        return result;
    }
}
