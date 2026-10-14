package com.ujcms.cms.core.composition.sources;

import com.ujcms.cms.core.composition.CompositionSchemas;
import com.ujcms.cms.core.composition.Shape;
import com.ujcms.cms.core.composition.ShapeLoader;
import com.ujcms.cms.core.composition.ResolveContext;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组件数据模板求值测试（component-settings §4.1 / §5）：{@code $settings.*} 替换（where/orderBy/limit）、
 * 未设值的 skip/empty 策略、mode 切换（manual → 手工条目）、手工条目包装、置顶装载 + 填充去重。
 * 查询执行以 mock {@link QuerySourceResolver} 验证替换后的规格；替换语义与 $ctx 同型（$ctx 由查询侧覆盖）。
 */
class DataTemplateResolverTest {
    private static final Long SITE = 1L;

    private QuerySourceResolver querySourceResolver;
    private ArticleService articleService;
    private ChannelService channelService;
    private DataTemplateResolver resolver;
    private final ShapeLoader loader = new ShapeLoader(new CompositionSchemas());

    @BeforeEach
    void setUp() {
        querySourceResolver = mock(QuerySourceResolver.class);
        articleService = mock(ArticleService.class);
        channelService = mock(ChannelService.class);
        resolver = new DataTemplateResolver(querySourceResolver, articleService, channelService);
    }

    private ResolveContext ctx() {
        return ResolveContext.template("1/default", Map.of("siteId", SITE));
    }

    private Shape rankedList(String missing) {
        return loader.load("""
                { "name": "RankedList", "version": "2.0.0",
                  "settings": {
                    "channel": { "type": "channelRef" },
                    "limit":   { "type": "number", "default": 9, "min": 1, "max": 20 },
                    "orderBy": { "type": "enum", "values": ["weekViews desc", "views desc"] }
                  },
                  "data": { "from": "article",
                            "where": { "channel": "$settings.channel", "isIncludeSubChannel": true },
                            "orderBy": "$settings.orderBy",
                            "limit": "$settings.limit"%s },
                  "item": { "headline": { "type": "text", "required": true } } }
                """.formatted(missing.isEmpty() ? "" : ", \"missing\": \"" + missing + "\""),
                "theme:1/test", "x");
    }

    /** 引擎设置值（SettingsResolver 产物形态）；null 值表示未设。 */
    private static Map<String, Object> settings(Object channel, Object limit, Object orderBy) {
        Map<String, Object> values = new HashMap<>();
        values.put("channel", channel);
        values.put("limit", limit);
        values.put("orderBy", orderBy);
        return values;
    }

    @Test
    void substitutesSettingsIntoWhereOrderByAndLimit() {
        when(querySourceResolver.executeQuery(eq(SITE), anyMap(), any(), any(), isNull(), eq(Set.of())))
                .thenReturn(List.of());

        resolver.resolve(rankedList(""), settings(12L, 9L, "weekViews desc"), SITE, ctx());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(querySourceResolver).executeQuery(eq(SITE), captor.capture(), eq("RankedList"), any(),
                isNull(), eq(Set.of()));
        Map<String, Object> spec = captor.getValue();
        assertEquals("article", spec.get("from"));
        assertEquals("weekViews desc", spec.get("orderBy"));
        assertEquals(9, ((Number) spec.get("limit")).intValue());
        Map<?, ?> where = (Map<?, ?>) spec.get("where");
        // $settings.channel → 类型化边界产出的栏目ID；字面量原样保留
        assertEquals(12L, where.get("channel"));
        assertEquals(Boolean.TRUE, where.get("isIncludeSubChannel"));
    }

    @Test
    void missingOptionalSettingSkipPolicyDropsFilterAndStillQueries() {
        // §4.1：可选设置未设值（channel=null）→ 该 where 条目按 missing:skip 语义删除（留空则统计全站）
        when(querySourceResolver.executeQuery(eq(SITE), anyMap(), any(), any(), isNull(), eq(Set.of())))
                .thenReturn(List.of());

        resolver.resolve(rankedList(""), settings(null, 9L, null), SITE, ctx());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(querySourceResolver).executeQuery(eq(SITE), captor.capture(), any(), any(), isNull(), eq(Set.of()));
        Map<?, ?> where = (Map<?, ?>) captor.getValue().get("where");
        assertFalse(where.containsKey("channel"));
        // orderBy 未设 → 键省略（查询侧回退默认排序）
        assertFalse(captor.getValue().containsKey("orderBy"));
    }

    @Test
    void missingEmptyPolicyShortCircuitsWithoutQuerying() {
        assertTrue(resolver.resolve(rankedList("empty"), settings(null, 9L, null), SITE, ctx())
                .isEmpty());
        verify(querySourceResolver, never()).executeQuery(any(), anyMap(), any(), any(), any(), any());
    }

    @Test
    void noDataTemplateResolvesEmpty() {
        Shape settingsOnly = loader.load("""
                { "name": "RichHtml", "version": "2.0.0", "settings": { "body": { "type": "html" } } }
                """, "theme:1/test", "x");
        assertTrue(resolver.resolve(settingsOnly, Map.of(), SITE, ctx()).isEmpty());
        verify(querySourceResolver, never()).executeQuery(any(), anyMap(), any(), any(), any(), any());
    }

    private Shape heroCarousel() {
        // Q3：查询/手工双模组件——mode 引用 enum 设置，manual 时以 slides 为条目来源
        return loader.load("""
                { "name": "HeroCarousel", "version": "2.0.0",
                  "settings": {
                    "mode":   { "type": "enum", "default": "auto", "values": ["auto", "manual"] },
                    "slides": { "type": "list", "of": { "type": "object",
                                "fields": { "headline": { "type": "text" }, "link": { "type": "link" } } } },
                    "channel": { "type": "channelRef" }
                  },
                  "data": { "from": "article", "where": { "channel": "$settings.channel" },
                            "mode": "$settings.mode", "items": "$settings.slides", "limit": 5 },
                  "item": { "headline": { "type": "text" }, "link": { "type": "link" } } }
                """, "theme:1/test", "x");
    }

    @Test
    void manualModeUsesInlineItemsAndSkipsQuery() {
        Map<String, Object> values = new HashMap<>();
        values.put("mode", "manual");
        values.put("slides", List.of(Map.of("headline", "甲", "link", "/a"), Map.of("headline", "乙")));
        values.put("channel", 12L);

        List<ResolvedItem> items = resolver.resolve(heroCarousel(), values, SITE, ctx());

        assertEquals(2, items.size());
        assertEquals("甲", items.get(0).data().get("headline"));
        // 手工条目按 item 字段类型包装（link 字符串 → {url}）
        assertEquals("/a", ((Map<?, ?>) items.get(0).data().get("link")).get("url"));
        verify(querySourceResolver, never()).executeQuery(any(), anyMap(), any(), any(), any(), any());
    }

    @Test
    void autoModeRunsQuery() {
        when(querySourceResolver.executeQuery(eq(SITE), anyMap(), any(), any(), isNull(), eq(Set.of())))
                .thenReturn(List.of());
        Map<String, Object> values = new HashMap<>();
        values.put("mode", "auto");
        values.put("slides", List.of(Map.of("headline", "甲")));
        values.put("channel", 12L);

        resolver.resolve(heroCarousel(), values, SITE, ctx());

        verify(querySourceResolver).executeQuery(eq(SITE), anyMap(), eq("HeroCarousel"), any(),
                isNull(), eq(Set.of()));
    }

    @Test
    void inlineOnlyComponentNeedsNoFrom() {
        Shape promo = loader.load("""
                { "name": "PromoCards", "version": "2.0.0",
                  "settings": { "cards": { "type": "list", "of": { "type": "object",
                                "fields": { "title": { "type": "text" } } } } },
                  "data": { "items": "$settings.cards" },
                  "item": { "title": { "type": "text" } } }
                """, "theme:1/test", "x");
        List<ResolvedItem> items = resolver.resolve(promo, Map.of("cards", List.of(Map.of("title", "促"))),
                SITE, ctx());
        assertEquals(1, items.size());
        assertEquals("促", items.get(0).data().get("title"));
        verify(querySourceResolver, never()).executeQuery(any(), anyMap(), any(), any(), any(), any());
    }

    private Shape pinnedList() {
        return loader.load("""
                { "name": "PinnedList", "version": "1.0.0",
                  "settings": { "pinned": { "type": "list", "of": { "type": "articleRef" } } },
                  "data": { "from": "article", "pinned": "$settings.pinned", "limit": 5 },
                  "item": { "headline": { "type": "text" } } }
                """, "theme:1/test", "x");
    }

    private Article article(long id, String title) {
        Article article = new Article();
        article.setId(id);
        article.setSiteId(SITE);
        article.setStatus(Article.STATUS_PUBLISHED);
        article.setTitle(title);
        return article;
    }

    @Test
    void pinnedEntitiesLeadAndFillDedupsBySourceId() {
        // §5 编辑置顶：置顶实体先渲染；填充 limitOverride = limit - 置顶数，且按置顶ID去重
        when(articleService.select(101L)).thenReturn(article(101L, "置顶一"));
        when(articleService.select(102L)).thenReturn(article(102L, "置顶二"));
        ResolvedItem fill = new ResolvedItem(200L, "PinnedList", Map.of("headline", "填充"));
        when(querySourceResolver.executeQuery(eq(SITE), anyMap(), any(), any(), eq(3), eq(Set.of(101L, 102L))))
                .thenReturn(List.of(fill));

        List<ResolvedItem> items = resolver.resolve(pinnedList(), Map.of("pinned", List.of(101L, 102L)),
                SITE, ctx());

        assertEquals(3, items.size());
        assertEquals("置顶一", items.get(0).data().get("headline"));
        assertEquals("置顶二", items.get(1).data().get("headline"));
        assertEquals("填充", items.get(2).data().get("headline"));
        verify(querySourceResolver).executeQuery(eq(SITE), anyMap(), any(), any(), eq(3), eq(Set.of(101L, 102L)));
    }

    @Test
    void pinnedBeyondLimitTruncatesWithoutQuerying() {
        when(articleService.select(101L)).thenReturn(article(101L, "一"));
        when(articleService.select(102L)).thenReturn(article(102L, "二"));
        Shape shape = loader.load("""
                { "name": "PinnedList", "version": "1.0.0",
                  "settings": { "pinned": { "type": "list", "of": { "type": "articleRef" } } },
                  "data": { "from": "article", "pinned": "$settings.pinned", "limit": 1 },
                  "item": { "headline": { "type": "text" } } }
                """, "theme:1/test", "x");

        List<ResolvedItem> items = resolver.resolve(shape, Map.of("pinned", List.of(101L, 102L)),
                SITE, ctx());

        assertEquals(1, items.size());
        assertEquals("一", items.get(0).data().get("headline"));
        verify(querySourceResolver, never()).executeQuery(any(), anyMap(), any(), any(), any(), any());
    }

    @Test
    void unpublishedPinnedArticleSkippedInProductionButStillExcludedFromFill() {
        Article draft = article(102L, "草稿");
        draft.setStatus(Article.STATUS_DRAFT);
        when(articleService.select(101L)).thenReturn(article(101L, "置顶"));
        when(articleService.select(102L)).thenReturn(draft);
        when(querySourceResolver.executeQuery(eq(SITE), anyMap(), any(), any(), eq(4), eq(Set.of(101L, 102L))))
                .thenReturn(List.of());

        List<ResolvedItem> items = resolver.resolve(pinnedList(), Map.of("pinned", List.of(101L, 102L)),
                SITE, ctx());

        // 未发布置顶不渲染（不占名额），但其ID仍进入去重集合
        assertEquals(1, items.size());
        verify(querySourceResolver).executeQuery(eq(SITE), anyMap(), any(), any(), eq(4), eq(Set.of(101L, 102L)));
    }
}
