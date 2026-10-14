package com.ujcms.cms.core.service;

import com.ujcms.cms.core.component.ContentStatEvictor;
import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleStatusCount;
import com.ujcms.cms.core.domain.ArticleTagName;
import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ujcms.cms.core.domain.Article.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章列表的三样后端能力（docs/spec/article-management/plan.md Phase 3）：从回收站恢复（D2）、批量移动栏目、
 * 各状态的文章数。
 * <p>
 * 移动栏目只写栏目一列，锁序与其它落位写入一致（站点行 → 源文章行，升序）；不合条件的文章略过并计数，
 * 不让整批失败。
 */
@SuppressWarnings("unchecked")
class ArticleServiceMoveTest {
    private static final long SITE = 1L;
    private static final long FROM = 5L;
    private static final long OTHER = 6L;
    private static final long TARGET = 9L;
    private static final long MODEL = 30L;

    private ArticleMapper mapper;
    private SiteMapper siteMapper;
    private ArticleLucene articleLucene;
    private BlockItemService blockItemService;
    private ArticleService service;

    private final Site site = new Site();
    private final User editor = mock(User.class);

    @BeforeEach
    void setUp() {
        mapper = mock(ArticleMapper.class);
        siteMapper = mock(SiteMapper.class);
        articleLucene = mock(ArticleLucene.class);
        blockItemService = mock(BlockItemService.class);
        SnowflakeSequence sequence = mock(SnowflakeSequence.class);
        service = new ArticleService(mock(HtmlService.class), mock(HtmlSanitizeService.class),
                mock(ObjectProvider.class), mock(ChannelService.class), mock(TagService.class),
                blockItemService, articleLucene, mapper, mock(ArticleExtMapper.class),
                mock(ArticleTagMapper.class), mock(ArticleTopicMapper.class), mock(ArticleRelatedMapper.class),
                mock(ArticleI18nMapper.class), mock(ArticleI18nAnchorService.class), mock(TopicService.class),
                mock(AttachmentService.class), sequence,
                new SlugGenerator(mock(SiteMapper.class)),
                mock(ContentStatEvictor.class), siteMapper);
        site.setId(SITE);
        site.setProtocol("https");
        site.setDomain("example.com");
        when(editor.hasAllStatusPermission()).thenReturn(false);
        // 与数据库一致：改了栏目之后再读，读到的是新栏目
        doAnswer(invocation -> {
            Article stored = mapper.select(invocation.<Long>getArgument(0));
            stored.setChannelId(invocation.getArgument(1));
            return 1;
        }).when(mapper).updateChannel(anyLong(), anyLong());
    }

    private Channel channel(long id, String processKey, long articleModelId) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setSiteId(SITE);
        channel.setSite(site);
        channel.setProcessKey(processKey);
        channel.setArticleModelId(articleModelId);
        return channel;
    }

    private Article article(long id, Channel channel, short status) {
        Article article = new Article();
        article.setId(id);
        article.setSiteId(SITE);
        article.setSite(site);
        article.setChannelId(channel.getId());
        article.setChannel(channel);
        article.setTitle("title " + id);
        article.setStatus(status);
        when(mapper.select(id)).thenReturn(article);
        return article;
    }

    // region restore

    @Test
    void restoreReturnsADeletedArticleToDraft() {
        Article article = article(100L, channel(FROM, null, MODEL), STATUS_DELETED);

        assertTrue(service.restore(article));

        assertEquals(STATUS_DRAFT, article.getStatus());
        verify(mapper).updateStatus(100L, STATUS_DRAFT);
        // 只写状态一列
        verify(mapper, never()).update(any(Article.class));
    }

    @Test
    void restoreLeavesAnArticleThatIsNotDeletedAlone() {
        Article article = article(100L, channel(FROM, null, MODEL), STATUS_OFFLINE);

        assertFalse(service.restore(article));

        assertEquals(STATUS_OFFLINE, article.getStatus());
        verify(mapper, never()).updateStatus(anyLong(), anyShort());
    }

    // endregion

    // region move

    @Test
    void moveWritesOnlyTheChannelColumnUnderTheSiteThenSourceLocks() {
        Channel from = channel(FROM, null, MODEL);
        Channel other = channel(OTHER, null, MODEL);
        Channel target = channel(TARGET, null, MODEL);
        Article a = article(102L, from, STATUS_PUBLISHED);
        Article b = article(101L, other, STATUS_DRAFT);
        // b 是扩展栏目的子行：锁的是它的源文章
        b.setSrcId(50L);
        article(50L, channel(77L, null, MODEL), STATUS_PUBLISHED);

        ArticleService.MoveResult result = service.move(List.of(a, b), target, editor);

        assertEquals(2, result.moved());
        assertEquals(0, result.skipped());
        assertEquals(Set.of(FROM, OTHER), result.movedByOldChannel().keySet());
        // 返回的文章已在新栏目：调用方据此重新生成新栏目的页面
        assertEquals(TARGET, result.movedByOldChannel().get(FROM).get(0).getChannelId());
        InOrder order = inOrder(siteMapper, mapper);
        order.verify(siteMapper).lockById(SITE);
        ArgumentCaptor<Collection<Long>> locked = ArgumentCaptor.forClass(Collection.class);
        order.verify(mapper).lockByIds(locked.capture());
        assertEquals(List.of(50L, 102L), List.copyOf(locked.getValue()));
        order.verify(mapper).updateChannel(102L, TARGET);
        order.verify(mapper).updateChannel(101L, TARGET);
        verify(mapper, never()).update(any(Article.class));
        verify(mapper, never()).updateStatus(anyLong(), anyShort());
    }

    @Test
    void moveSkipsArticlesAlreadyInTheTargetAndThoseUnderReview() {
        Channel target = channel(TARGET, null, MODEL);
        Channel reviewed = channel(FROM, "article-review", MODEL);
        Article already = article(100L, target, STATUS_DRAFT);
        Article pending = article(101L, reviewed, STATUS_PENDING);
        Article reviewing = article(102L, reviewed, STATUS_REVIEWING);
        when(editor.hasAllStatusPermission()).thenReturn(true);

        ArticleService.MoveResult result = service.move(List.of(already, pending, reviewing), target, editor);

        assertEquals(0, result.moved());
        assertEquals(3, result.skipped());
        verify(mapper, never()).updateChannel(anyLong(), anyLong());
    }

    @Test
    void moveSkipsAPublishedArticleOfAReviewedChannelWithoutAllStatusPermission() {
        Channel reviewed = channel(FROM, "article-review", MODEL);
        Channel target = channel(TARGET, null, MODEL);
        Article published = article(100L, reviewed, STATUS_PUBLISHED);
        Article draft = article(101L, reviewed, STATUS_DRAFT);

        ArticleService.MoveResult result = service.move(List.of(published, draft), target, editor);

        assertEquals(1, result.moved());
        assertEquals(1, result.skipped());
        verify(mapper).updateChannel(101L, TARGET);
        verify(mapper, never()).updateChannel(100L, TARGET);
    }

    @Test
    void moveIntoAReviewedChannelDoesNotCarryLiveArticlesPastItsReview() {
        Channel free = channel(FROM, null, MODEL);
        Channel reviewedTarget = channel(TARGET, "article-review", MODEL);
        Article published = article(100L, free, STATUS_PUBLISHED);
        Article ready = article(101L, free, STATUS_READY);
        Article draft = article(102L, free, STATUS_DRAFT);

        ArticleService.MoveResult result = service.move(List.of(published, ready, draft), reviewedTarget, editor);

        // 已发布、待发布的文章移进要审核的栏目，等于绕过那个栏目的审核：略过。草稿照常移动，提交时再进审批
        assertEquals(1, result.moved());
        assertEquals(2, result.skipped());
        verify(mapper).updateChannel(102L, TARGET);
    }

    @Test
    void allStatusPermissionMovesLiveArticlesOfReviewedChannels() {
        Channel reviewed = channel(FROM, "article-review", MODEL);
        Channel target = channel(TARGET, "article-review", MODEL);
        Article published = article(100L, reviewed, STATUS_PUBLISHED);
        when(editor.hasAllStatusPermission()).thenReturn(true);

        ArticleService.MoveResult result = service.move(List.of(published), target, editor);

        assertEquals(1, result.moved());
        verify(mapper).updateChannel(100L, TARGET);
    }

    @Test
    void moveSkipsArticlesOfAnotherArticleModel() {
        Channel from = channel(FROM, null, 31L);
        Channel target = channel(TARGET, null, MODEL);
        Article article = article(100L, from, STATUS_DRAFT);

        ArticleService.MoveResult result = service.move(List.of(article), target, editor);

        assertEquals(0, result.moved());
        assertEquals(1, result.skipped());
    }

    @Test
    void moveSkipsAnArticleWhoseFamilyAlreadySitsInTheTarget() {
        Channel from = channel(FROM, null, MODEL);
        Channel target = channel(TARGET, null, MODEL);
        Article source = article(100L, from, STATUS_PUBLISHED);
        // 源文章的引用已经在目标栏目
        Article refer = article(200L, target, STATUS_PUBLISHED);
        refer.setSrcId(100L);
        when(mapper.listBySrcId(100L)).thenReturn(List.of(refer));

        ArticleService.MoveResult result = service.move(List.of(source), target, editor);

        assertEquals(0, result.moved());
        assertEquals(1, result.skipped());
        verify(mapper, never()).updateChannel(anyLong(), anyLong());
    }

    @Test
    void movingTwoMembersOfOneFamilyKeepsOnlyTheFirst() {
        Channel from = channel(FROM, null, MODEL);
        Channel other = channel(OTHER, null, MODEL);
        Channel target = channel(TARGET, null, MODEL);
        Article source = article(100L, from, STATUS_PUBLISHED);
        Article refer = article(200L, other, STATUS_PUBLISHED);
        refer.setSrcId(100L);
        when(mapper.listBySrcId(100L)).thenReturn(List.of(refer));

        ArticleService.MoveResult result = service.move(List.of(source, refer), target, editor);

        assertEquals(1, result.moved());
        assertEquals(1, result.skipped());
        verify(mapper).updateChannel(100L, TARGET);
        verify(mapper, never()).updateChannel(200L, TARGET);
    }

    @Test
    void moveCountsAnArticleDeletedMeanwhileAsSkipped() {
        Channel from = channel(FROM, null, MODEL);
        Channel target = channel(TARGET, null, MODEL);
        Article gone = article(100L, from, STATUS_DRAFT);
        when(mapper.select(100L)).thenReturn(null);

        ArticleService.MoveResult result = service.move(List.of(gone), target, editor);

        assertEquals(0, result.moved());
        assertEquals(1, result.skipped());
    }

    @Test
    void moveOfNothingTakesNoLock() {
        ArticleService.MoveResult result = service.move(List.of(), channel(TARGET, null, MODEL), editor);

        assertEquals(0, result.moved());
        verify(siteMapper, never()).lockById(anyLong());
    }

    // endregion

    // region countByStatus

    @Test
    void countByStatusDropsTheSortAndReturnsOnlyStatusesThatHaveArticles() {
        ArticleStatusCount draft = new ArticleStatusCount();
        draft.setStatus(STATUS_DRAFT);
        draft.setTotal(3);
        ArticleStatusCount published = new ArticleStatusCount();
        published.setStatus(STATUS_PUBLISHED);
        published.setTotal(31);
        ArgumentCaptor<QueryInfo> queryInfo = ArgumentCaptor.forClass(QueryInfo.class);
        when(mapper.countByStatus(queryInfo.capture(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(draft, published));
        ArticleArgs args = ArticleArgs.of(new java.util.HashMap<>(Map.of(
                "Contains_title", "news", "OrderBy", "channel-name_desc")));
        args.siteId(SITE);

        Map<Short, Integer> counts = service.countByStatus(args);

        assertEquals(Map.of(STATUS_PUBLISHED, 31, STATUS_DRAFT, 3), counts);
        assertNull(counts.get(STATUS_DELETED));
        // 排序去掉了：不为它关联栏目表
        assertNotNull(queryInfo.getValue());
        assertTrue(queryInfo.getValue().getJoinTables().isEmpty());
        // 调用方的查询条件不受影响
        assertEquals("channel-name_desc", args.getQueryMap().get("OrderBy"));
    }

    // endregion

    // region attachListRelations（Phase 6）

    @Test
    void aPageGetsItsTagsAndBlockItemsInTwoQueries() {
        Channel channel = channel(FROM, null, MODEL);
        Article first = article(1L, channel, STATUS_PUBLISHED);
        Article second = article(2L, channel, STATUS_PUBLISHED);
        ArticleTagName alpha = new ArticleTagName();
        alpha.setArticleId(1L);
        alpha.setTagId(11L);
        alpha.setName("alpha");
        ArticleTagName beta = new ArticleTagName();
        beta.setArticleId(1L);
        beta.setTagId(12L);
        beta.setName("beta");
        when(mapper.listTagNames(List.of(1L, 2L))).thenReturn(List.of(alpha, beta));
        BlockItem item = new BlockItem();
        item.setId(21L);
        item.setArticleId(2L);
        when(blockItemService.listByArticleIds(List.of(1L, 2L))).thenReturn(List.of(item));

        service.attachListRelations(List.of(first, second));

        assertEquals(List.of("alpha", "beta"), first.getTags().stream().map(Tag::getName).toList());
        assertEquals(List.of(11L, 12L), first.getTags().stream().map(Tag::getId).toList());
        assertTrue(first.getBlockItemList().isEmpty());
        // 没有 TAG 的文章也赋上空列表：赋过值，就不再逐篇懒加载
        assertTrue(second.getTags().isEmpty());
        assertEquals(List.of(item), second.getBlockItemList());
    }

    @Test
    void anEmptyPageAsksNothing() {
        service.attachListRelations(List.of());

        verify(mapper, never()).listTagNames(any());
        verify(blockItemService, never()).listByArticleIds(any());
    }

    // endregion
    // region 提交之后的全文索引（Phase 1.7）

    /**
     * 模拟一次事务提交：依次执行登记的提交后动作，像 Spring 一样——某个动作抛出异常，后面的就不再执行
     */
    private static void commit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        try {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        } finally {
            synchronizations.forEach(it -> it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        }
    }

    @Test
    void anIndexFailureAfterCommitNeitherEscapesNorStopsWhatFollows() {
        Channel channel = channel(FROM, null, MODEL);
        Article first = article(100L, channel, STATUS_DELETED);
        Article second = article(101L, channel, STATUS_DELETED);
        doThrow(new IllegalStateException("index is down")).when(articleLucene).update(any());
        Runnable later = mock(Runnable.class);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.restore(first);
            service.restore(second);
            // 排在索引之后的提交后动作（搜索引擎推送、发布通知就是这样登记的）
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    later.run();
                }
            });
            // 提交之前不写索引
            verify(articleLucene, never()).update(any());

            assertDoesNotThrow(ArticleServiceMoveTest::commit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        // 第一篇失败了，第二篇照样尝试；后面的动作照常执行
        verify(articleLucene, org.mockito.Mockito.times(2)).update(any());
        verify(later).run();
    }

    @Test
    void aSiteDeleteClearsTheIndexOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.preSiteDelete(SITE);
            // 事务还可能回滚：站点还在，索引就得还在
            verify(articleLucene, never()).deleteBySiteId(anyLong());

            commit();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(articleLucene).deleteBySiteId(SITE);
    }

    @Test
    void aSiteDeleteOutsideATransactionClearsTheIndexAtOnce() {
        service.preSiteDelete(SITE);

        verify(articleLucene).deleteBySiteId(SITE);
    }

    // endregion

    // region 排序

    @Test
    void anOrderByOfOnlyCommasIsNoSortInsteadOfAnError() {
        ArticleArgs args = ArticleArgs.of(new java.util.HashMap<>(Map.of("OrderBy", ",")));

        assertDoesNotThrow(() -> service.selectList(args));
    }

    @Test
    void idListsAreBoundAsNumbers() {
        // 不带类型的 In_id 按字符串绑定：MySQL 会转换，PostgreSQL 报“bigint = character varying”
        ArticleArgs args = ArticleArgs.of().inIds(List.of(7L)).excludeIds(List.of(8L));

        assertEquals(List.of(7L), List.copyOf((Collection<Long>) args.getQueryMap().get("In_id_Long")));
        assertEquals(List.of(8L), List.copyOf((Collection<Long>) args.getQueryMap().get("NotIn_id_Long")));
    }

    // endregion
}
