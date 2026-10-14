package com.ujcms.cms.core.composition.sources;

import com.ujcms.cms.core.composition.fields.FieldDef;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 投影引擎测试（§14.2 / §14.4）：按目标 Shape 字段类型包装（link/media → {url}，themeColor → {token}）、
 * {@code $.customs.<code>} 嵌套路径、含字面量的模板插值（{@code /go/$.id}）、省略投影时的约定默认。
 * 直接以字段视图 Map 驱动，不依赖来源实体。Phase D 起产出 {@link ResolvedItem}（契约条目不携带
 * 生命周期 / 标签 / 合成标注）。
 */
class SourceProjectorTest {
    private static FieldDef field(String type) {
        return new FieldDef(Map.of("type", type));
    }

    private static Map<String, FieldDef> linkRowFields() {
        Map<String, FieldDef> fields = new LinkedHashMap<>();
        fields.put("label", field("text"));
        fields.put("link", field("link"));
        fields.put("icon", field("media"));
        return fields;
    }

    @Test
    void explicitProjectionWrapsLinkAndMediaByFieldType() {
        Map<String, Object> view = Map.of("id", 5L, "title", "文章A", "url", "/article/5", "image", "/img/a.jpg");
        ResolvedItem item = SourceProjector.project(5L, SourceProjector.FROM_ARTICLE, view,
                Map.of("label", "$.title", "link", "$.url", "icon", "$.image"), linkRowFields(), "LinkRow");

        assertEquals(5L, item.id());
        assertEquals("LinkRow", item.shape());
        assertEquals("文章A", item.data().get("label"));
        assertEquals("/article/5", ((Map<?, ?>) item.data().get("link")).get("url"));
        assertEquals("/img/a.jpg", ((Map<?, ?>) item.data().get("icon")).get("url"));
    }

    @Test
    void customsPathAndTemplateExpression() {
        Map<String, FieldDef> fields = new LinkedHashMap<>();
        fields.put("link", field("link"));
        fields.put("accent", field("themeColor"));
        Map<String, Object> view = Map.of("id", 42L, "customs", Map.of("titleColor", "primary"));

        ResolvedItem item = SourceProjector.project(42L, SourceProjector.FROM_BLOCK_ITEM, view,
                Map.of("link", "/go/$.id", "accent", "$.customs.titleColor"), fields, "Promo");

        // 含字面量模板 → 字符串插值 → 再按 link 类型包成 {url}
        assertEquals("/go/42", ((Map<?, ?>) item.data().get("link")).get("url"));
        // $.customs.titleColor → themeColor 包成 {token}
        assertEquals("primary", ((Map<?, ?>) item.data().get("accent")).get("token"));
    }

    @Test
    void conventionProjectionWhenProjectionOmitted() {
        Map<String, Object> view = Map.of("id", 7L, "title", "标题", "url", "/article/7", "image", "/img/7.jpg");
        ResolvedItem item = SourceProjector.project(7L, SourceProjector.FROM_ARTICLE, view, null,
                linkRowFields(), "LinkRow");

        // 约定：LinkRow.label ← title；link ← url（包 {url}）；icon ← image（包 {url}）
        assertEquals("标题", item.data().get("label"));
        assertEquals("/article/7", ((Map<?, ?>) item.data().get("link")).get("url"));
        assertEquals("/img/7.jpg", ((Map<?, ?>) item.data().get("icon")).get("url"));
    }

    @Test
    void wholePathKeepsMapValueWithoutRewrapping() {
        Map<String, FieldDef> fields = Map.of("icon", field("media"));
        // 来源字段本身已是 media 对象 → 原样保留（不再二次包裹）
        Map<String, Object> view = Map.of("id", 1L, "image", Map.of("url", "/x.jpg", "alt", "替代文字"));
        ResolvedItem item = SourceProjector.project(1L, SourceProjector.FROM_ARTICLE, view,
                Map.of("icon", "$.image"), fields, "LinkRow");

        assertInstanceOf(Map.class, item.data().get("icon"));
        assertEquals("替代文字", ((Map<?, ?>) item.data().get("icon")).get("alt"));
    }

    @Test
    void missingPathFieldsAreOmitted() {
        Map<String, Object> view = Map.of("id", 1L, "title", "T");
        ResolvedItem item = SourceProjector.project(1L, SourceProjector.FROM_ARTICLE, view,
                Map.of("label", "$.title", "icon", "$.image"), linkRowFields(), "LinkRow");

        assertEquals("T", item.data().get("label"));
        // $.image 解析为 null → 该字段不写入 data
        assertFalse(item.data().containsKey("icon"));
        assertNull(item.data().get("icon"));
    }

    @Test
    void articleConventionMapsDurationFromVideoTime() {
        // component-settings §12.3 #2：视频时长进入文章视图，约定映射 duration/videotime ← $.videoTime
        Map<String, FieldDef> fields = new LinkedHashMap<>();
        fields.put("headline", field("text"));
        fields.put("duration", field("text"));
        Map<String, Object> view = Map.of("id", 9L, "title", "视频A", "videoTime", "20:15");

        ResolvedItem item = SourceProjector.project(9L, SourceProjector.FROM_ARTICLE, view, null, fields,
                "VideoCards");

        assertEquals("视频A", item.data().get("headline"));
        assertEquals("20:15", item.data().get("duration"));
    }

    @Test
    void inlineItemKeepsDeclaredFieldsWrapsTypesAndDropsUndeclared() {
        // component-settings §5：手工条目——仅保留 item 声明字段、按类型包装；序号作内部ID
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("label", "自定义");
        raw.put("link", "/promo");
        raw.put("rogue", "<script>x</script>");

        ResolvedItem item = SourceProjector.projectInlineItem(3, raw, linkRowFields(), "LinkRow");

        assertEquals(3L, item.id());
        assertEquals("LinkRow", item.shape());
        assertEquals("自定义", item.data().get("label"));
        assertEquals("/promo", ((Map<?, ?>) item.data().get("link")).get("url"));
        assertFalse(item.data().containsKey("rogue"));
    }
}
