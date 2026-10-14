package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 自由颜色字段。值：十六进制颜色字符串（{@code ^#[0-9A-Fa-f]{3,8}$}）。
 * 也是主题设置中定义调色板自身所用的输入类型。
 *
 * @author PONY
 */
@Component
public class FreeColorFieldType implements FieldType {
    @Override
    public String name() {
        return "freeColor";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (!(value instanceof String str) || !FieldValues.HEX_COLOR_PATTERN.matcher(str).matches()) {
            return List.of("必须为十六进制颜色(#RGB/#RRGGBB)");
        }
        return Collections.emptyList();
    }
}
