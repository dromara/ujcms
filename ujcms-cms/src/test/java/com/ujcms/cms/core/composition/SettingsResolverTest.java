package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.service.VoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 面板设置解析测试（component-settings §3 类型化边界 / §4.2 契约）：缺省值补齐、channelRef 校验
 * （ID/别名形态、越站、失联占位）、number 钳制、enum 成员校验、引用列表规范化与契约展开。
 */
class SettingsResolverTest {
    private static final Long SITE = 1L;

    private ArticleService articleService;
    private ChannelService channelService;
    private BlockService blockService;
    private VoteService voteService;
    private SettingsResolver resolver;
    private final ShapeLoader loader = new ShapeLoader(new CompositionSchemas());

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        channelService = mock(ChannelService.class);
        blockService = mock(BlockService.class);
        voteService = mock(VoteService.class);
        resolver = new SettingsResolver(articleService, channelService, blockService, voteService);
    }

    private Shape rankedList() {
        return loader.load("""
                { "name": "RankedList", "version": "2.0.0",
                  "settings": {
                    "title":   { "type": "text", "max": 150, "default": "热点新闻" },
                    "moreUrl": { "type": "link" },
                    "channel": { "type": "channelRef" },
                    "limit":   { "type": "number", "default": 9, "min": 1, "max": 20 },
                    "orderBy": { "type": "enum", "default": "weekViews desc",
                                 "values": ["weekViews desc", "views desc", "publishedAt desc"] }
                  },
                  "data": { "from": "article", "where": { "channel": "$settings.channel" },
                            "orderBy": "$settings.orderBy", "limit": "$settings.limit" },
                  "item": { "headline": { "type": "text", "required": true } } }
                """, "theme:1/test", "x");
    }

    private Channel channel(long id, String slug, String name) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setSiteId(SITE);
        channel.setSlug(slug);
        channel.setName(name);
        return channel;
    }

    @Test
    void appliesDefaultsAndKeepsUnsetAsExplicitNull() {
        SettingsResolver.Resolved resolved = resolver.resolve(rankedList(), Map.of(), SITE);
        assertEquals("热点新闻", resolved.values().get("title"));
        assertEquals(9L, resolved.values().get("limit"));
        assertEquals("weekViews desc", resolved.values().get("orderBy"));
        // 未设且无缺省：引擎值与契约值都是显式 null（§4.2——键集 = 声明的设置名）
        assertTrue(resolved.contract().containsKey("moreUrl"));
        assertNull(resolved.contract().get("moreUrl"));
        assertNull(resolved.values().get("channel"));
    }

    @Test
    void channelRefByIdIsValidatedAndExpandedToSummary() {
        when(channelService.select(12L)).thenReturn(channel(12L, "news", "新闻"));
        SettingsResolver.Resolved resolved = resolver.resolve(rankedList(), Map.of("channel", 12), SITE);
        // 引擎值：经存在性校验的裸ID
        assertEquals(12L, resolved.values().get("channel"));
        // 契约值：摘要对象（§4.2）
        Map<?, ?> summary = (Map<?, ?>) resolved.contract().get("channel");
        assertEquals("12", summary.get("id"));
        assertEquals("news", summary.get("alias"));
        assertEquals("新闻", summary.get("name"));
    }

    @Test
    void channelRefByAliasResolvesSiteScoped() {
        // 种子缺省值形态（§4.1：{"alias": "video"}）按站点解析
        when(channelService.findBySiteIdAndSlug(SITE, "video")).thenReturn(channel(31L, "video", "视频"));
        SettingsResolver.Resolved resolved =
                resolver.resolve(rankedList(), Map.of("channel", Map.of("alias", "video")), SITE);
        assertEquals(31L, resolved.values().get("channel"));
    }

    @Test
    void channelRefCrossSiteOrDeletedIsEngineNullWithContractStub() {
        // 越站栏目：引擎按缺失处理（不跨站取数）；契约保留 {id,deleted} 占位供操作员发现
        Channel foreign = channel(55L, "x", "他站");
        foreign.setSiteId(2L);
        when(channelService.select(55L)).thenReturn(foreign);
        SettingsResolver.Resolved crossSite = resolver.resolve(rankedList(), Map.of("channel", 55), SITE);
        assertNull(crossSite.values().get("channel"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) crossSite.contract().get("channel")).get("deleted"));
        // 已删除栏目：同上
        when(channelService.select(66L)).thenReturn(null);
        SettingsResolver.Resolved deleted = resolver.resolve(rankedList(), Map.of("channel", 66), SITE);
        assertNull(deleted.values().get("channel"));
        assertEquals("66", ((Map<?, ?>) deleted.contract().get("channel")).get("id"));
    }

    private Shape blockList() {
        return loader.load("""
                { "name": "BlockList", "version": "2.0.0",
                  "settings": {
                    "title": { "type": "text", "max": 150 },
                    "block": { "type": "blockRef", "default": { "alias": "focus" } },
                    "limit": { "type": "number", "default": 6, "min": 1, "max": 12 }
                  },
                  "data": { "from": "blockItem", "where": { "blockId": "$settings.block" },
                            "limit": "$settings.limit" },
                  "item": { "title": { "type": "text", "required": true } } }
                """, "theme:1/test", "x");
    }

    private Block block(long id, String alias, String name, Long siteId) {
        Block block = new Block();
        block.setId(id);
        block.setSiteId(siteId);
        block.setAlias(alias);
        block.setName(name);
        return block;
    }

    @Test
    void blockRefByIdIsValidatedAndExpandedToSummary() {
        when(blockService.select(7L)).thenReturn(block(7L, "focus", "焦点图", SITE));
        SettingsResolver.Resolved resolved = resolver.resolve(blockList(), Map.of("block", 7), SITE);
        // 引擎值：经存在性校验的裸ID（数据模板 where.blockId）
        assertEquals(7L, resolved.values().get("block"));
        Map<?, ?> summary = (Map<?, ?>) resolved.contract().get("block");
        assertEquals("7", summary.get("id"));
        assertEquals("focus", summary.get("alias"));
        assertEquals("焦点图", summary.get("name"));
    }

    @Test
    void blockRefByAliasDefaultResolvesScopeSiteScoped() {
        // 种子缺省值形态（§4.1 同型：{"alias": "focus"}）——未设值回退缺省，再按站点可见范围解析
        when(blockService.findByScopeSiteIdAndAlias(SITE, "focus")).thenReturn(block(7L, "focus", "焦点图", SITE));
        SettingsResolver.Resolved resolved = resolver.resolve(blockList(), Map.of(), SITE);
        assertEquals(7L, resolved.values().get("block"));
    }

    @Test
    void blockRefCrossSitePrivateIsEngineNullButGlobalIsAccepted() {
        // 他站私有区块：引擎按缺失处理；契约保留占位
        when(blockService.select(21L)).thenReturn(block(21L, "x", "他站", 2L));
        SettingsResolver.Resolved crossSite = resolver.resolve(blockList(), Map.of("block", 21), SITE);
        assertNull(crossSite.values().get("block"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) crossSite.contract().get("block")).get("deleted"));
        // 他站全局共享区块：可用（与控制台区块目录可见范围同口径）
        Block global = block(22L, "g", "共享", 2L);
        global.setScope(com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL);
        when(blockService.select(22L)).thenReturn(global);
        assertEquals(22L, resolver.resolve(blockList(), Map.of("block", 22), SITE).values().get("block"));
    }

    private Shape voteShape() {
        return loader.load("""
                { "name": "Vote", "version": "1.0.0",
                  "settings": {
                    "title": { "type": "text", "max": 150, "default": "投票" },
                    "vote":  { "type": "voteRef" },
                    "showResults": { "type": "boolean", "default": true }
                  } }
                """, "theme:1/test", "x");
    }

    private Vote vote(long id, String title, Long siteId) {
        Vote vote = new Vote();
        vote.setId(id);
        vote.setSiteId(siteId);
        vote.setTitle(title);
        return vote;
    }

    @Test
    void voteRefByIdIsValidatedAndExpandedToSummary() {
        when(voteService.select(9L)).thenReturn(vote(9L, "年度评选", SITE));
        SettingsResolver.Resolved resolved = resolver.resolve(voteShape(), Map.of("vote", 9), SITE);
        // 引擎值：经存在性校验的裸ID透传（模板片段 [@Vote id=…] 自取实体，RefResolver 不解析）
        assertEquals(9L, resolved.values().get("vote"));
        Map<?, ?> summary = (Map<?, ?>) resolved.contract().get("vote");
        assertEquals("9", summary.get("id"));
        assertEquals("年度评选", summary.get("title"));
    }

    @Test
    void voteRefUnsetStaysNullForLatestVoteFallback() {
        // 未设值（微区域的种子形态）：引擎/契约均为显式 null——片段回退最新投票
        SettingsResolver.Resolved resolved = resolver.resolve(voteShape(), Map.of(), SITE);
        assertTrue(resolved.contract().containsKey("vote"));
        assertNull(resolved.values().get("vote"));
        assertNull(resolved.contract().get("vote"));
        assertEquals(Boolean.TRUE, resolved.values().get("showResults"));
    }

    @Test
    void voteRefCrossSiteOrDeletedIsEngineNullWithContractStub() {
        // 越站投票：引擎按缺失处理；契约保留 {id,deleted} 占位供操作员发现
        when(voteService.select(31L)).thenReturn(vote(31L, "他站", 2L));
        SettingsResolver.Resolved crossSite = resolver.resolve(voteShape(), Map.of("vote", 31), SITE);
        assertNull(crossSite.values().get("vote"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) crossSite.contract().get("vote")).get("deleted"));
        // 已删除投票：同上
        when(voteService.select(32L)).thenReturn(null);
        SettingsResolver.Resolved deleted = resolver.resolve(voteShape(), Map.of("vote", 32), SITE);
        assertNull(deleted.values().get("vote"));
        assertEquals("32", ((Map<?, ?>) deleted.contract().get("vote")).get("id"));
    }

    @Test
    void numberIsClampedByDeclaredBounds() {
        // §3：number 按 min/max 钳制——超界收敛而非拒绝
        assertEquals(20L, resolver.resolve(rankedList(), Map.of("limit", 999), SITE).values().get("limit"));
        assertEquals(1L, resolver.resolve(rankedList(), Map.of("limit", -5), SITE).values().get("limit"));
        assertEquals(15L, resolver.resolve(rankedList(), Map.of("limit", "15"), SITE).values().get("limit"));
        // 不可解析（类型化边界拒之门外，非空故不回退缺省——下游取数用默认上限）→ 引擎值 null；
        // 保存期字段校验本就会拒绝 number 字段的非数字值，此为纵深防御
        assertNull(resolver.resolve(rankedList(), Map.of("limit", "abc"), SITE).values().get("limit"));
    }

    @Test
    void enumMembershipIsEnforced() {
        assertEquals("views desc",
                resolver.resolve(rankedList(), Map.of("orderBy", "views desc"), SITE).values().get("orderBy"));
        // 非成员 → 归缺失（不静默替换），下游 orderBy 回退默认排序
        assertNull(resolver.resolve(rankedList(), Map.of("orderBy", "secret_col desc"), SITE)
                .values().get("orderBy"));
    }

    @Test
    void refListNormalizesToValidIdsForEngineAndSummariesForContract() {
        Shape shape = loader.load("""
                { "name": "PinnedList", "version": "1.0.0",
                  "settings": { "pinned": { "type": "list", "of": { "type": "articleRef" } } },
                  "data": { "from": "article", "pinned": "$settings.pinned" },
                  "item": { "headline": { "type": "text" } } }
                """, "theme:1/test", "x");
        Article ok = new Article();
        ok.setId(101L);
        ok.setSiteId(SITE);
        when(articleService.select(101L)).thenReturn(ok);
        when(articleService.select(102L)).thenReturn(null);

        SettingsResolver.Resolved resolved =
                resolver.resolve(shape, Map.of("pinned", Arrays.asList(101, 102)), SITE);
        // 引擎值：仅有效ID（置顶装载与填充去重用）
        assertEquals(List.of(101L), resolved.values().get("pinned"));
        // 契约值：有效摘要 + 失联占位
        List<?> contract = (List<?>) resolved.contract().get("pinned");
        assertEquals(2, contract.size());
        assertEquals(Boolean.TRUE, ((Map<?, ?>) contract.get(1)).get("deleted"));
    }

    @Test
    void inlineItemsListPassesThrough() {
        Shape shape = loader.load("""
                { "name": "Promo", "version": "1.0.0",
                  "settings": { "slides": { "type": "list", "of": { "type": "object",
                                "fields": { "t": { "type": "text" } } } } },
                  "data": { "items": "$settings.slides" },
                  "item": { "t": { "type": "text" } } }
                """, "theme:1/test", "x");
        List<Map<String, Object>> slides = List.of(Map.of("t", "一"), Map.of("t", "二"));
        SettingsResolver.Resolved resolved = resolver.resolve(shape, Map.of("slides", slides), SITE);
        assertEquals(slides, resolved.values().get("slides"));
        assertEquals(slides, resolved.contract().get("slides"));
    }

    @Test
    void staleRefsListsDeadTargetsPerRefKindOnTheSharedBoundary() {
        // 失效引用（theme-marketplace D24 唯一口径——健康页与种子导入共用）：四种引用型设置 + 引用列表按元素；
        // 健康引用不入列；未设值回退的缺省别名不可解析同样入列；非引用型设置不参与
        Shape shape = loader.load("""
                { "name": "Mixed", "version": "1.0.0",
                  "settings": {
                    "channel": { "type": "channelRef", "default": { "alias": "video" } },
                    "block":   { "type": "blockRef" },
                    "vote":    { "type": "voteRef" },
                    "hero":    { "type": "articleRef" },
                    "picks":   { "type": "list", "of": { "type": "articleRef" } },
                    "limit":   { "type": "number", "default": 3 }
                  },
                  "data": { "from": "article", "where": { "channel": "$settings.channel" },
                            "pinned": "$settings.picks" },
                  "item": { "headline": { "type": "text" } } }
                """, "theme:1/test", "x");
        when(channelService.findBySiteIdAndSlug(SITE, "video")).thenReturn(null);
        when(blockService.select(888L)).thenReturn(null);
        when(voteService.select(7L)).thenReturn(null);
        Article alive = new Article();
        alive.setId(5L);
        alive.setSiteId(SITE);
        when(articleService.select(5L)).thenReturn(alive);
        when(articleService.select(6L)).thenReturn(null);
        Map<String, Object> raw = new java.util.HashMap<>();
        raw.put("block", "888");
        raw.put("vote", 7);
        raw.put("hero", "5");
        raw.put("picks", Arrays.asList(5, 6));
        raw.put("limit", 99);

        List<SettingsResolver.StaleRef> stale = resolver.staleRefs(shape, raw, SITE);

        Map<String, SettingsResolver.StaleRef> bySetting = new java.util.HashMap<>();
        stale.forEach(ref -> bySetting.put(ref.setting(), ref));
        assertEquals(4, stale.size());
        assertEquals(SettingsResolver.KIND_CHANNEL, bySetting.get("channel").kind());
        assertEquals("video", bySetting.get("channel").targetRef());
        assertEquals(SettingsResolver.KIND_BLOCK, bySetting.get("block").kind());
        assertEquals("888", bySetting.get("block").targetRef());
        assertEquals(SettingsResolver.KIND_VOTE, bySetting.get("vote").kind());
        assertEquals("7", bySetting.get("vote").targetRef());
        assertEquals(SettingsResolver.KIND_ARTICLE, bySetting.get("picks").kind());
        assertEquals("6", bySetting.get("picks").targetRef());
        assertNull(bySetting.get("hero"));

        // 目标重建为新ID 12（如 移除 → 导入 后）：显式引用 12 即健康，不再入列
        when(channelService.select(12L)).thenReturn(channel(12L, "video", "视频"));
        raw.put("channel", "12");
        assertTrue(resolver.staleRefs(shape, raw, SITE).stream().noneMatch(ref -> "channel".equals(ref.setting())));
    }
}
