package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 多行纯文本字段。参数：{@code max}。
 *
 * @author PONY
 */
@Component
public class LongTextFieldType implements FieldType {
    @Override
    public String name() {
        return "longText";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (!(value instanceof String str)) {
            return List.of("必须为字符串");
        }
        Integer max = def.getInteger("max");
        if (max != null && str.length() > max) {
            return List.of("长度不能大于 " + max);
        }
        return Collections.emptyList();
    }
}
