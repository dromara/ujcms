package com.ujcms.cms.core.composition.sources;

import com.ujcms.cms.core.composition.CompositionSchemas;
import com.ujcms.cms.core.composition.CtxParams;
import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.RegistrySnapshot;
import com.ujcms.cms.core.composition.Shape;
import com.ujcms.cms.core.composition.ShapeLoader;
import com.ujcms.cms.core.composition.ResolveContext;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.service.args.BlockItemArgs;
import com.ujcms.cms.core.service.args.ChannelArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 查询引擎测试：$ctx 替换与缺失键策略（skip/empty）、来源实体分派、单字段排序翻译。
 * 投影内容（按类型包装、约定默认）由 {@link SourceProjectorTest} 独立覆盖；此处以 verify 校验查询行为，
 * 避免脆弱地 mock Lombok 实体的 getter（与既有测试套件一致——只 mock 服务与 mapper）。
 * Phase D 起由 {@code DataTemplateResolver} 驱动（$settings 替换后的查询规格 + 目标组件名）。
 */
class QuerySourceResolverTest {
    private static final Long SITE_ID = 1L;
    private static final String THEME = "1/liquid-default";
    private static final String SHAPE = "LinkRow";

    private ArticleService articleService;
    private ChannelService channelService;
    private BlockItemService blockItemService;
    private QuerySourceResolver resolver;

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        channelService = mock(ChannelService.class);
        blockItemService = mock(BlockItemService.class);
        TagService tagService = mock(TagService.class);
        ManifestService manifestService = mock(ManifestService.class);

        ShapeLoader loader = new ShapeLoader(new CompositionSchemas());
        Shape linkRow = loader.load("""
                { "name": "LinkRow", "version": "1.0.0", "titleField": "label",
                  "item": {
                    "label": { "type": "text", "max": 150, "required": true },
                    "link":  { "type": "link", "required": true } } }
                """, Shape.SOURCE_THEME_PREFIX + THEME, "link-row.json");
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape(THEME, linkRow, "{}", "link-row.json").build();
        when(manifestService.getSnapshot()).thenReturn(snapshot);

        resolver = new QuerySourceResolver(articleService, channelService, blockItemService, tagService,
                manifestService);
    }

    private ResolveContext ctx(Map<String, Object> ctxParams) {
        return ResolveContext.template(THEME, ctxParams);
    }

    private List<ResolvedItem> execute(Map<String, Object> spec, ResolveContext ctx) {
        return resolver.executeQuery(SITE_ID, spec, SHAPE, ctx, null, Set.of());
    }

    @Test
    void ctxChannelSubstitutionResolvesAliasToChannelFilterThenQueries() {
        Channel channel = new Channel();
        channel.setId(100L);
        when(channelService.findBySiteIdAndSlug(SITE_ID, "news")).thenReturn(channel);
        when(articleService.selectList(any(ArticleArgs.class), eq(0), anyInt())).thenReturn(List.of());

        Map<String, Object> spec = Map.of("from", "article", "where", Map.of("channel", "$ctx.channel"));
        Map<String, Object> ctxParams = Map.of(CtxParams.CHANNEL, "news", CtxParams.SITE_ID, SITE_ID);

        execute(spec, ctx(ctxParams));

        verify(channelService).findBySiteIdAndSlug(SITE_ID, "news");
        verify(articleService).selectList(any(ArticleArgs.class), eq(0), anyInt());
    }

    @Test
    void missingCtxKeySkipPolicyDropsFilterAndStillQueries() {
        when(articleService.selectList(any(ArticleArgs.class), eq(0), anyInt())).thenReturn(List.of());
        // missing 默认 skip：$ctx.channel 不在上下文 → 删除该过滤，照常查询
        Map<String, Object> spec = Map.of("from", "article", "where", Map.of("channel", "$ctx.channel"));

        execute(spec, ctx(Map.of()));

        verify(channelService, never()).findBySiteIdAndSlug(any(), any());
        verify(articleService).selectList(any(ArticleArgs.class), eq(0), anyInt());
    }

    @Test
    void missingCtxKeyEmptyPolicyReturnsEmptyWithoutQuerying() {
        Map<String, Object> spec = Map.of("from", "article", "missing", "empty",
                "where", Map.of("channel", "$ctx.channel"));

        assertTrue(execute(spec, ctx(Map.of())).isEmpty());

        verify(articleService, never()).selectList(any(ArticleArgs.class), anyInt(), anyInt());
    }

    @Test
    void unknownChannelAliasYieldsEmptyWithoutQuerying() {
        when(channelService.findBySiteIdAndSlug(SITE_ID, "ghost")).thenReturn(null);
        Map<String, Object> spec = Map.of("from", "article", "where", Map.of("channel", "ghost"));

        assertTrue(execute(spec, ctx(Map.of())).isEmpty());

        verify(articleService, never()).selectList(any(ArticleArgs.class), anyInt(), anyInt());
    }

    @Test
    void channelSourceDispatchesToChannelService() {
        when(channelService.selectList(any(ChannelArgs.class), eq(0), anyInt())).thenReturn(List.of());
        Map<String, Object> spec = Map.of("from", "channel", "where", Map.of("rootOnly", true));

        execute(spec, ctx(Map.of()));

        verify(channelService).selectList(any(ChannelArgs.class), eq(0), anyInt());
        verify(articleService, never()).selectList(any(ArticleArgs.class), anyInt(), anyInt());
    }

    @Test
    void blockItemSourceAppliesEnabledAndActiveWindow() {
        when(blockItemService.selectList(any(BlockItemArgs.class), eq(0), anyInt())).thenReturn(List.of());
        Map<String, Object> spec = Map.of("from", "blockItem", "where", Map.of("blockId", 42));

        execute(spec, ctx(Map.of()));

        ArgumentCaptor<BlockItemArgs> captor = ArgumentCaptor.forClass(BlockItemArgs.class);
        verify(blockItemService).selectList(captor.capture(), eq(0), anyInt());
        Map<String, Object> qm = captor.getValue().getQueryMap();
        assertEquals(42L, qm.get("EQ_blockId_Long"));
        assertEquals(Boolean.TRUE, qm.get("EQ_enabled_Boolean"));
        // V11.1 isActive 窗口：start <= now（或为空）且 end > now（或为空）
        assertTrue(qm.containsKey("LE_0start_startDate_DateTime"));
        assertTrue(qm.containsKey("GT_0end_endDate_DateTime"));
    }

    @Test
    void specLimitCapsFetchAndLimitOverrideWins() {
        when(articleService.selectList(any(ArticleArgs.class), eq(0), anyInt())).thenReturn(List.of());
        // spec limit 生效
        execute(Map.of("from", "article", "limit", 3), ctx(Map.of()));
        verify(articleService).selectList(any(ArticleArgs.class), eq(0), eq(3));
        // limitOverride 覆盖 spec（置顶填充按剩余位计算）
        resolver.executeQuery(SITE_ID, Map.of("from", "article", "limit", 3), SHAPE, ctx(Map.of()), 2, Set.of());
        verify(articleService).selectList(any(ArticleArgs.class), eq(0), eq(2));
        // 均未声明 → 默认 20
        execute(Map.of("from", "article"), ctx(Map.of()));
        verify(articleService).selectList(any(ArticleArgs.class), eq(0), eq(20));
    }

    @Test
    void orderTokenTranslatesDslAndFallsBackOnUnknownField() {
        assertEquals("publishDate_desc", QuerySourceResolver.orderToken("article", "publishedAt desc"));
        assertEquals("publishDate", QuerySourceResolver.orderToken("article", "publishDate asc"));
        assertEquals("publishDate_desc", QuerySourceResolver.orderToken("article", ""));
        // 非法字段（防注入）→ 回退默认
        assertEquals("publishDate_desc", QuerySourceResolver.orderToken("article", "secret_col desc"));
        assertEquals("name", QuerySourceResolver.orderToken("channel", "name"));
        assertEquals("order", QuerySourceResolver.orderToken("blockItem", "order"));
    }

    @Test
    void orderTokenTranslatesArticleStatFields() {
        // component-settings §12.3 #1：浏览量统计排序（ArticleExt 列，@articleExt-* 查询令牌）——
        // 「本周最热」等排行组件的排序由此表达，与模板 [@ArticleList orderBy='@articleExt-weekViews_desc'] 同机制
        assertEquals("@articleExt-weekViews_desc", QuerySourceResolver.orderToken("article", "weekViews desc"));
        assertEquals("@articleExt-monthViews_desc", QuerySourceResolver.orderToken("article", "monthViews desc"));
        assertEquals("@articleExt-yearViews_desc", QuerySourceResolver.orderToken("article", "yearViews desc"));
        assertEquals("@articleExt-dayViews_desc", QuerySourceResolver.orderToken("article", "dayViews desc"));
        assertEquals("@articleExt-views_desc", QuerySourceResolver.orderToken("article", "views desc"));
        assertEquals("@articleExt-views", QuerySourceResolver.orderToken("article", "views"));
        // 统计排序仅文章来源；其他来源仍回退默认
        assertEquals("order", QuerySourceResolver.orderToken("channel", "weekViews desc"));
    }
}
