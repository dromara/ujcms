package com.ujcms.cms.core.composition.fields;

import java.util.List;

/**
 * 字段类型。字段类型系统的扩展点：实现本接口并注册为 Spring Bean 即被
 * {@link FieldTypeRegistry} 收集（商业模块可按此方式贡献字段类型）。
 *
 * <p>每个字段类型负责两件事：校验值是否符合字段定义；在保存时对值做必要的处理（如 HTML 清理）。
 * {@code null} 值的必填检查由调用方（{@link FieldValueValidator}）统一处理，
 * {@link #validate} 只会收到非 {@code null} 值。
 *
 * @author PONY
 */
public interface FieldType {
    /**
     * 类型名。即字段定义中 {@code type} 属性的取值，如 {@code text}、{@code media}。
     *
     * @return 类型名
     */
    String name();

    /**
     * 校验非空值。
     *
     * @param value 字段值（非 {@code null}）
     * @param def   字段定义
     * @param ctx   校验上下文（结构类型递归校验子字段时使用）
     * @return 错误信息列表，空列表表示通过
     */
    List<String> validate(Object value, FieldDef def, FieldValueValidator ctx);

    /**
     * 保存前处理值（如 {@code html} 类型做防跨站清理）。默认原样返回。
     *
     * @param value 字段值（非 {@code null}）
     * @param def   字段定义
     * @param ctx   校验上下文
     * @return 处理后的值
     */
    default Object processOnSave(Object value, FieldDef def, FieldValueValidator ctx) {
        return value;
    }
}
