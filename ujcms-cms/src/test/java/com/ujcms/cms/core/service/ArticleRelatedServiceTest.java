package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.ArticleLuceneArgs;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.service.ArticleRelatedService.Candidate;
import com.ujcms.cms.core.service.ArticleRelatedService.RelatedResult;
import com.ujcms.cms.core.service.ArticleRelatedService.Seed;
import com.ujcms.cms.core.service.args.ArticleArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ArticleRelatedService} 的候选合并与渲染补齐（docs/spec/article-related/plan.md §3-4）：
 * 三路信号合并去重、排除集与非已发布过滤、得分排序（共享标签 + 同栏目 > 仅标题相似）、
 * 补齐时手选在前且不重复
 */
class ArticleRelatedServiceTest {
    private ArticleService articleService;
    private ArticleLucene lucene;
    private ArticleRelatedService service;
    private final Map<Long, Article> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        lucene = mock(ArticleLucene.class);
        service = new ArticleRelatedService(articleService, lucene);
        when(lucene.findAll(any(), any(), any())).thenReturn(new PageImpl<>(Collections.emptyList()));
        when(articleService.selectList(any(ArticleArgs.class), anyInt(), anyInt())).thenReturn(Collections.emptyList());
        when(articleService.listByIds(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            List<Article> out = new ArrayList<>();
            for (Long id : ids) {
                if (store.containsKey(id)) out.add(store.get(id));
            }
            return out;
        });
    }

    private Article article(long id, long channelId, short status, int ageDays) {
        Article a = new Article();
        a.setId(id);
        a.setSiteId(1L);
        a.setChannelId(channelId);
        a.setStatus(status);
        a.setPublishDate(OffsetDateTime.now().minusDays(ageDays));
        a.setTitle("t" + id);
        store.put(id, a);
        return a;
    }

    private static EsArticle hit(long id) {
        EsArticle e = new EsArticle();
        e.setId(id);
        return e;
    }

    private static Tag tag(long id, String name) {
        Tag t = new Tag();
        t.setId(id);
        t.setName(name);
        return t;
    }

    @Test
    void mergesSignalsAndRanksSharedTagsAboveTitleOnly() {
        Article onlyTitle = article(10L, 5L, Article.STATUS_PUBLISHED, 10);
        Article tagAndChannel = article(11L, 7L, Article.STATUS_PUBLISHED, 10);
        Article draft = article(12L, 7L, Article.STATUS_DRAFT, 1);
        when(lucene.findAll(any(ArticleLuceneArgs.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(hit(10L), hit(11L), hit(12L))));
        when(articleService.selectList(any(ArticleArgs.class), anyInt(), anyInt())).thenAnswer(inv -> {
            ArticleArgs args = inv.getArgument(0);
            if (args.getQueryMap().containsKey("EQ_article@ArticleTag-tagId_Long")) return List.of(tagAndChannel);
            if (args.getQueryMap().containsKey("EQ_channelId_Long")) return List.of(tagAndChannel, draft);
            return List.of();
        });
        Seed seed = new Seed(1L, 7L, "seed", null, List.of(tag(1L, "共同")), 99L);

        List<Candidate> result = service.candidates(seed, Set.of(), 10, false);

        assertEquals(List.of(11L, 10L), result.stream().map(c -> c.article().getId()).toList(), "草稿被过滤，标签+栏目排前");
        Candidate top = result.get(0);
        assertTrue(top.hints().containsAll(List.of(ArticleRelatedService.HINT_TITLE_SIMILAR,
                ArticleRelatedService.HINT_SHARED_TAGS, ArticleRelatedService.HINT_SAME_CHANNEL)));
        assertEquals(List.of("共同"), top.sharedTags());
        assertEquals(List.of(ArticleRelatedService.HINT_TITLE_SIMILAR), result.get(1).hints());
        assertTrue(top.score() > result.get(1).score());
        assertSame(onlyTitle, result.get(1).article());
    }

    @Test
    void honoursExcludeAndLimit() {
        article(20L, 5L, Article.STATUS_PUBLISHED, 1);
        article(21L, 5L, Article.STATUS_PUBLISHED, 2);
        article(22L, 5L, Article.STATUS_PUBLISHED, 3);
        when(lucene.findAll(any(ArticleLuceneArgs.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(hit(20L), hit(21L), hit(22L))));
        Seed seed = new Seed(1L, null, "seed", null, List.of(), null);

        List<Candidate> result = service.candidates(seed, Set.of(20L), 1, false);

        assertEquals(1, result.size());
        assertEquals(21L, result.get(0).article().getId());
    }

    @Test
    void blankSeedYieldsNothingFromLucene() {
        Seed seed = new Seed(1L, null, "", null, List.of(), null);
        assertTrue(service.candidates(seed, Set.of(), 5, false).isEmpty());
    }

    @Test
    void renderKeepsManualFirstAndFillsWithoutDuplicates() {
        Article self = article(1L, 5L, Article.STATUS_PUBLISHED, 0);
        Article manual = article(2L, 5L, Article.STATUS_PUBLISHED, 0);
        article(3L, 5L, Article.STATUS_PUBLISHED, 0);
        when(articleService.listRelated(1L)).thenReturn(List.of(manual));
        // 全文索引同时命中手选与自身：补齐须排除两者
        when(lucene.findAll(any(ArticleLuceneArgs.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(hit(2L), hit(1L), hit(3L))));

        RelatedResult result = service.listForRender(self, 3, true, null);

        assertEquals(List.of(2L, 3L), result.list().stream().map(Article::getId).toList());
        assertEquals(1, result.manualCount());

        RelatedResult noFill = service.listForRender(self, 3, false, null);
        assertEquals(List.of(2L), noFill.list().stream().map(Article::getId).toList());
    }

    @Test
    void renderTruncatesManualToLimit() {
        Article self = article(1L, 5L, Article.STATUS_PUBLISHED, 0);
        when(articleService.listRelated(1L)).thenReturn(List.of(article(2L, 5L, Article.STATUS_PUBLISHED, 0),
                article(3L, 5L, Article.STATUS_PUBLISHED, 0), article(4L, 5L, Article.STATUS_PUBLISHED, 0)));
        RelatedResult result = service.listForRender(self, 2, true, null);
        assertEquals(List.of(2L, 3L), result.list().stream().map(Article::getId).toList());
        assertEquals(2, result.manualCount());
    }
}
