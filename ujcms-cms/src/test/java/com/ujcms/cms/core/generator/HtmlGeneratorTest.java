package com.ujcms.cms.core.generator;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Task;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量设置栏目后的静态页任务（docs/spec/channel-management/plan.md D20）：范围同单个编辑——栏目页、各级上级栏目页、首页，
 * 合成一个任务，每个页面只生成一次。
 */
class HtmlGeneratorTest {
    private static final long SITE = 1L;

    private ChannelService channelService;
    private HtmlService htmlService;
    private TaskService taskService;
    private SiteService siteService;
    private ArticleService articleService;
    private HtmlGenerator generator;

    @BeforeEach
    void setUp() {
        channelService = mock(ChannelService.class);
        htmlService = mock(HtmlService.class);
        taskService = mock(TaskService.class);
        siteService = mock(SiteService.class);
        // 任务在调用线程里直接执行
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        when(taskService.select(anyLong())).thenAnswer(invocation -> new Task());
        articleService = mock(ArticleService.class);
        generator = new HtmlGenerator(articleService, channelService, htmlService, taskService,
                executor, siteService);
    }

    private Channel channel(long id, Channel parent) {
        Channel bean = new Channel();
        bean.setId(id);
        bean.setSiteId(SITE);
        if (parent != null) {
            bean.setParentId(parent.getId());
            bean.setParent(parent);
        }
        when(channelService.select(id)).thenReturn(bean);
        return bean;
    }

    @Test
    void updateChannelsRelatedHtmlGeneratesEachChannelAndAncestorOnceThenHome() {
        Channel root = channel(10L, null);
        Channel child = channel(11L, root);
        Channel sibling = channel(12L, root);
        Channel grandchild = channel(13L, child);
        Site site = new Site();
        when(siteService.select(SITE)).thenReturn(site);

        // 13 带出 11、10；12 的上级 10 已生成；10 本身也已生成
        generator.updateChannelsRelatedHtml(SITE, 2L, "task", List.of(13L, 12L, 10L));

        verify(taskService, times(1)).insert(any(Task.class));
        verify(htmlService, times(1)).updateChannelHtml(grandchild);
        verify(htmlService, times(1)).updateChannelHtml(child);
        verify(htmlService, times(1)).updateChannelHtml(sibling);
        verify(htmlService, times(1)).updateChannelHtml(root);
        verify(htmlService, times(4)).updateChannelHtml(any(Channel.class));
        verify(htmlService, times(1)).deleteHomeHtml(site);
        verify(htmlService, times(1)).updateHomeHtml(site);
    }

    @Test
    void updateChannelsRelatedHtmlSkipsChannelsDeletedMeanwhile() {
        Channel root = channel(10L, null);
        when(channelService.select(99L)).thenReturn(null);
        when(siteService.select(SITE)).thenReturn(new Site());

        generator.updateChannelsRelatedHtml(SITE, 2L, "task", List.of(99L, 10L));

        verify(htmlService, times(1)).updateChannelHtml(root);
        verify(htmlService, times(1)).updateChannelHtml(any(Channel.class));
    }

    // region 文章相关静态页（docs/spec/article-management/plan.md Phase 1.4）

    private Article article(long id, long channelId) {
        // 静态化已开启的站点
        Site site = mock(Site.class);
        when(site.isHtmlEnabled()).thenReturn(true);
        Article bean = new Article();
        bean.setId(id);
        bean.setSiteId(SITE);
        bean.setSite(site);
        bean.setChannelId(channelId);
        return bean;
    }

    @Test
    void anArticleWithoutNeighboursStillRegeneratesItsChannelsAndHome() {
        Channel root = channel(10L, null);
        Channel child = channel(11L, root);
        Article current = article(1L, 11L);
        when(articleService.select(1L)).thenReturn(current);
        Site site = new Site();
        when(siteService.select(SITE)).thenReturn(site);

        // 栏目里只有这一篇：没有上一篇、下一篇。以前到此就返回了，栏目页和首页都不更新
        generator.updateArticleRelatedHtml(SITE, 2L, "task", List.of(current), null);

        verify(htmlService, times(1)).updateArticleHtml(current);
        verify(htmlService, times(1)).updateChannelHtml(child);
        verify(htmlService, times(1)).updateChannelHtml(root);
        verify(htmlService, times(1)).updateHomeHtml(site);
    }

    @Test
    void neighboursAndChannelsAreReadAgainInsideTheTask() {
        Channel channel = channel(11L, null);
        Article current = article(1L, 11L);
        when(articleService.select(1L)).thenReturn(current);
        // 排队之前读到的上一篇
        Article stale = article(2L, 11L);
        when(articleService.findPrev(1L, current.getOrder(), 11L)).thenReturn(stale);
        // 任务执行时按 ID 读到的现值（期间被编辑过）
        Article fresh = article(2L, 11L);
        when(articleService.select(2L)).thenReturn(fresh);
        when(siteService.select(SITE)).thenReturn(new Site());

        generator.updateArticleRelatedHtml(SITE, 2L, "task", List.of(current), null);

        // 生成用的是任务里重新读出的对象；栏目来自 channelService.select（完整读取），不是文章关联带出的那个
        verify(htmlService, times(2)).updateArticleHtml(any(Article.class));
        verify(htmlService).updateArticleHtml(current);
        verify(htmlService, times(1)).updateChannelHtml(channel);
        verify(articleService, times(1)).select(2L);
    }

    @Test
    void aSiteWithoutStaticPagesProducesNoTask() {
        Site site = mock(Site.class);
        when(site.isHtmlEnabled()).thenReturn(false);
        Article current = new Article();
        current.setId(1L);
        current.setSiteId(SITE);
        current.setSite(site);
        current.setChannelId(11L);
        when(articleService.select(1L)).thenReturn(current);

        generator.updateArticleRelatedHtml(SITE, 2L, "task", List.of(current), null);

        verify(htmlService, never()).updateArticleHtml(any(Article.class));
        verify(taskService, never()).insert(any(Task.class));
    }

    // endregion
}
