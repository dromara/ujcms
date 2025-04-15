package com.ujcms.cms.core.generator;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Task;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.TaskService;
import com.ujcms.cms.core.service.args.ChannelArgs;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * HTML 静态页生成
 *
 * @author PONY
 */
@Component
public class HtmlGenerator extends AbstractGenerator {
    private final ChannelService channelService;
    private final HtmlService htmlService;
    private final SiteService siteService;

    public HtmlGenerator(ArticleService articleService, ChannelService channelService, HtmlService htmlService,
                         TaskService taskService, @Qualifier("generator") ThreadPoolTaskExecutor executor,
                         SiteService siteService) {
        super(articleService, taskService, executor);
        this.channelService = channelService;
        this.htmlService = htmlService;
        this.siteService = siteService;
    }

    /**
     * 更新所有静态页
     *
     * @param taskSiteId 任务站点ID
     * @param taskUserId 任务用户ID
     * @param taskName   任务名称
     * @param site       需要生成静态页的站点
     */
    public void updateAllHtml(Long taskSiteId, Long taskUserId, String taskName, Site site) {
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, false,
                taskId -> {
                    handleArticle(taskId, site.getId(), htmlService::updateArticleHtml);
                    handleChannel(taskId, site.getId(), htmlService::updateChannelHtml);
                    htmlService.updateHomeHtml(site);
                });
    }

    /**
     * 更新所有静态页
     *
     * @param taskSiteId 任务站点ID
     * @param taskUserId 任务用户ID
     * @param taskName   任务名称
     * @param sites      需要生成静态页的站点
     */
    public void updateAllHtml(Long taskSiteId, Long taskUserId, String taskName, List<Site> sites) {
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, false,
                taskId -> {
                    for (Site site : sites) {
                        handleArticle(taskId, site.getId(), htmlService::updateArticleHtml);
                        handleChannel(taskId, site.getId(), htmlService::updateChannelHtml);
                        htmlService.updateHomeHtml(site);
                    }
                });
    }

    /**
     * 更新文章相关静态页。包括上一篇、下一篇、所属栏目、上级栏目、首页
     *
     * @param taskSiteId    任务站点ID
     * @param taskUserId    任务用户ID
     * @param taskName      任务名称
     * @param articleList   文章列表
     * @param origChannelId 文章原栏目
     */
    public void updateArticleRelatedHtml(Long taskSiteId, Long taskUserId, String taskName,
                                         Collection<Article> articleList, @Nullable Long origChannelId) {
        Set<Article> articles = new HashSet<>(articleList.size());
        for (Article article : articleList) {
            Long articleId = article.getId();
            Long channelId = article.getChannelId();
            Long order = article.getOrder();
            // 当前文章直接更新，以免导致404错误
            Optional.ofNullable(articleService.select(articleId))
                    .filter(it -> it.getSite().isHtmlEnabled()).ifPresent(htmlService::updateArticleHtml);
            // 上一篇文章
            Optional.ofNullable(articleService.findPrev(articleId, order, channelId))
                    .filter(it -> it.getSite().isHtmlEnabled()).ifPresent(articles::add);
            // 下一篇文章
            Optional.ofNullable(articleService.findNext(articleId, order, channelId))
                    .filter(it -> it.getSite().isHtmlEnabled()).ifPresent(articles::add);
            // 原栏目 上一篇、下一篇 文章
            if (origChannelId != null && !origChannelId.equals(channelId)) {
                Optional.ofNullable(articleService.findPrev(articleId, order, origChannelId))
                        .filter(it -> it.getSite().isHtmlEnabled()).ifPresent(articles::add);
                Optional.ofNullable(articleService.findNext(articleId, order, origChannelId))
                        .filter(it -> it.getSite().isHtmlEnabled()).ifPresent(articles::add);
            }
        }
        if (articles.isEmpty()) {
            return;
        }
        Set<Channel> channels = new HashSet<>(articles.size());
        for (Channel channel : articles.stream().map(Article::getChannel).collect(Collectors.toSet())) {
            do {
                // 文章所属栏目及上级栏目
                channels.add(channel);
                channel = channel.getParent();
            } while (channel != null);
        }
        // 原栏目
        Optional.ofNullable(origChannelId).map(channelService::select).ifPresent(channels::add);
        Set<Site> sites = channels.stream().map(Channel::getSite).collect(Collectors.toSet());

        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, true,
                taskId -> {
                    // 更新文章HTML
                    articles.forEach(htmlService::updateArticleHtml);
                    // 更新栏目HTML
                    channels.forEach(htmlService::updateChannelHtml);
                    // 更新首页HTML
                    sites.forEach(htmlService::updateHomeHtml);
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
    public void updateChannelRelatedHtml(Long taskSiteId, Long taskUserId, String taskName, Long channelId) {
        // 所属栏目、所属栏目的上级栏目、首页
        Channel channel = channelService.select(channelId);
        if (channel == null) {
            return;
        }
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, true,
                taskId -> {
                    htmlService.updateChannelHtml(channel);
                    Channel parent = channel.getParent();
                    while (parent != null) {
                        htmlService.updateChannelHtml(parent);
                        parent = parent.getParent();
                    }
                    // 首页
                    Site site = siteService.select(channel.getSiteId());
                    if (site != null) {
                        htmlService.updateHomeHtml(site);
                    }
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
    public void updateArticleHtml(Long taskSiteId, Long taskUserId, String taskName, Site site) {
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, false,
                taskId -> handleArticle(taskId, site.getId(), htmlService::updateArticleHtml));
    }

    /**
     * 生成栏目静态页
     *
     * @param taskSiteId 任务站点ID
     * @param taskUserId 任务用户ID
     * @param taskName   任务名称
     * @param site       需要生成静态页的站点
     */
    public void updateChannelHtml(Long taskSiteId, Long taskUserId, String taskName, Site site) {
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, false,
                taskId -> handleChannel(taskId, site.getId(), htmlService::updateChannelHtml));
    }

    private void handleChannel(Long taskId, Long siteId, Consumer<Channel> consumer) {
        List<Channel> channels = channelService.selectList(ChannelArgs.of().siteId(siteId));
        for (Channel channel : channels) {
            // 必须通过 select 获取完整数据， selectList 只适合列表，部分字段无数据
            consumer.accept(channelService.select(channel.getId()));
            if (super.updateTask(taskId, 1)) {
                break;
            }
        }
    }
}
