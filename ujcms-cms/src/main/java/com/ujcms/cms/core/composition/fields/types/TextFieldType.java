package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * 单行纯文本字段。参数：{@code min}、{@code max}、{@code pattern}。
 *
 * @author PONY
 */
@Component
public class TextFieldType implements FieldType {
    @Override
    public String name() {
        return "text";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (!(value instanceof String str)) {
            return List.of("必须为字符串");
        }
        List<String> errors = new ArrayList<>();
        Integer min = def.getInteger("min");
        if (min != null && str.length() < min) {
            errors.add("长度不能小于 " + min);
        }
        Integer max = def.getInteger("max");
        if (max != null && str.length() > max) {
            errors.add("长度不能大于 " + max);
        }
        String pattern = def.getString("pattern");
        if (pattern != null) {
            try {
                if (!str.matches(pattern)) {
                    errors.add("不符合格式: " + pattern);
                }
            } catch (PatternSyntaxException e) {
                errors.add("字段定义的 pattern 不是合法正则: " + pattern);
            }
        }
        return errors;
    }
}
