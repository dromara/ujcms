package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 列表字段。定义：{@code {"type":"list","of":{…field…},"min","max"}}。有序、后台可拖拽排序。
 * 元素类型允许嵌套一层 object（如 {@code {label, url}} 行），更深递归在 Shape 加载时被拒绝。
 *
 * @author PONY
 */
@Component
public class ListFieldType implements FieldType {
    @Override
    public String name() {
        return "list";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (!(value instanceof List<?> list)) {
            return List.of("必须为列表");
        }
        List<String> errors = new ArrayList<>();
        Integer min = def.getInteger("min");
        if (min != null && list.size() < min) {
            errors.add("元素数量不能少于 " + min);
        }
        Integer max = def.getInteger("max");
        if (max != null && list.size() > max) {
            errors.add("元素数量不能多于 " + max);
        }
        FieldDef of = def.getFieldDef("of");
        if (of == null) {
            errors.add("字段定义缺少 of(元素类型)");
            return errors;
        }
        for (int i = 0; i < list.size(); i++) {
            Object element = list.get(i);
            if (element == null) {
                errors.add("[" + i + "] 元素不能为空");
                continue;
            }
            for (String message : ctx.validateValue(element, of)) {
                errors.add("[" + i + "] " + message);
            }
        }
        return errors;
    }

    @Override
    public Object processOnSave(Object value, FieldDef def, FieldValueValidator ctx) {
        FieldDef of = def.getFieldDef("of");
        if (!(value instanceof List<?> list) || of == null) {
            return value;
        }
        List<Object> result = new ArrayList<>(list.size());
        for (Object element : list) {
            result.add(ctx.processValueOnSave(element, of));
        }
        return result;
    }
}
