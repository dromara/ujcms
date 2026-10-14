package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 引用字段读取期解析测试（§7.3）。按套件既有约定只 mock 服务、不 mock 实体 getter（脆弱且多为 final）——
 * 覆盖 RefResolver 新增的逻辑：ID 归一化、按ID查取的交互、删除目标 → 占位、list/object 递归、
 * 无引用字段时同实例返回、入参不可变、非ID值防御性透传。摘要字段抽取（{@code getTitle/getUrl/getImage}）
 * 是与 {@code SourceProjector.articleView} 相同的 getter 委托，由实景 live boot 端到端覆盖。
 */
class RefResolverTest {
    private ArticleService articleService;
    private ChannelService channelService;
    private RefResolver resolver;

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        channelService = mock(ChannelService.class);
        resolver = new RefResolver(articleService, channelService);
    }

    private static Map<String, FieldDef> fields(String name, String type) {
        return Map.of(name, new FieldDef(Map.of("type", type)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ref(Map<String, Object> data, String key) {
        return (Map<String, Object>) data.get(key);
    }

    @Test
    void articleRefLooksUpByIdAndDeletedYieldsStub() {
        when(articleService.select(101L)).thenReturn(null);   // 已删除
        Map<String, Object> data = new LinkedHashMap<>(Map.of("hero", 101L));

        Map<String, Object> out = resolver.resolve(fields("hero", "articleRef"), data);

        verify(articleService).select(101L);                  // 按裸ID查取（§7.3）
        Map<String, Object> ref = ref(out, "hero");
        assertEquals("101", ref.get("id"));                   // id 序列化为字符串（§10.1）
        assertEquals(Boolean.TRUE, ref.get("deleted"));       // 缺失 → 占位
        assertFalse(ref.containsKey("title"));
        assertEquals(101L, data.get("hero"));                 // 入参不被修改（缓存数据不可变）
    }

    @Test
    void channelRefAcceptsStringIdFromConsole() {
        when(channelService.select(7L)).thenReturn(null);
        // 控制台以字符串提交（雪花ID超出 JS 安全整数，§10.1）
        Map<String, Object> out = resolver.resolve(fields("cat", "channelRef"),
                new LinkedHashMap<>(Map.of("cat", "7")));

        verify(channelService).select(7L);
        assertEquals("7", ref(out, "cat").get("id"));
        assertEquals(Boolean.TRUE, ref(out, "cat").get("deleted"));
    }

    @Test
    void nonRefFieldsUntouchedAndSameInstanceReturned() {
        Map<String, Object> data = new LinkedHashMap<>(Map.of("headline", "纯文本", "n", 5));
        Map<String, Object> out = resolver.resolve(
                Map.of("headline", new FieldDef(Map.of("type", "text")),
                        "n", new FieldDef(Map.of("type", "number"))),
                data);
        assertSame(data, out);   // 无引用字段 → 原样返回入参，不复制
        verify(articleService, never()).select(anyLong());
        verify(channelService, never()).select(anyLong());
    }

    @Test
    void resolvesEachIdInListOfArticleRef() {
        when(articleService.select(anyLong())).thenReturn(null);
        FieldDef listDef = new FieldDef(Map.of("type", "list", "of", Map.of("type", "articleRef")));
        Map<String, Object> data = new LinkedHashMap<>(Map.of("items", List.of(1L, 2L)));

        Map<String, Object> out = resolver.resolve(Map.of("items", listDef), data);

        verify(articleService).select(1L);
        verify(articleService).select(2L);
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) out.get("items");
        assertEquals("1", ((Map<?, ?>) items.get(0)).get("id"));
        assertEquals("2", ((Map<?, ?>) items.get(1)).get("id"));
    }

    @Test
    void resolvesRefInsideObjectAndKeepsSiblings() {
        when(channelService.select(3L)).thenReturn(null);
        FieldDef objectDef = new FieldDef(Map.of("type", "object",
                "fields", Map.of("cat", Map.of("type", "channelRef"), "label", Map.of("type", "text"))));
        Map<String, Object> data = new LinkedHashMap<>(Map.of(
                "box", new LinkedHashMap<>(Map.of("cat", 3L, "label", "热门"))));

        Map<String, Object> out = resolver.resolve(Map.of("box", objectDef), data);

        verify(channelService).select(3L);
        Map<String, Object> box = ref(out, "box");
        assertEquals("3", ref(box, "cat").get("id"));
        assertEquals("热门", box.get("label"));   // 非引用兄弟字段保留
    }

    @Test
    void malformedRefValueLeftUntouchedNoLookup() {
        // 已是解析对象 / 非数字 → 防御性透传，不查取（库内存的恒为裸ID）
        Map<String, Object> already = new LinkedHashMap<>(Map.of("id", "5", "title", "x"));
        Map<String, Object> data = new LinkedHashMap<>(Map.of("hero", already));
        Map<String, Object> out = resolver.resolve(fields("hero", "articleRef"), data);
        assertSame(already, out.get("hero"));
        verify(articleService, never()).select(anyLong());
    }

    // ---- firstRefId（visual-builder §5.5：契约卡片 articleId/channelId 标注） ----------------------

    @Test
    void firstRefIdPicksFirstTopLevelRefOfRequestedType() {
        Map<String, FieldDef> defs = new LinkedHashMap<>();
        defs.put("label", new FieldDef(Map.of("type", "text")));
        defs.put("hero", new FieldDef(Map.of("type", "articleRef")));
        defs.put("second", new FieldDef(Map.of("type", "articleRef")));
        defs.put("cat", new FieldDef(Map.of("type", "channelRef")));
        Map<String, Object> data = new LinkedHashMap<>(Map.of("label", "x", "hero", "9", "second", 8L, "cat", 3L));

        assertEquals(9L, RefResolver.firstRefId(defs, data, RefResolver.TYPE_ARTICLE_REF));
        assertEquals(3L, RefResolver.firstRefId(defs, data, RefResolver.TYPE_CHANNEL_REF));
    }

    @Test
    void firstRefIdSkipsEmptyValuesAndIgnoresNestedRefs() {
        // 首个引用字段无值 → 落到下一个；list/object 内的引用不参与（深链语义有歧义）
        Map<String, FieldDef> defs = new LinkedHashMap<>();
        defs.put("hero", new FieldDef(Map.of("type", "articleRef")));
        defs.put("fallback", new FieldDef(Map.of("type", "articleRef")));
        defs.put("items", new FieldDef(Map.of("type", "list", "of", Map.of("type", "articleRef"))));
        Map<String, Object> data = new LinkedHashMap<>(Map.of("fallback", 42L, "items", List.of(1L)));

        assertEquals(42L, RefResolver.firstRefId(defs, data, RefResolver.TYPE_ARTICLE_REF));

        data.remove("fallback");
        assertEquals(null, RefResolver.firstRefId(defs, data, RefResolver.TYPE_ARTICLE_REF));
    }
}
