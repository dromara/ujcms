package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 数字字段。参数：{@code min}、{@code max}、{@code integer}。
 *
 * @author PONY
 */
@Component
public class NumberFieldType implements FieldType {
    @Override
    public String name() {
        return "number";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (!(value instanceof Number number)) {
            return List.of("必须为数字");
        }
        List<String> errors = new ArrayList<>();
        Double min = def.getDouble("min");
        if (min != null && number.doubleValue() < min) {
            errors.add("不能小于 " + min);
        }
        Double max = def.getDouble("max");
        if (max != null && number.doubleValue() > max) {
            errors.add("不能大于 " + max);
        }
        if (def.getBoolean("integer", false) && !FieldValues.isIntegral(number)) {
            errors.add("必须为整数");
        }
        return errors;
    }
}
