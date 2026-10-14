package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 种子令牌展开器测试（theme-seed-export R1）：导入/修复共用遍历——展开顺序
 * （asset → theme → 引用令牌）、严格模式抛异常（导入契约）、宽容模式原样保留（修复语义）、
 * 来源缺失时文章令牌不可解析、令牌字面量清点（检测口径）。
 */
class SeedTokenExpanderTest {
    private static final Long SITE = 1L;
    private static final String ORIGIN = "theme:demo";
    private static final String PREFIX = "/themes/1/demo";

    private SeedTokenExpander expander;
    private SeedAssetCopier.Session assets;

    @BeforeEach
    void setUp() {
        ChannelMapper channelMapper = mock(ChannelMapper.class);
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        Channel video = new Channel();
        video.setId(42L);
        when(channelMapper.findBySiteIdAndSlug(SITE, "video")).thenReturn(List.of(video));
        when(channelMapper.findBySiteIdAndSlug(eq(SITE), eq("ghost"))).thenReturn(List.of());
        Article welcome = new Article();
        welcome.setId(77L);
        when(articleMapper.selectByOrigin(SITE, ORIGIN, "a-welcome")).thenReturn(welcome);
        com.ujcms.cms.core.mapper.BlockMapper blockMapper = mock(com.ujcms.cms.core.mapper.BlockMapper.class);
        com.ujcms.cms.core.domain.Block focus = new com.ujcms.cms.core.domain.Block();
        focus.setId(40L);
        focus.setAlias("focus");
        when(blockMapper.listByScopeSiteIdAndAlias(SITE, "focus")).thenReturn(List.of(focus));
        com.ujcms.cms.ext.mapper.VoteMapper voteMapper = mock(com.ujcms.cms.ext.mapper.VoteMapper.class);
        com.ujcms.cms.ext.domain.Vote poll = new com.ujcms.cms.ext.domain.Vote();
        poll.setId(90L);
        when(voteMapper.selectByOrigin(SITE, ORIGIN, "v-poll")).thenReturn(poll);
        expander = new SeedTokenExpander(channelMapper, articleMapper, blockMapper, voteMapper);
        // 资源会话桩：{{asset:<relpath>}} → /uploads/theme-seed/1/demo/<relpath>（与真实会话确定性 URL 同构）
        assets = mock(SeedAssetCopier.Session.class);
        when(assets.expand(any())).thenAnswer(inv -> {
            String value = inv.getArgument(0);
            return value == null ? null
                    : value.replace("{{asset:img/a.svg}}", "/uploads/theme-seed/1/demo/img/a.svg");
        });
    }

    private SeedTokenExpander.ExpandContext ctx(boolean lenient) {
        return new SeedTokenExpander.ExpandContext(PREFIX, assets, SITE, ORIGIN, lenient);
    }

    @Test
    void expandsAssetThemeAndRefTokensRecursively() {
        Map<String, Object> settings = Map.of(
                "channel", "{{channel:video}}",
                "cover", Map.of("url", "{{asset:img/a.svg}}"),
                "items", List.of(Map.of("article", "{{article:a-welcome}}", "bg", "{{theme}}/assets/bg.png")));
        Map<String, Object> out = expander.expandData(settings, ctx(false));
        assertEquals("42", out.get("channel"));
        assertEquals("/uploads/theme-seed/1/demo/img/a.svg", ((Map<?, ?>) out.get("cover")).get("url"));
        Map<?, ?> item = (Map<?, ?>) ((List<?>) out.get("items")).get(0);
        assertEquals("77", item.get("article"));
        assertEquals(PREFIX + "/assets/bg.png", item.get("bg"));
    }

    @Test
    void strictModeThrowsOnUnresolvableRef() {
        SeedTokenExpander.SeedTokenException e = assertThrows(SeedTokenExpander.SeedTokenException.class,
                () -> expander.expandData(Map.of("channel", "{{channel:ghost}}"), ctx(false)));
        assertEquals("{{channel:ghost}}", e.getMessage());
    }

    @Test
    void lenientModeKeepsUnresolvableRefVerbatim() {
        // 修复语义：悬空令牌原样保留（同串内可解析令牌照常展开），绝不整体失败
        Map<String, Object> out = expander.expandData(
                Map.of("text", "{{channel:ghost}} 与 {{channel:video}}"), ctx(true));
        assertEquals("{{channel:ghost}} 与 42", out.get("text"));
    }

    @Test
    void expandsVoteTokenByOriginAndKeepsWhenOriginMissing() {
        // E4b / D30：{{vote:<originId>}} 来源绑定（文章同法）——按面板来源解析；无来源宽容保留
        Map<String, Object> out = expander.expandData(Map.of("vote", "{{vote:v-poll}}"), ctx(false));
        assertEquals("90", out.get("vote"));
        SeedTokenExpander.ExpandContext noOrigin =
                new SeedTokenExpander.ExpandContext(PREFIX, assets, SITE, null, true);
        assertEquals("{{vote:v-poll}}",
                expander.expandData(Map.of("vote", "{{vote:v-poll}}"), noOrigin).get("vote"));
        SeedTokenExpander.SeedTokenException e = assertThrows(SeedTokenExpander.SeedTokenException.class,
                () -> expander.expandData(Map.of("vote", "{{vote:v-ghost}}"), ctx(false)));
        assertEquals("{{vote:v-ghost}}", e.getMessage());
    }

    @Test
    void lenientModeKeepsArticleTokenWhenOriginMissing() {
        // 操作员面板无来源标记：{{article:originId}} 无解析域，宽容模式保留
        SeedTokenExpander.ExpandContext noOrigin =
                new SeedTokenExpander.ExpandContext(PREFIX, assets, SITE, null, true);
        Map<String, Object> out = expander.expandData(Map.of("article", "{{article:a-welcome}}"), noOrigin);
        assertEquals("{{article:a-welcome}}", out.get("article"));
    }

    @Test
    void listsTokensWithPaths() {
        Map<String, Object> settings = Map.of(
                "channel", "{{channel:video}}",
                "items", List.of(Map.of("img", "{{asset:img/a.svg}} {{theme}}")));
        List<String> tokens = SeedTokenExpander.listTokens(settings);
        assertEquals(3, tokens.size());
        assertTrue(tokens.contains("channel: {{channel:video}}"));
        assertTrue(tokens.contains("items[0].img: {{asset:img/a.svg}}"));
        assertTrue(tokens.contains("items[0].img: {{theme}}"));
    }

    @Test
    void plainValuesHaveNoTokens() {
        assertTrue(SeedTokenExpander.listTokens(Map.of(
                "title", "普通 {不是令牌} {{也不是}}",
                "count", 3,
                "flag", true)).isEmpty());
    }

    // ----- 别名映射遗留形（D20：令牌文法之前的手写种子形，字段定义驱动）-----

    private static Map<String, FieldDef> aliasDefs() {
        Map<String, FieldDef> defs = new LinkedHashMap<>();
        defs.put("channel", new FieldDef(Map.of("type", "channelRef")));
        defs.put("block", new FieldDef(Map.of("type", "blockRef")));
        defs.put("menu", new FieldDef(Map.of("type", "list", "of", Map.of(
                "type", "object", "fields", Map.of(
                        "target", Map.of("type", "channelRef"),
                        "extra", Map.of("type", "object", "fields", Map.of()))))));
        return defs;
    }

    @Test
    void scanAliasRefsIsFieldDefDriven() {
        Map<String, Object> settings = Map.of(
                "channel", Map.of("alias", "video"),
                // blockRef 的别名对象自 E4a / D24 起同样检出（{{block:}} 令牌文法就位，修复可归一为ID）
                "block", Map.of("alias", "focus"),
                "menu", List.of(Map.of(
                        "target", Map.of("alias", "news"),
                        // 未声明为引用类字段的 alias 键不误报（字段定义驱动的全部意义）
                        "extra", Map.of("alias", "innocent"))));
        List<String> hits = SeedTokenExpander.listAliasRefs(settings, aliasDefs());
        assertEquals(3, hits.size());
        assertTrue(hits.contains("channel: {\"alias\": \"video\"}"));
        assertTrue(hits.contains("block: {\"alias\": \"focus\"}"));
        assertTrue(hits.contains("menu[0].target: {\"alias\": \"news\"}"));
    }

    @Test
    void expandAliasRefsResolvesToIdAndKeepsUnresolvable() {
        Map<String, Object> settings = Map.of(
                "channel", Map.of("alias", "video"),
                "menu", List.of(Map.of("target", Map.of("alias", "ghost"))));
        Map<String, Object> out = expander.expandAliasRefs(settings, aliasDefs(), SITE);
        // 可解析 → 字符串ID（与令牌展开产物同形）；不可解析 → 原样保留（宽容语义）
        assertEquals("42", out.get("channel"));
        Map<?, ?> item = (Map<?, ?>) ((List<?>) out.get("menu")).get(0);
        assertEquals(Map.of("alias", "ghost"), item.get("target"));
    }
}
