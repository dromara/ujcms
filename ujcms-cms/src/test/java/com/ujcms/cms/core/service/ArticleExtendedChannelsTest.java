package com.ujcms.cms.core.service;

import com.ujcms.cms.core.component.ContentStatEvictor;
import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.LogicException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据权限只校验“新加的”扩展栏目（docs/spec/article-data-permission/plan.md D3）：
 * {@link ArticleService#listAddedExtendedChannelIds} 与 {@link ArticleService#assignExtendedChannels} 读同一份现有子行，
 * 原样提交回来的扩展栏目不算新加；REFER 与 COPY 互换要重建子行，算新加。审核人判定在没有流程端口时恒为否。
 * <p>
 * 扩展栏目只认源文章所在站点的子行（D15）：站群推送到其它站点的子行，保存源文章时既不算现有的扩展栏目，也不会被删除。
 * <p>
 * 同步前先锁源文章（D17）：锁是事务里的第一次读，之后才读源文章与子行。
 * <p>
 * 改换栏目的判定（D20）：先锁落位家族的源文章，新栏目里不得已有家族的另一成员；本次保存同时取消勾选的子行除外。
 */
@SuppressWarnings("unchecked")
class ArticleExtendedChannelsTest {
    private static final long ARTICLE = 1L;
    private static final long SITE = 1L;
    private static final long OTHER_SITE = 2L;

    private ArticleMapper mapper;
    private ChannelService channelService;
    private ObjectProvider<ArticleProcessPort> processPort;
    private ArticleService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ArticleMapper.class);
        channelService = mock(ChannelService.class);
        processPort = mock(ObjectProvider.class);
        service = new ArticleService(mock(HtmlService.class), mock(HtmlSanitizeService.class),
                processPort, channelService, mock(TagService.class),
                mock(BlockItemService.class), mock(ArticleLucene.class), mapper, mock(ArticleExtMapper.class),
                mock(ArticleTagMapper.class), mock(ArticleTopicMapper.class), mock(ArticleRelatedMapper.class),
                mock(ArticleI18nMapper.class), mock(ArticleI18nAnchorService.class), mock(TopicService.class),
                mock(AttachmentService.class), mock(SnowflakeSequence.class),
                new SlugGenerator(mock(SiteMapper.class)),
                mock(ContentStatEvictor.class), mock(SiteMapper.class));
    }

    private static Article child(long id, long channelId, short type) {
        return child(id, SITE, channelId, type);
    }

    private static Article child(long id, long siteId, long channelId, short type) {
        Article article = new Article();
        article.setId(id);
        article.setSrcId(ARTICLE);
        article.setSiteId(siteId);
        article.setChannelId(channelId);
        article.setType(type);
        return article;
    }

    @Test
    void newArticleCountsEveryDesiredChannelAsAdded() {
        assertEquals(Set.of(10L, 11L, 12L), service.listAddedExtendedChannelIds(null, SITE, List.of(10L, 11L), List.of(12L)));
        verify(mapper, never()).listBySrcId(anyLong());
    }

    @Test
    void nullListsMeanNoChange() {
        assertTrue(service.listAddedExtendedChannelIds(ARTICLE, SITE, null, null).isEmpty());
        verify(mapper, never()).listBySrcId(anyLong());
    }

    @Test
    void channelsPostedBackUnchangedAreNotAdded() {
        when(mapper.listBySrcId(ARTICLE)).thenReturn(List.of(
                child(2L, 10L, Article.TYPE_REFER), child(3L, 12L, Article.TYPE_COPY)));

        // 10（REFER）、12（COPY）原样提交；11 是新加的 REFER
        assertEquals(Set.of(11L), service.listAddedExtendedChannelIds(ARTICLE, SITE, List.of(10L, 11L), List.of(12L)));
        // 去掉一个扩展栏目不算新加
        assertTrue(service.listAddedExtendedChannelIds(ARTICLE, SITE, List.of(), List.of(12L)).isEmpty());
    }

    @Test
    void switchingBetweenReferAndCopyCountsAsAdded() {
        when(mapper.listBySrcId(ARTICLE)).thenReturn(List.of(child(2L, 10L, Article.TYPE_REFER)));

        // 10 由 REFER 改为 COPY：旧子行删除、在该栏目重新建一行
        assertEquals(Set.of(10L), service.listAddedExtendedChannelIds(ARTICLE, SITE, List.of(), List.of(10L)));
    }

    @Test
    void aRowPushedToAnotherSiteIsNotAnExtendedChannel() {
        when(mapper.listBySrcId(ARTICLE)).thenReturn(List.of(
                child(2L, 10L, Article.TYPE_REFER), child(3L, OTHER_SITE, 20L, Article.TYPE_REFER)));

        // 对方站点的栏目 20 不是现有的扩展栏目：提交回来就算新加（随后因跨站点被拒绝），而不是悄悄放行
        assertEquals(Set.of(20L), service.listAddedExtendedChannelIds(ARTICLE, SITE, List.of(10L, 20L), null));
    }

    @Test
    void savingTheSourceDeletesUntickedRowsOfItsOwnSiteAndLeavesOtherSitesAlone() {
        Article src = new Article();
        src.setId(ARTICLE);
        src.setSiteId(SITE);
        src.setChannelId(5L);
        when(mapper.select(ARTICLE)).thenReturn(src);
        // 本站的 REFER（含站内推送产生的）、对方站点的 REFER 与 COPY（站群推送）
        when(mapper.listBySrcId(ARTICLE)).thenReturn(List.of(
                child(2L, 10L, Article.TYPE_REFER), child(3L, OTHER_SITE, 20L, Article.TYPE_REFER),
                child(4L, OTHER_SITE, 21L, Article.TYPE_COPY)));

        // 扩展栏目全部取消勾选
        service.assignExtendedChannels(ARTICLE, List.of(), List.of(), new User());

        verify(mapper).delete(2L);
        // 以前：对方站点的两篇也被当作“取消勾选”硬删
        verify(mapper, never()).delete(3L);
        verify(mapper, never()).delete(4L);
    }

    @Test
    void theSyncLocksTheSourceBeforeItReadsAnything() {
        Article src = new Article();
        src.setId(ARTICLE);
        src.setSiteId(SITE);
        src.setChannelId(5L);
        when(mapper.select(ARTICLE)).thenReturn(src);
        when(mapper.listBySrcId(ARTICLE)).thenReturn(List.of(child(2L, 10L, Article.TYPE_REFER)));

        // 原样提交：没有新建也没有删除，但锁仍然先于读取（D17）
        service.assignExtendedChannels(ARTICLE, List.of(10L), List.of(), new User());

        InOrder inOrder = inOrder(mapper);
        inOrder.verify(mapper).lockById(ARTICLE);
        inOrder.verify(mapper).select(ARTICLE);
        inOrder.verify(mapper).listBySrcId(ARTICLE);
        verify(mapper, never()).delete(anyLong());
        verify(mapper, never()).insert(any(Article.class));
    }

    @Test
    void nothingToSyncTakesNoLock() {
        service.assignExtendedChannels(ARTICLE, null, null, new User());
        verify(mapper, never()).lockById(anyLong());
    }

    // ---- 改换栏目（D20）

    /**
     * 库中：源文章 1 在栏目 5，引用子行 2 在栏目 10；任何栏目都有文章模型
     */
    private Article movingSource(long toChannelId) {
        Article stored = new Article();
        stored.setId(ARTICLE);
        stored.setSiteId(SITE);
        stored.setChannelId(5L);
        when(mapper.select(ARTICLE)).thenReturn(stored);
        when(mapper.listBySrcId(ARTICLE)).thenReturn(List.of(child(2L, 10L, Article.TYPE_REFER)));
        Channel channel = new Channel();
        channel.setArticleModel(new Model());
        when(channelService.select(anyLong())).thenReturn(channel);
        Article bean = new Article();
        bean.setId(ARTICLE);
        bean.setSiteId(SITE);
        bean.setChannelId(toChannelId);
        bean.setTitle("t");
        return bean;
    }

    @Test
    void movingTheSourceIntoAChildsChannelIsRefusedBeforeAnyWrite() {
        Article bean = movingSource(10L);

        // 子行所在的栏目 10 仍在扩展栏目里：拒绝
        LogicException e = assertThrows(LogicException.class,
                () -> service.update(bean, 1L, List.of(), List.of(10L), List.of(), new User()));

        assertEquals("error.article.placement.moveCollision", e.getCode());
        InOrder inOrder = inOrder(mapper);
        inOrder.verify(mapper).lockById(ARTICLE);
        inOrder.verify(mapper).select(ARTICLE);
        verify(mapper, never()).update(any(Article.class));
        verify(mapper, never()).delete(anyLong());
    }

    @Test
    void movingTheSourceIntoAChildsChannelWithoutTouchingExtendedChannelsIsRefused() {
        Article bean = movingSource(10L);
        assertThrows(LogicException.class, () -> service.update(bean, 1L, List.of(), null, null, new User()));
        verify(mapper, never()).update(any(Article.class));
    }

    @Test
    void movingTheSourceIntoAChannelThisSaveUnticksTakesTheChildsPlace() {
        Article bean = movingSource(10L);

        // 栏目 10 同时从扩展栏目里去掉：子行 2 由同步删去，源文章顶上
        service.update(bean, 1L, List.of(), List.of(), List.of(), new User());

        InOrder inOrder = inOrder(mapper);
        inOrder.verify(mapper).lockById(ARTICLE);
        inOrder.verify(mapper).update(any(Article.class));
        inOrder.verify(mapper).delete(2L);
    }

    @Test
    void movingTheSourceIntoAFreeChannelIsAccepted() {
        Article bean = movingSource(11L);
        service.update(bean, 1L, List.of(), List.of(10L), List.of(), new User());
        verify(mapper).update(any(Article.class));
        verify(mapper, never()).delete(anyLong());
    }

    @Test
    void movingAChildOntoItsSourceOrASiblingLocksTheSourceAndIsRefused() {
        Article stored = child(2L, 10L, Article.TYPE_REFER);
        Article source = new Article();
        source.setId(ARTICLE);
        source.setSiteId(SITE);
        source.setChannelId(5L);
        when(mapper.select(2L)).thenReturn(stored);
        when(mapper.select(ARTICLE)).thenReturn(source);
        when(mapper.listBySrcId(ARTICLE)).thenReturn(List.of(stored, child(3L, 12L, Article.TYPE_COPY)));
        Article bean = child(2L, 5L, Article.TYPE_REFER);

        // 移到源文章所在的栏目 5
        assertThrows(LogicException.class, () -> service.update(bean, 1L, List.of(), null, null, new User()));
        // 移到另一子行所在的栏目 12
        bean.setChannelId(12L);
        assertThrows(LogicException.class, () -> service.update(bean, 1L, List.of(), null, null, new User()));

        // 锁的是家族的源文章，不是子行自身
        verify(mapper, times(2)).lockById(ARTICLE);
        verify(mapper, never()).lockById(2L);
        verify(mapper, never()).update(any(Article.class));
    }

    @Test
    void aSaveThatKeepsTheChannelStillLocksFirstAndJudgesNoMove() {
        Article bean = movingSource(5L);
        service.update(bean, 1L, List.of(), List.of(10L), List.of(), new User());
        InOrder inOrder = inOrder(mapper);
        inOrder.verify(mapper).lockById(ARTICLE);
        inOrder.verify(mapper).update(any(Article.class));
        // 子行原样保留
        verify(mapper, never()).delete(anyLong());
        verify(mapper, never()).insert(any(Article.class));
    }

    @Test
    void isReviewerIsFalseWithoutAProcessPortAndDelegatesWhenPresent() {
        Article article = new Article();
        User user = new User();
        when(processPort.getIfAvailable()).thenReturn(null);
        assertFalse(service.isReviewer(article, user));

        ArticleProcessPort port = mock(ArticleProcessPort.class);
        when(port.isReviewer(article, user)).thenReturn(true);
        when(processPort.getIfAvailable()).thenReturn(port);
        assertTrue(service.isReviewer(article, user));
    }
}
