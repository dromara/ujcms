package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

/**
 * 日期字段。值为 {@code YYYY-MM-DD} 字符串。
 *
 * @author PONY
 */
@Component
public class DateFieldType implements FieldType {
    @Override
    public String name() {
        return "date";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (!(value instanceof String str)) {
            return List.of("必须为 YYYY-MM-DD 格式字符串");
        }
        try {
            LocalDate.parse(str);
        } catch (DateTimeParseException e) {
            return List.of("不是合法日期(YYYY-MM-DD): " + str);
        }
        return Collections.emptyList();
    }
}
