package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 布尔字段。
 *
 * @author PONY
 */
@Component
public class BooleanFieldType implements FieldType {
    @Override
    public String name() {
        return "boolean";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (!(value instanceof Boolean)) {
            return List.of("必须为布尔值");
        }
        return Collections.emptyList();
    }
}
