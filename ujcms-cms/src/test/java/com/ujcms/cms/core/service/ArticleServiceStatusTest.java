package com.ujcms.cms.core.service;

import com.ujcms.cms.core.component.ContentStatEvictor;
import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleExt;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.web.exception.LogicException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.ujcms.cms.core.domain.Article.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章状态机（docs/spec/article-management/plan.md Phase 1）。
 * <p>
 * 状态、置顶、流程字段各自定向写入，不再整行回写（1.1）——整行回写曾让定时任务把到期的置顶又写回去（H1），
 * 而轻量 update 不重建索引，审核通过的文章搜不到（H2）。上下线时间在每一条发布路径上都生效（D4）：
 * 上线时间在将来的文章照常进入审批（H3），没有流程引擎的社区版也不提前上线；下线时间已过的不能提交。
 * 提交时撤销文章名下过时的流程实例（1.5）。
 */
@SuppressWarnings("unchecked")
class ArticleServiceStatusTest {
    private static final long ID = 100L;
    private static final long SITE = 1L;
    private static final long CHANNEL = 5L;
    private static final long USER = 7L;

    private ArticleMapper mapper;
    private ArticleExtMapper extMapper;
    private ArticleLucene articleLucene;
    private ChannelService channelService;
    private ObjectProvider<ArticleProcessPort> processPort;
    private ArticleService service;

    private final Site site = new Site();

    @BeforeEach
    void setUp() {
        mapper = mock(ArticleMapper.class);
        extMapper = mock(ArticleExtMapper.class);
        articleLucene = mock(ArticleLucene.class);
        channelService = mock(ChannelService.class);
        processPort = mock(ObjectProvider.class);
        SnowflakeSequence sequence = mock(SnowflakeSequence.class);
        when(sequence.nextId()).thenReturn(ID);
        service = new ArticleService(mock(HtmlService.class), mock(HtmlSanitizeService.class),
                processPort, channelService, mock(TagService.class),
                mock(BlockItemService.class), articleLucene, mapper, extMapper,
                mock(ArticleTagMapper.class), mock(ArticleTopicMapper.class), mock(ArticleRelatedMapper.class),
                mock(ArticleI18nMapper.class), mock(ArticleI18nAnchorService.class), mock(TopicService.class),
                mock(AttachmentService.class), sequence,
                new SlugGenerator(mock(SiteMapper.class)),
                mock(ContentStatEvictor.class), mock(SiteMapper.class));
        site.setId(SITE);
        site.setProtocol("https");
        site.setDomain("example.com");
    }

    private Article article(short status, String processKey) {
        Channel channel = new Channel();
        channel.setId(CHANNEL);
        channel.setSiteId(SITE);
        channel.setSite(site);
        channel.setProcessKey(processKey);
        channel.setArticleModel(new Model());
        when(channelService.select(CHANNEL)).thenReturn(channel);
        Article article = new Article();
        article.setId(ID);
        article.setSiteId(SITE);
        article.setSite(site);
        article.setChannelId(CHANNEL);
        article.setChannel(channel);
        article.setTitle("title");
        article.setStatus(status);
        when(mapper.select(ID)).thenReturn(article);
        return article;
    }

    private ArticleProcessPort port() {
        ArticleProcessPort port = mock(ArticleProcessPort.class);
        when(processPort.getIfAvailable()).thenReturn(port);
        // ObjectProvider#ifAvailable 是默认方法，mock 之后不会执行：照它的语义转给端口
        doAnswer(invocation -> {
            invocation.<Consumer<ArticleProcessPort>>getArgument(0).accept(port);
            return null;
        }).when(processPort).ifAvailable(any());
        // 与 T1 实现一致：进入流程即待审核，并记下流程实例ID
        doAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            article.setStatus(STATUS_PENDING);
            article.setProcessInstanceId("p-new");
            return null;
        }).when(port).start(any(), any(), anyLong());
        return port;
    }

    // region submit

    @Test
    void submitWithoutWorkflowPublishesThroughTargetedWritesAndReindexes() {
        Article article = article(STATUS_DRAFT, null);

        service.submit(article, USER);

        assertEquals(STATUS_PUBLISHED, article.getStatus());
        verify(mapper).updateStatus(ID, STATUS_PUBLISHED);
        verify(extMapper).updateProcess(ID, null, null);
        // 不回写整行
        verify(mapper, never()).update(any(Article.class));
        verify(extMapper, never()).update(any(ArticleExt.class));
        // 状态变了，索引随之重建（H2）
        verify(articleLucene).update(any(EsArticle.class));
    }

    @Test
    void submitHonoursAFutureOnlineDateEvenWithAProcessKeyAndNoEngine() {
        // 社区版：栏目带着 processKey，却没有流程引擎——视同无流程，但上线时间仍然作数
        Article article = article(STATUS_DRAFT, "k");
        article.setOnlineDate(OffsetDateTime.now().plusDays(1));
        when(processPort.getIfAvailable()).thenReturn(null);

        service.submit(article, USER);

        assertEquals(STATUS_READY, article.getStatus());
        verify(mapper).updateStatus(ID, STATUS_READY);
    }

    // ----- 提交审核、绝不直接发布（docs/spec/member-contribute/plan.md D6） -----

    @Test
    void submitForReviewStaysPendingWithoutAProcess() {
        Article article = article(STATUS_REJECTED, null);
        article.getExt().setRejectReason("fix");

        service.submitForReview(article, USER);

        assertEquals(STATUS_PENDING, article.getStatus());
        assertNull(article.getProcessInstanceId());
        verify(mapper).updateStatus(ID, STATUS_PENDING);
        verify(extMapper).updateProcess(ID, null, null);
        assertNull(article.getExt().getRejectReason());
    }

    @Test
    void submitForReviewStartsTheProcessWhenTheChannelHasOneAndThePortIsPresent() {
        Article article = article(STATUS_REJECTED, "k");
        ArticleProcessPort port = port();

        service.submitForReview(article, USER);

        verify(port).cancelAll(ID, SITE, "resubmit");
        verify(port).start(article, "k", USER);
        verify(mapper).updateStatus(ID, STATUS_PENDING);
        verify(extMapper).updateProcess(ID, "p-new", null);
    }

    @Test
    void submitForReviewNeverPublishesEvenWhenThePortIsAbsent() {
        // 栏目有流程键而社区版无端口：submit 会直接发布，submitForReview 停在待审核
        Article article = article(STATUS_PENDING, "k");

        service.submitForReview(article, USER);

        assertEquals(STATUS_PENDING, article.getStatus());
        verify(mapper, never()).updateStatus(ID, STATUS_PUBLISHED);
    }

    @Test
    void rejectFiresTheRejectListenersAfterWritingTheReason() {
        Article article = article(STATUS_PENDING, null);
        com.ujcms.cms.core.listener.ArticleRejectListener listener =
                mock(com.ujcms.cms.core.listener.ArticleRejectListener.class);
        service.setRejectListeners(List.of(listener));

        service.reject(article, "reason");

        verify(mapper).updateStatus(ID, STATUS_REJECTED);
        verify(extMapper).updateProcess(ID, null, "reason");
        verify(listener).afterArticleReject(article);
        assertEquals("reason", article.getExt().getRejectReason());
    }

    @Test
    void submitRefusesAnArticleWhoseOfflineDateHasPassed() {
        Article article = article(STATUS_OFFLINE, null);
        article.setOfflineDate(OffsetDateTime.now().minusMinutes(1));

        assertThrows(LogicException.class, () -> service.submit(article, USER));

        verify(mapper, never()).updateStatus(anyLong(), anyShort());
        assertEquals(STATUS_OFFLINE, article.getStatus());
    }

    @Test
    void submitStartsTheProcessAfterCancellingLeftoverInstances() {
        Article article = article(STATUS_REJECTED, "k");
        article.getExt().setRejectReason("fix the title");
        ArticleProcessPort port = port();

        service.submit(article, USER);

        verify(port).cancelAll(ID, SITE, "resubmit");
        verify(port).start(article, "k", USER);
        verify(mapper).updateStatus(ID, STATUS_PENDING);
        // 退回原因随重新提交清除
        verify(extMapper).updateProcess(ID, "p-new", null);
        assertNull(article.getExt().getRejectReason());
    }

    @Test
    void submitLeavesAPendingArticleAloneWhileItsInstanceIsRunning() {
        Article article = article(STATUS_PENDING, "k");
        article.setProcessInstanceId("p1");
        ArticleProcessPort port = port();
        when(port.isRunning(article, "k")).thenReturn(true);

        service.submit(article, USER);

        verify(port, never()).start(any(), any(), anyLong());
        verify(port, never()).cancelAll(anyLong(), anyLong(), any());
        verify(mapper, never()).updateStatus(anyLong(), anyShort());
    }

    @Test
    void submitRestartsAPendingArticleWhoseInstanceIsGone() {
        // 流程实例被删除，或属于栏目以前的流程：重新进入流程，而不是当作“已在审核”原地不动
        Article article = article(STATUS_PENDING, "k");
        article.setProcessInstanceId("p-old");
        ArticleProcessPort port = port();
        when(port.isRunning(article, "k")).thenReturn(false);

        service.submit(article, USER);

        verify(port).cancelAll(ID, SITE, "resubmit");
        verify(port).start(article, "k", USER);
        verify(extMapper).updateProcess(ID, "p-new", null);
    }

    @Test
    void submitOnAChannelWithoutWorkflowCancelsTheLingeringInstanceBeforePublishing() {
        // 文章改换到了没有流程的栏目（或栏目的流程被清空），原来的实例还在跑
        Article article = article(STATUS_PENDING, null);
        article.setProcessInstanceId("p-old");
        ArticleProcessPort port = port();

        service.submit(article, USER);

        verify(port).cancelAll(ID, SITE, "resubmit");
        verify(port, never()).start(any(), any(), anyLong());
        verify(mapper).updateStatus(ID, STATUS_PUBLISHED);
        verify(extMapper).updateProcess(ID, null, null);
    }

    // endregion

    // region insert

    @Test
    void insertSendsAFutureDatedArticleThroughTheProcess() {
        // 上线时间在将来 → 控制器按上下线时间归位为“待发布”。以前待发布不进流程，到点自动上线，审批就这样被绕开（H3）
        Article article = article(STATUS_READY, "k");
        article.setOnlineDate(OffsetDateTime.now().plusDays(1));
        ArticleProcessPort port = port();

        service.insert(article, USER, List.of());

        verify(port).start(article, "k", USER);
        assertEquals(STATUS_PENDING, article.getStatus());
    }

    @Test
    void insertWithoutAnEngineKeepsAFutureDatedArticleReady() {
        Article article = article(STATUS_READY, "k");
        article.setOnlineDate(OffsetDateTime.now().plusDays(1));
        when(processPort.getIfAvailable()).thenReturn(null);

        service.insert(article, USER, List.of());

        assertEquals(STATUS_READY, article.getStatus());
    }

    @Test
    void aMigratedArticleKeepsItsOwnDatesWhileANewOneGetsNow() {
        // 数据迁移：源系统里的创建、修改时间要留下来（Phase 5b）。普通的新增一律写成“现在”
        OffsetDateTime created = OffsetDateTime.parse("2019-03-04T05:06:07+08:00");
        OffsetDateTime modified = OffsetDateTime.parse("2020-01-02T03:04:05+08:00");
        Article migrated = article(STATUS_DRAFT, null);
        migrated.setCreated(created);
        migrated.setModified(modified);

        service.insertMigrated(migrated, USER, List.of());

        assertEquals(created, migrated.getCreated());
        assertEquals(modified, migrated.getModified());

        Article fresh = article(STATUS_DRAFT, null);
        fresh.setCreated(created);
        fresh.setModified(modified);

        service.insert(fresh, USER, List.of());

        assertEquals(OffsetDateTime.now().getYear(), fresh.getCreated().getYear());
        assertEquals(OffsetDateTime.now().getYear(), fresh.getModified().getYear());
    }

    @Test
    void aMigratedPublishedArticleNeverStartsAProcess() {
        // 数据迁移：迁入的是历史内容，栏目有审批流程也不进待审核（docs/spec/data-migration/plan.md D6）
        Article article = article(STATUS_PUBLISHED, "k");
        ArticleProcessPort port = port();

        service.insertMigrated(article, USER, List.of());

        verify(port, never()).start(any(), any(), anyLong());
        assertEquals(STATUS_PUBLISHED, article.getStatus());
    }

    @Test
    void insertOfADraftNeverStartsAProcess() {
        Article article = article(STATUS_DRAFT, "k");
        ArticleProcessPort port = port();

        service.insert(article, USER, List.of());

        verify(port, never()).start(any(), any(), anyLong());
        assertEquals(STATUS_DRAFT, article.getStatus());
    }

    // endregion

    // region archive / offline / delete / reject / sticky

    @Test
    void archiveWritesOnlyTheStatus() {
        Article article = article(STATUS_PUBLISHED, null);

        service.archive(article);

        verify(mapper).updateStatus(ID, STATUS_ARCHIVED);
        verify(mapper, never()).update(any(Article.class));
        verify(articleLucene).update(any(EsArticle.class));
    }

    @Test
    void offlineLeavesTheProcessAndKeepsTheRejectReason() {
        Article article = article(STATUS_REVIEWING, "k");
        article.setProcessInstanceId("p1");
        article.getExt().setRejectReason("an earlier remark");
        ArticleProcessPort port = port();
        User user = new User();
        user.setUsername("editor");

        service.offline(article, user);

        verify(port).cancelAll(eq(ID), eq(SITE), any());
        // 流程实例ID清掉，退回原因保留
        verify(extMapper).updateProcess(eq(ID), isNull(), eq("an earlier remark"));
        verify(mapper).updateStatus(ID, STATUS_OFFLINE);
        assertNull(article.getProcessInstanceId());
    }

    @Test
    void deleteWritesOnlyTheStatus() {
        Article article = article(STATUS_PUBLISHED, null);

        service.delete(article, new User());

        verify(mapper).updateStatus(ID, STATUS_DELETED);
        // 没有流程实例ID可清，流程字段不动
        verify(extMapper, never()).updateProcess(anyLong(), any(), any());
        verify(mapper, never()).update(any(Article.class));
    }

    @Test
    void rejectStoresTheReasonAndKeepsTheInstanceId() {
        Article article = article(STATUS_REVIEWING, "k");
        article.setProcessInstanceId("p1");

        service.reject(article, "fix the title");

        verify(mapper).updateStatus(ID, STATUS_REJECTED);
        // 流程实例ID保留：作者要从“流程任务”里看审核意见
        verify(extMapper).updateProcess(ID, "p1", "fix the title");
        assertEquals(STATUS_REJECTED, article.getStatus());
    }

    @Test
    void updateStickyWritesOnlyTheStickyColumns() {
        Article article = article(STATUS_PUBLISHED, null);
        OffsetDateTime until = OffsetDateTime.now().plusDays(3);

        service.updateSticky(article, (short) 5, until);

        verify(mapper).updateSticky(ID, (short) 5, until);
        verify(mapper, never()).update(any(Article.class));
    }

    // endregion

    // region schedule

    @Test
    void expiredStickyIsClearedOnTheReturnedBeansAndNeverWrittenBack() {
        Article article = article(STATUS_PUBLISHED, null);
        article.setSticky((short) 3);
        article.setStickyDate(OffsetDateTime.now().minusMinutes(5));
        when(mapper.selectAll(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(article));

        List<Article> changed = service.applySchedule();

        // 三步（置顶到期、上线、下线）各返回一次同一篇文章；收尾只做一次
        assertEquals(3, changed.size());
        assertEquals((short) 0, changed.get(0).getSticky());
        assertNull(changed.get(0).getStickyDate());
        verify(mapper).updateStickyDate(any(OffsetDateTime.class));
        // H1：以前任务把读出的整行再写回去，到期的置顶又回来了
        verify(mapper, never()).update(any(Article.class));
        verify(mapper, never()).updateSticky(anyLong(), anyShort(), any());
        verify(articleLucene, times(1)).update(any(EsArticle.class));
    }

    // endregion

    // region queries

    @Test
    void aRequestSortGetsAnIdTiebreak() {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("OrderBy", "publishDate_desc");

        service.selectList(ArticleArgs.of(queryMap));

        ArgumentCaptor<QueryInfo> captor = ArgumentCaptor.forClass(QueryInfo.class);
        verify(mapper).selectAll(captor.capture(), any(), any(), any(), any(), any(), any());
        List<QueryInfo.OrderByCondition> orderBy = captor.getValue().getOrderByConditions();
        assertEquals(2, orderBy.size());
        assertEquals("t.id_", orderBy.get(1).getColumn());
        assertEquals("desc", orderBy.get(1).getDirection());
        // 调用方的查询条件不被改动
        assertEquals("publishDate_desc", queryMap.get("OrderBy"));
    }

    @Test
    void theDefaultSortAndASortEndingInIdAreLeftAlone() {
        service.selectList(ArticleArgs.of());
        Map<String, Object> byId = new HashMap<>();
        byId.put("OrderBy", "id");
        service.selectList(ArticleArgs.of(byId));

        ArgumentCaptor<QueryInfo> captor = ArgumentCaptor.forClass(QueryInfo.class);
        verify(mapper, times(2)).selectAll(captor.capture(), any(), any(), any(), any(), any(), any());
        // 默认：order_ desc, id_ desc
        assertEquals(2, captor.getAllValues().get(0).getOrderByConditions().size());
        assertEquals(1, captor.getAllValues().get(1).getOrderByConditions().size());
    }

    @Test
    void titleSimilarityPutsTheMostSimilarFirst() {
        EsArticle far = new EsArticle();
        far.setTitle("Trade talks go hybrid today");
        EsArticle near = new EsArticle();
        near.setTitle("Trade talks go hybrid");
        when(articleLucene.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of(far, near)));

        List<EsArticle> result = service.titleSimilarity(0.6, "Trade talks go hybrid", SITE, null);

        assertEquals(2, result.size());
        assertEquals("Trade talks go hybrid", result.get(0).getTitle());
    }

    // endregion
}
