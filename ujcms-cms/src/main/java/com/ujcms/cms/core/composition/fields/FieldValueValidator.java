package com.ujcms.cms.core.composition.fields;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段值校验器。按字段定义集合（Shape 的 {@code fields}、Slot 的 {@code sectionFields}、
 * 主题设置组的 {@code fields}）校验一组数据，并在保存前做值处理（HTML 清理等）。
 *
 * <p>结构类型（list/object）通过 {@link #validateValue} 递归校验子字段。
 *
 * @author PONY
 */
public final class FieldValueValidator {
    private final FieldTypeRegistry registry;

    public FieldValueValidator(FieldTypeRegistry registry) {
        this.registry = registry;
    }

    public FieldTypeRegistry getRegistry() {
        return registry;
    }

    /**
     * 校验一组字段数据。
     *
     * @param fields 字段定义集合
     * @param data   数据
     * @return 校验错误列表（{@code field} 为字段路径），空列表表示通过
     */
    public List<FieldError> validate(Map<String, FieldDef> fields, Map<String, Object> data) {
        List<FieldError> errors = new ArrayList<>();
        // 未声明字段直接拒绝，保证 data_json_ 与 Shape 严格一致
        for (String key : data.keySet()) {
            if (!fields.containsKey(key)) {
                errors.add(new FieldError(key, "字段未在卡片类型中声明"));
            }
        }
        fields.forEach((name, def) -> {
            Object value = data.get(name);
            if (isEmpty(value)) {
                if (def.isRequired()) {
                    errors.add(new FieldError(name, "必填字段不能为空"));
                }
                return;
            }
            for (String message : validateValue(value, def)) {
                errors.add(new FieldError(name, message));
            }
        });
        return errors;
    }

    /**
     * 校验单个非空值（按字段定义的类型分发）。结构类型递归时也调用本方法。
     *
     * @param value 字段值（非 {@code null}）
     * @param def   字段定义
     * @return 错误信息列表
     */
    public List<String> validateValue(Object value, FieldDef def) {
        FieldType type = registry.get(def.getType());
        if (type == null) {
            return List.of("未知字段类型: " + def.getType());
        }
        return type.validate(value, def, this);
    }

    /**
     * 保存前处理一组字段数据（如 {@code html} 字段清理）。仅处理已声明字段的非空值。
     *
     * @param fields 字段定义集合
     * @param data   数据
     * @return 处理后的数据（新 Map，保持原有键序）
     */
    public Map<String, Object> processOnSave(Map<String, FieldDef> fields, Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>(data.size());
        data.forEach((name, value) -> {
            FieldDef def = fields.get(name);
            result.put(name, def == null ? value : processValueOnSave(value, def));
        });
        return result;
    }

    /**
     * 保存前处理单个值。结构类型递归时也调用本方法。
     */
    @Nullable
    public Object processValueOnSave(@Nullable Object value, FieldDef def) {
        if (value == null) {
            return null;
        }
        FieldType type = registry.get(def.getType());
        return type == null ? value : type.processOnSave(value, def, this);
    }

    /**
     * 值是否为空（必填校验口径：{@code null} / 空白字符串 / 空 Map / 空 List）。
     * 发布期必填门与健康报告复用同一口径（design-review P1-4）。
     */
    public static boolean isEmpty(@Nullable Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String str) {
            return str.isBlank();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    /**
     * 字段级校验错误
     *
     * @param field   字段路径（如 {@code headline}、{@code rows[2].url}）
     * @param message 错误信息
     */
    public record FieldError(String field, String message) {
    }
}
