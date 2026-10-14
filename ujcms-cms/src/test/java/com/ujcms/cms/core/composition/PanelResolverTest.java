package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.composition.sources.DataTemplateResolver;
import com.ujcms.cms.core.composition.sources.QuerySourceResolver;
import com.ujcms.cms.core.domain.Article;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 面板解析器 v2 测试（component-settings §3 / §4.2）：组件解析 → 设置类型化 → 数据模板求值 →
 * 契约装配（settings 契约值 + items 平铺）全链路（真实子解析器 + mock 服务），以及缓存命中 /
 * 预览绕过与不可解析组件的空面板降级。
 */
class PanelResolverTest {
    private static final Long SITE = 1L;
    private static final String THEME = "1/default";

    private ArticleService articleService;
    private ChannelService channelService;
    private PanelResolver resolver;

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        channelService = mock(ChannelService.class);
        BlockItemService blockItemService = mock(BlockItemService.class);
        ManifestService manifestService = mock(ManifestService.class);
        ThemeSettingsService themeSettingsService = mock(ThemeSettingsService.class);

        ShapeLoader loader = new ShapeLoader(new CompositionSchemas());
        Shape rankedList = loader.load("""
                { "name": "RankedList", "version": "2.0.0",
                  "settings": {
                    "title":   { "type": "text", "default": "热点新闻" },
                    "channel": { "type": "channelRef" },
                    "limit":   { "type": "number", "default": 2, "min": 1, "max": 20 }
                  },
                  "data": { "from": "article",
                            "where": { "channel": "$settings.channel", "isIncludeSubChannel": true },
                            "limit": "$settings.limit" },
                  "item": { "headline": { "type": "text", "required": true },
                            "link":     { "type": "link" } } }
                """, Shape.SOURCE_THEME_PREFIX + THEME, "x");
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape(THEME, rankedList, "{}", "x").build();
        when(manifestService.getSnapshot()).thenReturn(snapshot);

        QuerySourceResolver querySourceResolver = new QuerySourceResolver(articleService, channelService,
                blockItemService, mock(TagService.class), manifestService);
        resolver = new PanelResolver(manifestService,
                new SettingsResolver(articleService, channelService, mock(BlockService.class),
                        mock(com.ujcms.cms.ext.service.VoteService.class)),
                new DataTemplateResolver(querySourceResolver, articleService, channelService),
                themeSettingsService,
                new RefResolver(articleService, channelService),
                new PanelResolveCache(new SimpleMeterRegistry(), 60),
                new SimpleMeterRegistry());
    }

    private Placement panel(long id, String component, Map<String, Object> settings) {
        Placement bean = new Placement();
        bean.setId(id);
        bean.setSiteId(SITE);
        bean.setPageRef("home");
        bean.setSlotName("aside");
        bean.setPanelShape(component);
        bean.setSettings(new HashMap<>(settings));
        bean.setEnabled(true);
        return bean;
    }

    private Article article(long id, String title) {
        Article article = new Article();
        article.setId(id);
        article.setSiteId(SITE);
        article.setStatus(Article.STATUS_PUBLISHED);
        article.setTitle(title);
        return article;
    }

    private ResolveContext ctx() {
        return ResolveContext.template(THEME, Map.of("siteId", SITE));
    }

    @Test
    void resolvesSettingsRunsDataTemplateAndFlattensItems() {
        Channel news = new Channel();
        news.setId(12L);
        news.setSiteId(SITE);
        news.setSlug("news");
        news.setName("新闻");
        when(channelService.select(12L)).thenReturn(news);
        when(articleService.selectList(any(ArticleArgs.class), eq(0), anyInt()))
                .thenReturn(List.of(article(201L, "文章一"), article(202L, "文章二")));

        ResolvedRegion.Panel panel = resolver.resolve(panel(9001L, "RankedList", Map.of("channel", 12)), ctx());

        assertEquals("RankedList", panel.component());
        assertEquals("2.0.0", panel.version());
        assertTrue(panel.enabled());
        // 契约设置：缺省补齐 + 引用展开（§4.2）
        assertEquals("热点新闻", panel.settings().get("title"));
        assertEquals("新闻", ((Map<?, ?>) panel.settings().get("channel")).get("name"));
        assertEquals(2L, panel.settings().get("limit"));
        // 条目：item 字段平铺（约定投影 headline ← title，link ← url 包 {url}）
        assertEquals(2, panel.items().size());
        assertEquals("文章一", panel.items().get(0).get("headline"));
        // 查询按替换后的规格执行：channel=12（栏目ID直用，不走别名解析）+ isIncludeSubChannel → 后代过滤；limit=2
        ArgumentCaptor<ArticleArgs> captor = ArgumentCaptor.forClass(ArticleArgs.class);
        verify(articleService).selectList(captor.capture(), eq(0), eq(2));
        assertEquals(List.of(12L), List.copyOf(captor.getValue().getChannelAncestorIds()));
        verify(channelService, never()).findBySiteIdAndSlug(any(), any());
    }

    @Test
    void secondResolveHitsCacheAndPreviewBypasses() {
        when(articleService.selectList(any(ArticleArgs.class), eq(0), anyInt())).thenReturn(List.of());
        Placement panel = panel(9002L, "RankedList", Map.of());

        resolver.resolve(panel, ctx());
        resolver.resolve(panel, ctx());
        // 第二次命中缓存（同面板键）→ 来源执行只发生一次（§19.1）
        verify(articleService, times(1)).selectList(any(ArticleArgs.class), eq(0), anyInt());

        ResolveContext preview = ResolveContext.templatePreview(THEME, Map.of("siteId", SITE));
        resolver.resolve(panel, preview);
        resolver.resolve(panel, preview);
        // 预览从不进入缓存
        verify(articleService, times(3)).selectList(any(ArticleArgs.class), eq(0), anyInt());
    }

    @Test
    void unresolvableComponentYieldsEmptyPanel() {
        ResolvedRegion.Panel panel = resolver.resolve(panel(9003L, "Ghost", Map.of("x", 1)), ctx());
        assertEquals("Ghost", panel.component());
        assertEquals("", panel.version());
        assertTrue(panel.settings().isEmpty());
        assertTrue(panel.items().isEmpty());
    }

    @Test
    void contractKeepsExplicitNullForUnsetSetting() {
        when(articleService.selectList(any(ArticleArgs.class), eq(0), anyInt())).thenReturn(List.of());
        ResolvedRegion.Panel panel = resolver.resolve(panel(9004L, "RankedList", Map.of()), ctx());
        assertTrue(panel.settings().containsKey("channel"));
        assertNull(panel.settings().get("channel"));
    }
}
