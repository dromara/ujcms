package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shape 加载测试：合法定义、各类畸形定义。
 */
class ShapeLoaderTest {
    private final ShapeLoader loader = new ShapeLoader(new CompositionSchemas());

    private Shape load(String json) {
        return loader.load(json, "theme:1/test", "test.json");
    }

    @Test
    void loadsValidShape() {
        Shape shape = load("""
                { "name": "FocusSlide", "version": "2.0.0",
                  "label": { "zh-CN": "焦点图卡片" }, "tags": ["homepage"],
                  "titleField": "headline",
                  "item": {
                    "headline": { "type": "text", "max": 150, "required": true },
                    "image": { "type": "media", "required": true }
                  },
                  "migrations": [ { "from": "1.x", "rename": { "subtitle": "subhead" } } ] }
                """);
        assertEquals("FocusSlide", shape.getName());
        assertEquals("2.0.0", shape.getVersion());
        assertEquals("headline", shape.getTitleField());
        assertEquals("焦点图卡片", shape.getLabel().get("zh-CN"));
        assertEquals(2, shape.getFields().size());
        assertEquals(1, shape.getMigrations().size());
        assertTrue(shape.getFields().get("headline").isRequired());
    }

    @Test
    void loadsPresentationBlockAndRejectsUnknownIcon() {
        // visual-builder §7.1：可选 presentation 块（icon 受控枚举 / group 本地化 / order / thumbnail P6/D11）
        Shape shape = load("""
                { "name": "Demo", "version": "1.0.0",
                  "presentation": { "icon": "carousel", "group": { "zh-CN": "首页组件" },
                                    "order": 10, "thumbnail": "thumbnails/demo.png" },
                  "item": { "title": { "type": "text" } } }
                """);
        assertEquals("carousel", shape.getPresentation().get("icon"));
        assertEquals(10, shape.getPresentation().get("order"));
        assertEquals("thumbnails/demo.png", shape.getPresentation().get("thumbnail"));
        // 未声明 → 空 Map
        assertTrue(load("""
                { "name": "Plain", "version": "1.0.0", "item": { "t": { "type": "text" } } }
                """).getPresentation().isEmpty());
        // icon 非受控枚举 / 未知键 → Schema 拒绝
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Bad", "version": "1.0.0",
                  "presentation": { "icon": "sparkle" },
                  "item": { "t": { "type": "text" } } }
                """));
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Bad", "version": "1.0.0",
                  "presentation": { "hover": true },
                  "item": { "t": { "type": "text" } } }
                """));
    }

    @Test
    void thumbnailGrammarAcceptsPathAndDataUriRejectsTraversal() {
        // P6/D11 缩略图语法：相对路径 / data:image/ URI 合法；穿越、绝对路径、协议前缀 Schema 拒绝
        assertEquals("data:image/svg+xml;base64,PHN2Zy8+", load("""
                { "name": "Demo", "version": "1.0.0",
                  "presentation": { "thumbnail": "data:image/svg+xml;base64,PHN2Zy8+" },
                  "item": { "t": { "type": "text" } } }
                """).getPresentation().get("thumbnail"));
        for (String bad : new String[]{"../evil.svg", "/etc/passwd", "https://evil.example/x.svg",
                "thumbnails\\\\evil.svg"}) {
            assertThrows(CompositionLoadException.class, () -> load("""
                    { "name": "Bad", "version": "1.0.0",
                      "presentation": { "thumbnail": "%s" },
                      "item": { "t": { "type": "text" } } }
                    """.formatted(bad)), bad);
        }
    }

    @Test
    void titleFieldDefaultsToFirstTextField() {
        Shape shape = load("""
                { "name": "Demo", "version": "1.0.0",
                  "item": {
                    "image": { "type": "media" },
                    "caption": { "type": "text" }
                  } }
                """);
        assertEquals("caption", shape.getTitleField());
        assertEquals("标题", shape.resolveTitle(Map.of("caption", "标题")));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(CompositionLoadException.class, () -> load("{ not json"));
    }

    @Test
    void rejectsNonPascalCaseName() {
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "focusSlide", "version": "1.0.0", "item": { "a": { "type": "text" } } }
                """));
    }

    @Test
    void rejectsV1FieldsBlock() {
        // v2 更名（component-settings §4.1）：条目结构块为 item，v1 的 fields 是未知顶层属性
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0", "fields": { "a": { "type": "text" } } }
                """));
    }

    @Test
    void rejectsInvalidSemver() {
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0", "item": { "a": { "type": "text" } } }
                """));
    }

    @Test
    void itemIsOptionalButNotEmpty() {
        // v2：item 可省略（纯设置/纯装饰组件），但声明了就不得为空对象
        Shape chrome = load("""
                { "name": "Demo", "version": "1.0.0" }
                """);
        assertTrue(chrome.getFields().isEmpty());
        assertNull(chrome.getTitleField());
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0", "item": {} }
                """));
    }

    @Test
    void rejectsUnknownTopLevelProperty() {
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0", "extends": "Other",
                  "item": { "a": { "type": "text" } } }
                """));
    }

    @Test
    void rejectsTitleFieldReferencingMissingField() {
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0", "titleField": "missing",
                  "item": { "a": { "type": "text" } } }
                """));
    }

    @Test
    void rejectsListOfList() {
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0",
                  "item": { "rows": { "type": "list", "of": { "type": "list", "of": { "type": "text" } } } } }
                """));
    }

    @Test
    void rejectsListMissingOf() {
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0",
                  "item": { "rows": { "type": "list" } } }
                """));
    }

    @Test
    void rejectsStructuralFieldInsideNestedObject() {
        // list > object > object：超过允许的嵌套深度
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0",
                  "item": { "rows": { "type": "list", "of": { "type": "object",
                    "fields": { "nested": { "type": "object", "fields": { "a": { "type": "text" } } } } } } } }
                """));
    }

    @Test
    void rejectsObjectContainingList() {
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0",
                  "item": { "box": { "type": "object",
                    "fields": { "items": { "type": "list", "of": { "type": "text" } } } } } }
                """));
    }

    @Test
    void loadsSettingsAndDataBlocks() {
        // component-settings §4.1：RankedList 形态——settings + data 模板 + item
        Shape shape = load("""
                { "name": "RankedList", "version": "2.0.0",
                  "settings": {
                    "title":   { "type": "text", "max": 150, "default": "热点新闻" },
                    "channel": { "type": "channelRef" },
                    "limit":   { "type": "number", "default": 9, "min": 1, "max": 20 },
                    "orderBy": { "type": "enum", "default": "weekViews desc",
                                 "values": ["weekViews desc", "views desc", "publishedAt desc"] }
                  },
                  "data": { "from": "article",
                            "where": { "channel": "$settings.channel", "isIncludeSubChannel": true },
                            "orderBy": "$settings.orderBy",
                            "limit": "$settings.limit" },
                  "item": { "headline": { "type": "text", "required": true },
                            "link":     { "type": "link", "required": true } } }
                """);
        assertEquals(4, shape.getSettings().size());
        assertEquals("热点新闻", shape.getSettings().get("title").getDefaultValue());
        assertNotNull(shape.getData());
        assertEquals("article", shape.getData().get("from"));
        // 纯设置组件：settings-only、无 data/item（RichHtml v2 形态）
        Shape settingsOnly = load("""
                { "name": "RichHtml", "version": "2.0.0",
                  "settings": { "body": { "type": "html" } } }
                """);
        assertNull(settingsOnly.getData());
        assertTrue(settingsOnly.getFields().isEmpty());
    }

    @Test
    void rejectsDataReferencingUndeclaredOrMistypedSettings() {
        // $settings 引用未声明设置 → 加载期拒绝
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0",
                  "settings": { "limit": { "type": "number" } },
                  "data": { "from": "article", "where": { "channel": "$settings.channel" } },
                  "item": { "t": { "type": "text" } } }
                """));
        // limit 引用非 number 设置 → 拒绝
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0",
                  "settings": { "title": { "type": "text" } },
                  "data": { "from": "article", "limit": "$settings.title" },
                  "item": { "t": { "type": "text" } } }
                """));
        // mode 引用非 enum 设置 → 拒绝
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0",
                  "settings": { "mode": { "type": "text" },
                                "slides": { "type": "list", "of": { "type": "object",
                                            "fields": { "t": { "type": "text" } } } } },
                  "data": { "from": "article", "mode": "$settings.mode", "items": "$settings.slides" },
                  "item": { "t": { "type": "text" } } }
                """));
        // pinned 引用非 list-of-引用 设置 → 拒绝
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0",
                  "settings": { "pinned": { "type": "list", "of": { "type": "text" } } },
                  "data": { "from": "article", "pinned": "$settings.pinned" },
                  "item": { "t": { "type": "text" } } }
                """));
        // where 引用白名单外的 $ctx 键 → 拒绝
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0",
                  "data": { "from": "article", "where": { "channel": "$ctx.userId" } },
                  "item": { "t": { "type": "text" } } }
                """));
    }

    @Test
    void rejectsDataWithoutFromOrItems() {
        // Schema：data 须至少声明 from（查询）或 items（手工条目）之一
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0",
                  "data": { "limit": 5 },
                  "item": { "t": { "type": "text" } } }
                """));
    }

    @Test
    void settingsFollowSameStructuralRules() {
        // settings 与 item 同嵌套约束：list-of-object 合法（页面本地编排条目）；list 嵌 list 拒绝
        Shape shape = load("""
                { "name": "Demo", "version": "1.0.0",
                  "settings": { "slides": { "type": "list", "of": { "type": "object",
                                "fields": { "t": { "type": "text" } } } } } }
                """);
        assertEquals("list", shape.getSettings().get("slides").getType());
        assertThrows(CompositionLoadException.class, () -> load("""
                { "name": "Demo", "version": "1.0.0",
                  "settings": { "rows": { "type": "list", "of": { "type": "list",
                                "of": { "type": "text" } } } } }
                """));
    }

}
