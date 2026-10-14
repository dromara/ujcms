package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对象字段。定义：{@code {"type":"object","fields":{…}}}。
 * 仅支持在 list 内嵌套一层 object，更深递归在 Shape 加载时被拒绝。
 *
 * @author PONY
 */
@Component
public class ObjectFieldType implements FieldType {
    @Override
    public String name() {
        return "object";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        Map<String, Object> map = FieldValues.asMap(value);
        if (map == null) {
            return List.of("必须为对象");
        }
        Map<String, FieldDef> fields = def.getFieldDefs("fields");
        if (fields.isEmpty()) {
            return List.of("字段定义缺少 fields(子字段)");
        }
        List<String> errors = new ArrayList<>();
        for (FieldValueValidator.FieldError error : ctx.validate(fields, map)) {
            errors.add(error.field() + ": " + error.message());
        }
        return errors;
    }

    @Override
    public Object processOnSave(Object value, FieldDef def, FieldValueValidator ctx) {
        Map<String, Object> map = FieldValues.asMap(value);
        if (map == null) {
            return value;
        }
        return ctx.processOnSave(def.getFieldDefs("fields"), map);
    }
}
