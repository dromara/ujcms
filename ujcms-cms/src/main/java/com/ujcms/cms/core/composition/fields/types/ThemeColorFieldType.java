package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 主题色字段。值：{@code {token}}。参数：{@code palette: string[]}（主题设置 palette 组中的令牌名）。
 * {@code resolvedValue} 在读取时根据当前主题设置计算，从不持久化（Phase 2+ 渲染时实现）。
 *
 * @author PONY
 */
@Component
public class ThemeColorFieldType implements FieldType {
    @Override
    public String name() {
        return "themeColor";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        Map<String, Object> map = FieldValues.asMap(value);
        if (map == null) {
            return List.of("必须为主题色对象");
        }
        String token = FieldValues.getString(map, "token");
        if (token == null || token.isBlank()) {
            return List.of("token 不能为空");
        }
        List<String> palette = def.getStringList("palette");
        if (!palette.isEmpty() && !palette.contains(token)) {
            return List.of("token 必须为 " + palette + " 之一");
        }
        return Collections.emptyList();
    }
}
