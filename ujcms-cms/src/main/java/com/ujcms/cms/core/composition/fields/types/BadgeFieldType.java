package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 角标字段。值：{@code {text, bg, fg}}；{@code null} 表示无角标。
 *
 * @author PONY
 */
@Component
public class BadgeFieldType implements FieldType {
    @Override
    public String name() {
        return "badge";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        Map<String, Object> map = FieldValues.asMap(value);
        if (map == null) {
            return List.of("必须为角标对象");
        }
        List<String> errors = new ArrayList<>();
        String text = FieldValues.getString(map, "text");
        if (text == null || text.isBlank()) {
            errors.add("text 不能为空");
        }
        for (String key : new String[]{"bg", "fg"}) {
            String color = FieldValues.getString(map, key);
            if (color != null && !color.isBlank() && !FieldValues.HEX_COLOR_PATTERN.matcher(color).matches()) {
                errors.add(key + " 必须为十六进制颜色(#RGB/#RRGGBB)");
            }
        }
        return errors;
    }
}
