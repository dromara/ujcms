package com.ujcms.cms.core.service;

import com.ujcms.cms.core.component.ContentStatEvictor;
import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Article.RelatedItem;
import com.ujcms.cms.core.domain.ArticleRelated;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.LogicException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link ArticleService#assignRelated} 的写不变量（docs/spec/article-related/plan.md §3）：
 * 自关联、跨站、超上限拒绝；出向行整体替换且顺序为调用方顺序；互相关联按 mutual 增删反向行；重复 ID 去重
 */
@SuppressWarnings("unchecked")
class ArticleRelatedAssignTest {
    private ArticleMapper mapper;
    private ArticleRelatedMapper relatedMapper;
    private ArticleService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ArticleMapper.class);
        relatedMapper = mock(ArticleRelatedMapper.class);
        service = new ArticleService(mock(HtmlService.class), mock(HtmlSanitizeService.class),
                mock(ObjectProvider.class), mock(ChannelService.class), mock(TagService.class),
                mock(BlockItemService.class), mock(ArticleLucene.class), mapper, mock(ArticleExtMapper.class),
                mock(ArticleTagMapper.class), mock(ArticleTopicMapper.class), relatedMapper,
                mock(ArticleI18nMapper.class), mock(ArticleI18nAnchorService.class), mock(TopicService.class),
                mock(AttachmentService.class), mock(SnowflakeSequence.class), mock(SlugGenerator.class),
                mock(ContentStatEvictor.class), mock(SiteMapper.class));
        when(mapper.select(anyLong())).thenAnswer(inv -> article(inv.getArgument(0), 1L));
    }

    private static Article article(Long id, Long siteId) {
        Article a = new Article();
        a.setId(id);
        a.setSiteId(siteId);
        return a;
    }

    private static RelatedItem item(long id, boolean mutual) {
        RelatedItem i = new RelatedItem();
        i.setId(id);
        i.setMutual(mutual);
        return i;
    }

    @Test
    void rejectsSelfCrossSiteAndTooMany() {
        assertThrows(LogicException.class, () -> service.assignRelated(1L, List.of(item(1L, false))));
        when(mapper.select(9L)).thenReturn(article(9L, 2L));
        assertThrows(LogicException.class, () -> service.assignRelated(1L, List.of(item(9L, false))));
        List<RelatedItem> many = IntStream.rangeClosed(100, 100 + ArticleService.RELATED_MAX)
                .mapToObj(i -> item(i, false)).collect(Collectors.toList());
        assertThrows(LogicException.class, () -> service.assignRelated(1L, many));
        verify(relatedMapper, never()).deleteOutgoing(anyLong());
    }

    @Test
    void replacesOutgoingRowsInCallerOrderAndDedupes() {
        service.assignRelated(1L, List.of(item(3L, false), item(2L, false), item(3L, false)));
        verify(relatedMapper).deleteOutgoing(1L);
        ArgumentCaptor<List<ArticleRelated>> rows = ArgumentCaptor.forClass(List.class);
        verify(relatedMapper).insertAll(rows.capture());
        assertEquals(List.of(3L, 2L), rows.getValue().stream().map(ArticleRelated::getRelatedId).toList());
        assertEquals(List.of(0, 10000), rows.getValue().stream().map(ArticleRelated::getOrder).toList());
        assertEquals(1L, rows.getValue().get(0).getArticleId());
    }

    @Test
    void emptyListClearsWithoutInsert() {
        service.assignRelated(1L, new ArrayList<>());
        verify(relatedMapper).deleteOutgoing(1L);
        verify(relatedMapper, never()).insertAll(any());
    }

    @Test
    void mutualSyncCreatesReverseRowAtEndAndRemovesWhenOneWay() {
        // 2 尚无反向行且要求互链 → 追加到对方列表末尾；3 已有反向行但改为单向 → 删除；4 已有且互链 → 不动
        when(relatedMapper.exists(2L, 1L)).thenReturn(0);
        when(relatedMapper.maxOrderByArticleId(2L)).thenReturn(30000);
        when(relatedMapper.exists(3L, 1L)).thenReturn(1);
        when(relatedMapper.exists(4L, 1L)).thenReturn(1);
        List<Article> changed = service.assignRelated(1L, List.of(item(2L, true), item(3L, false), item(4L, true)));

        ArgumentCaptor<ArticleRelated> inserted = ArgumentCaptor.forClass(ArticleRelated.class);
        verify(relatedMapper).insert(inserted.capture());
        assertEquals(2L, inserted.getValue().getArticleId());
        assertEquals(1L, inserted.getValue().getRelatedId());
        assertEquals(40000, inserted.getValue().getOrder());
        verify(relatedMapper).delete(3L, 1L);
        verify(relatedMapper, never()).delete(4L, 1L);
        assertEquals(List.of(2L, 3L), changed.stream().map(Article::getId).toList());
    }

    @Test
    void firstReverseRowStartsAtZero() {
        when(relatedMapper.exists(2L, 1L)).thenReturn(0);
        when(relatedMapper.maxOrderByArticleId(2L)).thenReturn(null);
        service.assignRelated(1L, List.of(item(2L, true)));
        ArgumentCaptor<ArticleRelated> inserted = ArgumentCaptor.forClass(ArticleRelated.class);
        verify(relatedMapper).insert(inserted.capture());
        assertEquals(0, inserted.getValue().getOrder());
    }
}
