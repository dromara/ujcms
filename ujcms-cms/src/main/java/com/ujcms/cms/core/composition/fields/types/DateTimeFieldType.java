package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

/**
 * 日期时间字段。值为带时区偏移的 ISO 8601 字符串（如 {@code 2026-06-11T08:00:00+08:00}）。
 *
 * @author PONY
 */
@Component
public class DateTimeFieldType implements FieldType {
    @Override
    public String name() {
        return "datetime";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (!(value instanceof String str)) {
            return List.of("必须为 ISO 8601 格式字符串");
        }
        try {
            OffsetDateTime.parse(str);
        } catch (DateTimeParseException e) {
            return List.of("不是合法的带时区偏移 ISO 8601 日期时间: " + str);
        }
        return Collections.emptyList();
    }
}
