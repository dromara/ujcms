package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主题色解析（§7.2 / §9.2 步骤 5）。{@code themeColor} 字段值 {@code {token}} 在<b>读取期</b>按
 * 当前主题设置的调色板算出 {@code resolvedValue} 并并入渲染契约——从不持久化，故调色板一改即自动重解析。
 *
 * <p>沿 Shape 字段定义递归（含 {@code list}/{@code object} 内部），仅在含 {@code themeColor} 处生成新副本，
 * 不修改入参（卡片缓存数据不可变）。调色板为空（无主题设置）时原样返回。
 *
 * @author PONY
 */
public final class ThemeColorResolver {
    /**
     * 主题色字段类型名
     */
    public static final String TYPE_THEME_COLOR = "themeColor";
    private static final String TYPE_LIST = "list";
    private static final String TYPE_OBJECT = "object";
    private static final String TOKEN = "token";
    private static final String RESOLVED_VALUE = "resolvedValue";

    private ThemeColorResolver() {
    }

    /**
     * 解析卡片数据中的全部 {@code themeColor} 令牌。
     *
     * @param fields  卡片 Shape 字段定义
     * @param data    卡片字段数据
     * @param palette 调色板令牌 → 颜色值（来自主题设置的 {@code palette} 分组合并值）
     * @return 注入了 {@code resolvedValue} 的字段数据（无需解析时原样返回入参）
     */
    public static Map<String, Object> resolve(Map<String, FieldDef> fields, Map<String, Object> data,
                                              Map<String, Object> palette) {
        if (fields.isEmpty() || data.isEmpty() || palette.isEmpty()) {
            return data;
        }
        Map<String, Object> result = null;
        for (Map.Entry<String, FieldDef> entry : fields.entrySet()) {
            String name = entry.getKey();
            Object value = data.get(name);
            if (value == null) {
                continue;
            }
            Object resolved = resolveValue(entry.getValue(), value, palette);
            if (resolved != value) {
                if (result == null) {
                    result = new LinkedHashMap<>(data);
                }
                result.put(name, resolved);
            }
        }
        return result != null ? result : data;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Object resolveValue(FieldDef def, Object value, Map<String, Object> palette) {
        String type = def.getType();
        if (TYPE_THEME_COLOR.equals(type) && value instanceof Map<?, ?> map) {
            Object token = map.get(TOKEN);
            if (!(token instanceof String tokenStr) || !palette.containsKey(tokenStr)) {
                return value;
            }
            Map<String, Object> out = new LinkedHashMap<>((Map<String, Object>) map);
            out.put(RESOLVED_VALUE, palette.get(tokenStr));
            return out;
        }
        if (TYPE_LIST.equals(type) && value instanceof List<?> list) {
            FieldDef of = def.getFieldDef("of");
            if (of == null) {
                return value;
            }
            List<Object> out = new ArrayList<>(list.size());
            boolean changed = false;
            for (Object element : list) {
                Object resolved = element == null ? null : resolveValue(of, element, palette);
                changed |= resolved != element;
                out.add(resolved);
            }
            return changed ? out : value;
        }
        if (TYPE_OBJECT.equals(type) && value instanceof Map<?, ?> map) {
            Map<String, Object> objectData = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k instanceof String key) {
                    objectData.put(key, v);
                }
            });
            Map<String, Object> resolved = resolve(def.getFieldDefs("fields"), objectData, palette);
            return resolved == objectData ? value : resolved;
        }
        return value;
    }
}
