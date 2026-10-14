package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主题色解析测试（§7.2 / §9.2 步骤 5）：令牌 → resolvedValue 注入、嵌套 list/object、空调色板与缺失令牌。
 */
class ThemeColorResolverTest {
    private static FieldDef def(String type) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", type);
        return new FieldDef(raw);
    }

    private static FieldDef themeColorField() {
        return def("themeColor");
    }

    private static Map<String, Object> token(String token) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("token", token);
        return map;
    }

    @Test
    void injectsResolvedValueForKnownToken() {
        Map<String, FieldDef> fields = Map.of("bg", themeColorField(), "title", def("text"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bg", token("primary"));
        data.put("title", "Hello");
        Map<String, Object> palette = Map.of("primary", "#2563eb");

        Map<String, Object> result = ThemeColorResolver.resolve(fields, data, palette);

        @SuppressWarnings("unchecked")
        Map<String, Object> bg = (Map<String, Object>) result.get("bg");
        assertEquals("primary", bg.get("token"));
        assertEquals("#2563eb", bg.get("resolvedValue"));
        assertEquals("Hello", result.get("title"));
        // 原始数据未被修改（不可变契约）
        assertFalse(((Map<?, ?>) data.get("bg")).containsKey("resolvedValue"));
    }

    @Test
    void emptyPaletteReturnsInputUntouched() {
        Map<String, FieldDef> fields = Map.of("bg", themeColorField());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bg", token("primary"));
        assertSame(data, ThemeColorResolver.resolve(fields, data, Map.of()));
    }

    @Test
    void unknownTokenLeavesValueWithoutResolvedValue() {
        Map<String, FieldDef> fields = Map.of("bg", themeColorField());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bg", token("missing"));
        Map<String, Object> result = ThemeColorResolver.resolve(fields, data, Map.of("primary", "#fff"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bg = (Map<String, Object>) result.get("bg");
        assertNull(bg.get("resolvedValue"));
    }

    @Test
    void resolvesThemeColorInsideList() {
        FieldDef list = listOf("themeColor");
        Map<String, FieldDef> fields = Map.of("swatches", list);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("swatches", List.of(token("primary"), token("accent")));
        Map<String, Object> palette = Map.of("primary", "#111", "accent", "#222");

        Map<String, Object> result = ThemeColorResolver.resolve(fields, data, palette);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> swatches = (List<Map<String, Object>>) result.get("swatches");
        assertEquals("#111", swatches.get(0).get("resolvedValue"));
        assertEquals("#222", swatches.get(1).get("resolvedValue"));
    }

    @Test
    void resolvesThemeColorInsideObject() {
        Map<String, Object> objRaw = new LinkedHashMap<>();
        objRaw.put("type", "object");
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("accent", Map.of("type", "themeColor"));
        objRaw.put("fields", sub);
        FieldDef obj = new FieldDef(objRaw);

        Map<String, FieldDef> fields = Map.of("style", obj);
        Map<String, Object> styleVal = new LinkedHashMap<>();
        styleVal.put("accent", token("brand"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("style", styleVal);

        Map<String, Object> result = ThemeColorResolver.resolve(fields, data, Map.of("brand", "#0a0"));
        @SuppressWarnings("unchecked")
        Map<String, Object> style = (Map<String, Object>) result.get("style");
        @SuppressWarnings("unchecked")
        Map<String, Object> accent = (Map<String, Object>) style.get("accent");
        assertEquals("#0a0", accent.get("resolvedValue"));
    }

    @Test
    void noThemeColorFieldsReturnsInput() {
        Map<String, FieldDef> fields = Map.of("title", def("text"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", "x");
        assertTrue(ThemeColorResolver.resolve(fields, data, Map.of("primary", "#fff")).containsKey("title"));
    }

    private static FieldDef listOf(String ofType) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", "list");
        raw.put("of", Map.of("type", ofType));
        return new FieldDef(raw);
    }
}
