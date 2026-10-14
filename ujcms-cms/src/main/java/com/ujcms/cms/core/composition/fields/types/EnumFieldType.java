package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 枚举字段。参数：{@code values: string[]}。
 *
 * @author PONY
 */
@Component
public class EnumFieldType implements FieldType {
    @Override
    public String name() {
        return "enum";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (!(value instanceof String str)) {
            return List.of("必须为字符串");
        }
        List<String> values = def.getStringList("values");
        if (!values.contains(str)) {
            return List.of("取值必须为 " + values + " 之一");
        }
        return Collections.emptyList();
    }
}
