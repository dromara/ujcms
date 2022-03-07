package com.ujcms.core.generator;

import com.ujcms.core.domain.Article;
import com.ujcms.core.domain.Channel;
import com.ujcms.core.domain.Site;
import com.ujcms.core.domain.Task;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.service.ChannelService;
import com.ujcms.core.service.TaskService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * HTML 静态页生成
 *
 * @author PONY
 */
@Component
public class HtmlGenerator extends AbstractGenerator {
    private ChannelService channelService;
    private HtmlService htmlService;

    public HtmlGenerator(ArticleService articleService, ChannelService channelService, HtmlService htmlService,
                         TaskService taskService, @Qualifier("generator") ThreadPoolTaskExecutor executor) {
        super(articleService, taskService, executor);
        this.channelService = channelService;
        this.htmlService = htmlService;
    }

    /**
     * 更新所有静态页
     *
     * @param taskSiteId 任务站点ID
     * @param taskUserId 任务用户ID
     * @param taskName   任务名称
     * @param site       需要生成静态页的站点
     */
    public void updateAllHtml(Integer taskSiteId, Integer taskUserId, String taskName, Site site) {
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, false,
                (taskId) -> {
                    handleArticle(taskId, site.getId(), htmlService::updateArticleHtml);
                    handleChannel(taskId, site.getId(), htmlService::updateChannelHtml);
                    htmlService.updateHomeHtml(site);
                });
    }

    /**
     * 更新文章相关静态页。包括上一篇、下一篇、所属栏目、上级栏目、首页
     *
     * @param taskSiteId    任务站点ID
     * @param taskUserId    任务用户ID
     * @param taskName      任务名称
     * @param articleId     文章ID
     * @param origChannelId 文章原栏目
     */
    public void updateArticleRelatedHtml(Integer taskSiteId, Integer taskUserId, String taskName, Integer articleId,
                                         @Nullable Integer origChannelId) {
        // 上一篇、下一篇、所属栏目、所属栏目的上级栏目、首页
        Article article = articleService.select(articleId);
        if (article == null) {
            return;
        }
        Integer channelId = article.getChannelId();
        OffsetDateTime publishDate = article.getPublishDate();
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, true,
                (taskId) -> {
                    // 当前文章
                    htmlService.updateArticleHtml(article);
                    // 上一篇文章
                    Optional.ofNullable(articleService.findPrev(articleId, publishDate, channelId))
                            .ifPresent(htmlService::updateArticleHtml);
                    // 下一篇文章
                    Optional.ofNullable(articleService.findNext(articleId, publishDate, channelId))
                            .ifPresent(htmlService::updateArticleHtml);
                    // 原栏目 上一篇、下一篇 文章
                    if (origChannelId != null && !origChannelId.equals(channelId)) {
                        Optional.ofNullable(articleService.findPrev(articleId, publishDate, origChannelId))
                                .ifPresent(htmlService::updateArticleHtml);
                        Optional.ofNullable(articleService.findNext(articleId, publishDate, origChannelId))
                                .ifPresent(htmlService::updateArticleHtml);
                    }
                    // 文章所属栏目
                    Channel channel = article.getChannel();
                    do {
                        htmlService.updateChannelHtml(channel);
                        channel = channel.getParent();
                    } while (channel != null);
                    // 文章原所属栏目
                    if (origChannelId != null && !origChannelId.equals(channelId)) {
                        channel = channelService.select(origChannelId);
                        while (channel != null) {
                            htmlService.updateChannelHtml(channel);
                            channel = channel.getParent();
                        }
                    }
                    // 首页
                    htmlService.updateHomeHtml(article.getSite());
                }
        );
    }

    /**
     * 更新栏目相关静态页。包括当前栏目、上级栏目、首页。
     *
     * @param taskSiteId 任务站点ID
     * @param taskUserId 任务用户ID
     * @param taskName   任务名称
     * @param channelId  栏目ID
     */
    public void updateChannelRelatedHtml(Integer taskSiteId, Integer taskUserId, String taskName, Integer channelId) {
        // 所属栏目、所属栏目的上级栏目、首页
        Channel channel = channelService.select(channelId);
        if (channel == null) {
            return;
        }
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, true,
                (taskId) -> {
                    htmlService.updateChannelHtml(channel);
                    Channel parent = channel.getParent();
                    while (parent != null) {
                        htmlService.updateChannelHtml(parent);
                        parent = parent.getParent();
                    }
                    // 首页
                    htmlService.updateHomeHtml(channel.getSite());
                }
        );
    }

    /**
     * 更新文章静态页
     *
     * @param taskSiteId 任务站点ID
     * @param taskUserId 任务用户ID
     * @param taskName   任务名称
     * @param site       需要生成静态页的站点
     */
    public void updateArticleHtml(Integer taskSiteId, Integer taskUserId, String taskName, Site site) {
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, false,
                (taskId) -> handleArticle(taskId, site.getId(), htmlService::updateArticleHtml));
    }

    /**
     * 生成栏目静态页
     *
     * @param taskSiteId 任务站点ID
     * @param taskUserId 任务用户ID
     * @param taskName   任务名称
     * @param site       需要生成静态页的站点
     */
    public void updateChannelHtml(Integer taskSiteId, Integer taskUserId, String taskName, Site site) {
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, false,
                (taskId) -> handleChannel(taskId, site.getId(), htmlService::updateChannelHtml));
    }

    private void handleChannel(Integer taskId, Integer siteId, Consumer<Channel> consumer) {
        Map<String, Object> queryMap = new HashMap<>(16);
        queryMap.put("EQ_siteId", siteId);
        List<Channel> channels = channelService.selectList(queryMap, null);
        for (Channel channel : channels) {
            consumer.accept(channel);
            if (super.updateTask(taskId, 1)) {
                break;
            }
        }
    }
}
