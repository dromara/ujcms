package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleI18n;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.mapper.ArticleI18nMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ArticleI18nService} 写不变量与批量读测试（multilingual P5，translation-links.md §3；
 * 过期锚点与新增译文见 translation-workflow.md）：
 * 同语言组、异站点、一站一员（指名冲突，状态无关）、v1 不隐式并组；relink 集合式写入语义；
 * listMembersByArticleIds 分桶；建链锚点与钳制；createTranslation 草稿副本；
 * findUnlinkedMatch 别名优先/标题回退/歧义判定（批量翻译先关联后新建）。
 */
class ArticleI18nServiceTest {
    private ArticleI18nMapper mapper;
    private ArticleService articleService;
    private ArticleI18nService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ArticleI18nMapper.class);
        articleService = mock(ArticleService.class);
        SnowflakeSequence snowflakeSequence = mock(SnowflakeSequence.class);
        when(snowflakeSequence.nextId()).thenReturn(999L);
        // 锚点组件用真实实现（仅依赖同一 mapper mock），连带覆盖建链钳制逻辑
        service = new ArticleI18nService(articleService, new ArticleI18nAnchorService(mapper), mapper,
                snowflakeSequence);
    }

    /**
     * @param langParentId 站点的语言组指针：主站点传 {@code null}
     */
    private static Site site(long id, Long langParentId) {
        Site site = new Site();
        site.setId(id);
        site.setLangParentId(langParentId);
        return site;
    }

    private static Article article(long id, Site site) {
        Article article = new Article();
        article.setId(id);
        article.setSiteId(site.getId());
        article.setSite(site);
        return article;
    }

    /**
     * 语言组：站点1（主）与站点2（变体）；文章10（站点1）与文章20（站点2）
     */
    private final Site master = site(1L, null);
    private final Site variant = site(2L, 1L);
    private final Article a10 = article(10L, master);
    private final Article a20 = article(20L, variant);

    // region link

    @Test
    void linkSelfIsNoOp() {
        service.link(a10, a10);
        verify(mapper, never()).insert(any());
    }

    @Test
    void linkSameSiteRejected() {
        Article sameSite = article(11L, master);
        assertThrows(Http400Exception.class, () -> service.link(a10, sameSite));
    }

    @Test
    void linkAcrossLanguageGroupsRejected() {
        Article foreign = article(30L, site(9L, null));
        assertThrows(Http400Exception.class, () -> service.link(a10, foreign));
    }

    @Test
    void linkBothUngroupedCreatesGroup() {
        service.link(a10, a20);
        ArgumentCaptor<ArticleI18n> captor = ArgumentCaptor.forClass(ArticleI18n.class);
        verify(mapper, times(2)).insert(captor.capture());
        assertEquals(999L, captor.getAllValues().get(0).getGroupId());
        assertEquals(999L, captor.getAllValues().get(1).getGroupId());
        assertEquals(10L, captor.getAllValues().get(0).getArticleId());
        assertEquals(20L, captor.getAllValues().get(1).getArticleId());
    }

    @Test
    void linkJoinsExistingGroup() {
        when(mapper.select(10L)).thenReturn(new ArticleI18n(10L, 5L));
        when(mapper.findMemberArticleByGroupAndSiteId(10L, 2L)).thenReturn(null);
        service.link(a10, a20);
        ArgumentCaptor<ArticleI18n> captor = ArgumentCaptor.forClass(ArticleI18n.class);
        verify(mapper).insert(captor.capture());
        assertEquals(20L, captor.getValue().getArticleId());
        assertEquals(5L, captor.getValue().getGroupId());
    }

    /**
     * 一站一员：组内该站点已被其它文章占用 → 指名冲突
     */
    @Test
    void linkSiteConflictNamesOccupant() {
        when(mapper.select(10L)).thenReturn(new ArticleI18n(10L, 5L));
        Article occupant = article(21L, variant);
        when(mapper.findMemberArticleByGroupAndSiteId(10L, 2L)).thenReturn(occupant);
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.link(a10, a20));
        assertTrue(String.valueOf(e.getCode()).contains("21"), e.getCode());
        verify(mapper, never()).insert(any());
    }

    @Test
    void linkSameGroupIsNoOp() {
        when(mapper.select(10L)).thenReturn(new ArticleI18n(10L, 5L));
        when(mapper.select(20L)).thenReturn(new ArticleI18n(20L, 5L));
        service.link(a10, a20);
        verify(mapper, never()).insert(any());
    }

    /**
     * v1 不隐式并组：双方各自已入组 → 拒绝
     */
    @Test
    void linkBothGroupedRejected() {
        when(mapper.select(10L)).thenReturn(new ArticleI18n(10L, 5L));
        when(mapper.select(20L)).thenReturn(new ArticleI18n(20L, 6L));
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.link(a10, a20));
        assertTrue(String.valueOf(e.getCode()).contains("merging"), e.getCode());
    }

    // endregion

    // region relink（集合式写入）

    @Test
    void relinkNullUnlinksOccupant() {
        Article occupant = article(21L, variant);
        when(mapper.findMemberArticleByGroupAndSiteId(10L, 2L)).thenReturn(occupant);
        Map<Long, Long> map = new HashMap<>();
        map.put(2L, null);
        service.relink(a10, map);
        verify(mapper).deleteByArticleId(21L);
        verify(mapper, never()).insert(any());
    }

    @Test
    void relinkSameTargetIsNoOp() {
        when(mapper.findMemberArticleByGroupAndSiteId(10L, 2L)).thenReturn(a20);
        service.relink(a10, Map.of(2L, 20L));
        verify(mapper, never()).deleteByArticleId(anyLong());
        verify(mapper, never()).insert(any());
    }

    /**
     * 槽位替换：站点2已链接文章21，改链文章20 → 先解除21，再链入20。
     * link 内部会二次校验占位：生产中同事务 DML 已清缓存、二次查询命中删除后的库态，
     * mock 以连续打桩模拟（第一次返回占位者，解除后返回 null）
     */
    @Test
    void relinkReplacesOccupant() {
        Article occupant = article(21L, variant);
        when(mapper.findMemberArticleByGroupAndSiteId(10L, 2L)).thenReturn(occupant).thenReturn(null);
        when(articleService.select(20L)).thenReturn(a20);
        when(mapper.select(10L)).thenReturn(new ArticleI18n(10L, 5L));
        service.relink(a10, Map.of(2L, 20L));
        verify(mapper).deleteByArticleId(21L);
        ArgumentCaptor<ArticleI18n> captor = ArgumentCaptor.forClass(ArticleI18n.class);
        verify(mapper).insert(captor.capture());
        assertEquals(20L, captor.getValue().getArticleId());
        assertEquals(5L, captor.getValue().getGroupId());
    }

    @Test
    void relinkOwnSiteRejected() {
        assertThrows(Http400Exception.class, () -> service.relink(a10, Map.of(1L, 11L)));
    }

    @Test
    void relinkTargetSiteMismatchRejected() {
        when(articleService.select(30L)).thenReturn(article(30L, site(9L, null)));
        assertThrows(Http400Exception.class, () -> service.relink(a10, Map.of(2L, 30L)));
    }

    @Test
    void relinkMissingTargetRejected() {
        when(articleService.select(30L)).thenReturn(null);
        assertThrows(Http400Exception.class, () -> service.relink(a10, Map.of(2L, 30L)));
    }

    // endregion

    // region 批量读

    @Test
    void listMembersByArticleIdsBucketsByGroup() {
        when(mapper.listByArticleIds(List.of(10L, 30L))).thenReturn(List.of(new ArticleI18n(10L, 5L)));
        when(mapper.listByGroupIds(List.of(5L)))
                .thenReturn(List.of(new ArticleI18n(10L, 5L), new ArticleI18n(20L, 5L)));
        when(mapper.listArticleByGroupIds(List.of(5L))).thenReturn(List.of(a10, a20));
        Map<Long, List<Article>> result = service.listMembersByArticleIds(List.of(10L, 30L));
        assertEquals(1, result.size());
        assertEquals(2, result.get(10L).size());
    }

    @Test
    void listMembersByArticleIdsEmptyWhenNoneLinked() {
        when(mapper.listByArticleIds(any())).thenReturn(List.of());
        assertTrue(service.listMembersByArticleIds(List.of(10L)).isEmpty());
        verify(mapper, never()).listByGroupIds(any());
    }

    // endregion

    // region 过期锚点（translation-workflow.md）

    @Test
    void linkFreshPairWritesAnchors() {
        OffsetDateTime m10 = OffsetDateTime.now().minusDays(3);
        OffsetDateTime m20 = OffsetDateTime.now().minusDays(2);
        a10.setModified(m10);
        a20.setModified(m20);
        service.link(a10, a20);
        ArgumentCaptor<ArticleI18n> captor = ArgumentCaptor.forClass(ArticleI18n.class);
        verify(mapper, times(2)).insert(captor.capture());
        assertEquals(m10, captor.getAllValues().get(0).getContentDate());
        assertEquals(m20, captor.getAllValues().get(1).getContentDate());
        assertTrue(captor.getAllValues().get(0).getSyncedDate().isAfter(m20));
        assertTrue(captor.getAllValues().get(1).getSyncedDate().isAfter(m20));
    }

    /**
     * 建链钳制：加入既有组的成员 content_date_ 取 min(自身 modified_, 既有成员最小 synced_date_)，
     * 否则近期编辑过的新成员一入组就把既有成员标为“可能过期”，与“关联=断言当前一致”相悖
     */
    @Test
    void linkJoinClampsContentDate() {
        when(mapper.select(10L)).thenReturn(new ArticleI18n(10L, 5L));
        OffsetDateTime oldSynced = OffsetDateTime.now().minusDays(10);
        when(mapper.listByGroupIds(List.of(5L))).thenReturn(List.of(
                new ArticleI18n(10L, 5L, OffsetDateTime.now().minusDays(11), oldSynced)));
        a20.setModified(OffsetDateTime.now());
        service.link(a10, a20);
        ArgumentCaptor<ArticleI18n> captor = ArgumentCaptor.forClass(ArticleI18n.class);
        verify(mapper).insert(captor.capture());
        assertEquals(oldSynced, captor.getValue().getContentDate());
    }

    /**
     * P5 回归：占位检查必须状态无关——过滤版查不到草稿成员，会突破一站一员
     */
    @Test
    void linkSiteConflictSeesDraftOccupant() {
        when(mapper.select(10L)).thenReturn(new ArticleI18n(10L, 5L));
        Article draftOccupant = article(21L, variant);
        draftOccupant.setStatus(Article.STATUS_DRAFT);
        when(mapper.findArticleByGroupAndSiteId(10L, 2L)).thenReturn(null);
        when(mapper.findMemberArticleByGroupAndSiteId(10L, 2L)).thenReturn(draftOccupant);
        assertThrows(Http400Exception.class, () -> service.link(a10, a20));
        verify(mapper, never()).insert(any());
    }

    // endregion

    // region createTranslation（新增译文）

    private static Channel channel(long id, long siteId) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setSiteId(siteId);
        return channel;
    }

    private static User user(long id, long orgId) {
        User user = new User();
        user.setId(id);
        user.setOrgId(orgId);
        return user;
    }

    @Test
    void createTranslationBuildsLinkedDraft() {
        a10.setTitle("标题");
        a10.getExt().setViews(9L);
        a10.getExt().setStaticFile("/static/a.html");
        doAnswer(invocation -> {
            Article bean = invocation.getArgument(0);
            bean.setId(77L);
            return null;
        }).when(articleService).insert(any(Article.class), anyLong(), anyList());
        Article created = service.createTranslation(a10, variant, channel(200L, 2L), user(100L, 1L));
        assertEquals(77L, created.getId());
        assertEquals(Article.STATUS_DRAFT, created.getStatus());
        assertEquals(2L, created.getSiteId());
        assertEquals(200L, created.getChannelId());
        assertNull(created.getSrcId());
        assertEquals("标题", created.getTitle());
        assertNotSame(a10.getExt(), created.getExt());
        assertEquals(0L, created.getExt().getViews());
        assertNull(created.getExt().getStaticFile());
        // 已链入翻译组（新组两行：源文 + 草稿）
        ArgumentCaptor<ArticleI18n> captor = ArgumentCaptor.forClass(ArticleI18n.class);
        verify(mapper, times(2)).insert(captor.capture());
        assertEquals(10L, captor.getAllValues().get(0).getArticleId());
        assertEquals(77L, captor.getAllValues().get(1).getArticleId());
    }

    @Test
    void createTranslationRejectsOccupiedTargetSite() {
        when(mapper.findMemberArticleByGroupAndSiteId(10L, 2L)).thenReturn(article(21L, variant));
        assertThrows(Http400Exception.class,
                () -> service.createTranslation(a10, variant, channel(200L, 2L), user(100L, 1L)));
        verify(articleService, never()).insert(any(Article.class), anyLong(), anyList());
    }

    @Test
    void createTranslationRejectsChannelSiteMismatch() {
        assertThrows(Http400Exception.class,
                () -> service.createTranslation(a10, variant, channel(200L, 9L), user(100L, 1L)));
    }

    // endregion

    // region 未入组匹配（批量翻译“先关联已有译文、后新建”）

    private Article hit(long id, @Nullable String slug, String title) {
        Article article = article(id, variant);
        article.setSlug(slug == null ? "" : slug);
        article.setTitle(title);
        return article;
    }

    @Test
    void findUnlinkedMatchPrefersSlugOverTitle() {
        when(mapper.listUnlinkedMatchArticles(200L, "a-1", "标题"))
                .thenReturn(List.of(hit(21L, "other", "标题"), hit(22L, "a-1", "别的")));
        ArticleI18nService.Match match = service.findUnlinkedMatch(200L, "a-1", "标题");
        assertEquals(ArticleI18nService.Match.Basis.SLUG, match.basis());
        Article matched = match.article();
        assertNotNull(matched);
        assertEquals(22L, matched.getId());
    }

    @Test
    void findUnlinkedMatchFallsBackToTitleWhenSlugBlank() {
        when(mapper.listUnlinkedMatchArticles(200L, null, "标题")).thenReturn(List.of(hit(21L, null, "标题")));
        ArticleI18nService.Match match = service.findUnlinkedMatch(200L, " ", "标题");
        assertEquals(ArticleI18nService.Match.Basis.TITLE, match.basis());
        Article matched = match.article();
        assertNotNull(matched);
        assertEquals(21L, matched.getId());
    }

    @Test
    void findUnlinkedMatchAmbiguousWhenSameBasisHitsTwice() {
        when(mapper.listUnlinkedMatchArticles(200L, "a-1", "标题"))
                .thenReturn(List.of(hit(21L, "a-1", "x"), hit(22L, "a-1", "y")));
        assertSame(ArticleI18nService.Match.AMBIGUOUS, service.findUnlinkedMatch(200L, "a-1", "标题"));
        when(mapper.listUnlinkedMatchArticles(200L, null, "标题"))
                .thenReturn(List.of(hit(23L, null, "标题"), hit(24L, null, "标题")));
        assertSame(ArticleI18nService.Match.AMBIGUOUS, service.findUnlinkedMatch(200L, null, "标题"));
    }

    @Test
    void findUnlinkedMatchNoneWithoutHitsAndSkipsQueryWithoutKeys() {
        when(mapper.listUnlinkedMatchArticles(200L, "a-1", "标题")).thenReturn(List.of());
        assertSame(ArticleI18nService.Match.NONE, service.findUnlinkedMatch(200L, "a-1", "标题"));
        assertSame(ArticleI18nService.Match.NONE, service.findUnlinkedMatch(200L, null, ""));
        verify(mapper, times(1)).listUnlinkedMatchArticles(any(), any(), any());
    }

    // endregion

    // region 种子来源键配对建链（install-flow D50）

    /**
     * 瘦身种子行：mapper 只回 id/siteId/originId，不带 site 关联（由 Service 挂上）
     */
    private static Article seed(long id, long siteId, String originId) {
        Article article = new Article();
        article.setId(id);
        article.setSiteId(siteId);
        article.setOriginId(originId);
        return article;
    }

    @Test
    void linkSeedPeersLinksEqualOriginIdsOnly() {
        when(mapper.listSeedArticles(1L)).thenReturn(List.of(seed(10L, 1L, "a-x"), seed(11L, 1L, "a-zh-only")));
        when(mapper.listSeedArticles(2L)).thenReturn(List.of(seed(20L, 2L, "a-x"), seed(21L, 2L, "a-en-only")));

        ArticleI18nService.SeedLinkResult result = service.linkSeedPeers(variant, master);

        assertEquals(new ArticleI18nService.SeedLinkResult(1, 0, 0, 0), result);
        ArgumentCaptor<ArticleI18n> captor = ArgumentCaptor.forClass(ArticleI18n.class);
        verify(mapper, times(2)).insert(captor.capture());
        assertEquals(List.of(20L, 10L), captor.getAllValues().stream().map(ArticleI18n::getArticleId).toList());
        assertEquals(999L, captor.getAllValues().get(0).getGroupId());
        assertEquals(999L, captor.getAllValues().get(1).getGroupId());
    }

    @Test
    void linkSeedPeersCountsExistingPairsWithoutWriting() {
        when(mapper.listSeedArticles(1L)).thenReturn(List.of(seed(10L, 1L, "a-x")));
        when(mapper.listSeedArticles(2L)).thenReturn(List.of(seed(20L, 2L, "a-x")));
        OffsetDateTime now = OffsetDateTime.now();
        when(mapper.listByArticleIds(anyList())).thenReturn(List.of(
                new ArticleI18n(10L, 5L, now, now), new ArticleI18n(20L, 5L, now, now)));

        assertEquals(new ArticleI18nService.SeedLinkResult(0, 1, 0, 0), service.linkSeedPeers(variant, master));
        verify(mapper, never()).insert(any());
    }

    /**
     * 站点内同一来源键出现两篇（先后播种过两个主题）：无从判断哪篇是对方的译文，不建链
     */
    @Test
    void linkSeedPeersSkipsAmbiguousOriginIds() {
        when(mapper.listSeedArticles(1L)).thenReturn(List.of(seed(10L, 1L, "a-x"), seed(12L, 1L, "a-x")));
        when(mapper.listSeedArticles(2L)).thenReturn(List.of(seed(20L, 2L, "a-x")));

        assertEquals(new ArticleI18nService.SeedLinkResult(0, 0, 0, 1), service.linkSeedPeers(variant, master));
        verify(mapper, never()).insert(any());
    }

    /**
     * 不变量拒绝的配对计为冲突并跳过，其余配对照常建链
     */
    @Test
    void linkSeedPeersCountsRejectedPairsAsConflicts() {
        when(mapper.listSeedArticles(1L)).thenReturn(List.of(seed(10L, 1L, "a-x"), seed(11L, 1L, "a-y")));
        when(mapper.listSeedArticles(2L)).thenReturn(List.of(seed(20L, 2L, "a-x"), seed(21L, 2L, "a-y")));
        OffsetDateTime now = OffsetDateTime.now();
        ArticleI18n m10 = new ArticleI18n(10L, 5L, now, now);
        ArticleI18n m20 = new ArticleI18n(20L, 6L, now, now);
        when(mapper.listByArticleIds(anyList())).thenReturn(List.of(m10, m20));
        when(mapper.select(10L)).thenReturn(m10);
        when(mapper.select(20L)).thenReturn(m20);

        assertEquals(new ArticleI18nService.SeedLinkResult(1, 0, 1, 0), service.linkSeedPeers(variant, master));
        verify(mapper, times(2)).insert(any());
    }

    // endregion
}
