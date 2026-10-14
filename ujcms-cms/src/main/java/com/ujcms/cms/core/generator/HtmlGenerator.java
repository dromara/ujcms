package com.ujcms.cms.core.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Task;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.TaskService;
import com.ujcms.cms.core.service.args.ChannelArgs;

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
                         TaskService taskService, @Qualifier("generatorTaskExecutor") ThreadPoolTaskExecutor executor,
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
                    htmlService.deleteHomeHtml(site);
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
                        htmlService.deleteHomeHtml(site);
                        htmlService.updateHomeHtml(site);
                    }
                });
    }

    /**
     * 更新文章相关静态页。包括上一篇、下一篇、所属栏目、上级栏目、首页
     * <p>
     * 只把文章、栏目的 ID 带进任务，生成时再按 ID 读取：任务在线程池里排队，排队之前读出的对象到执行时可能已经
     * 过时（文章期间被编辑），而经文章关联带出的栏目只有部分字段，拿去渲染栏目页会出错
     * （docs/spec/article-management/plan.md Phase 1.4）
     *
     * @param taskSiteId    任务站点ID
     * @param taskUserId    任务用户ID
     * @param taskName      任务名称
     * @param articleList   文章列表
     * @param origChannelId 文章原栏目
     */
    public void updateArticleRelatedHtml(Long taskSiteId, Long taskUserId, String taskName,
                                         Collection<Article> articleList, @Nullable Long origChannelId) {
        Set<Long> articleIds = new LinkedHashSet<>();
        Set<Long> channelIds = new LinkedHashSet<>();
        for (Article article : articleList) {
            Long articleId = article.getId();
            Long channelId = article.getChannelId();
            Long order = article.getOrder();
            // 当前文章直接更新，以免导致404错误
            Article current = articleService.select(articleId);
            if (current != null && current.getSite().isHtmlEnabled()) {
                htmlService.updateArticleHtml(current);
                // 文章所在栏目的列表页也变了——即使它没有上一篇、下一篇
                channelIds.add(channelId);
            }
            // 上一篇、下一篇文章
            collectNeighbor(articleService.findPrev(articleId, order, channelId), articleIds, channelIds);
            collectNeighbor(articleService.findNext(articleId, order, channelId), articleIds, channelIds);
            // 原栏目 上一篇、下一篇 文章
            if (origChannelId != null && !origChannelId.equals(channelId)) {
                collectNeighbor(articleService.findPrev(articleId, order, origChannelId), articleIds, channelIds);
                collectNeighbor(articleService.findNext(articleId, order, origChannelId), articleIds, channelIds);
            }
        }
        if (channelIds.isEmpty()) {
            return;
        }
        // 原栏目
        Optional.ofNullable(origChannelId).ifPresent(channelIds::add);

        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, true,
                taskId -> {
                    // 更新文章HTML
                    for (Long articleId : articleIds) {
                        Optional.ofNullable(articleService.select(articleId)).ifPresent(htmlService::updateArticleHtml);
                    }
                    // 更新栏目HTML（各栏目及其上级栏目）、首页HTML
                    updateChannelsAndHome(channelIds, null);
                }
        );
    }

    private static void collectNeighbor(@Nullable Article neighbor, Set<Long> articleIds, Set<Long> channelIds) {
        if (neighbor != null && neighbor.getSite().isHtmlEnabled()) {
            articleIds.add(neighbor.getId());
            channelIds.add(neighbor.getChannelId());
        }
    }

    /**
     * 生成一批栏目、它们的各级上级栏目（每个只生成一次），以及这些栏目所在站点的首页。栏目逐个按 ID 完整读取
     *
     * @param homeSiteId 另外要更新首页的站点（栏目已被删除时，从栏目无从得知站点）。可为 {@code null}
     */
    private void updateChannelsAndHome(Collection<Long> channelIds, @Nullable Long homeSiteId) {
        Set<Long> done = new HashSet<>();
        Set<Long> siteIds = new LinkedHashSet<>();
        Optional.ofNullable(homeSiteId).ifPresent(siteIds::add);
        for (Long channelId : channelIds) {
            Long id = channelId;
            // 已随别的栏目生成过的，它的各级上级也已生成
            while (id != null && done.add(id)) {
                Channel channel = channelService.select(id);
                if (channel == null) {
                    break;
                }
                htmlService.updateChannelHtml(channel);
                siteIds.add(channel.getSiteId());
                id = channel.getParentId();
            }
        }
        for (Long siteId : siteIds) {
            Site site = siteService.select(siteId);
            if (site != null) {
                htmlService.deleteHomeHtml(site);
                htmlService.updateHomeHtml(site);
            }
        }
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
        if (channelService.select(channelId) == null) {
            return;
        }
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, true,
                taskId -> updateChannelsAndHome(List.of(channelId), null));
    }

    /**
     * 更新一批栏目的关联静态页：各栏目、它们的各级上级栏目（每个只生成一次）、首页。范围同
     * {@link #updateChannelRelatedHtml}，合成一个任务
     *
     * @param taskSiteId 任务站点ID
     * @param taskUserId 任务用户ID
     * @param taskName   任务名称
     * @param channelIds 栏目ID
     */
    public void updateChannelsRelatedHtml(Long taskSiteId, Long taskUserId, String taskName,
                                          Collection<Long> channelIds) {
        List<Long> ids = new ArrayList<>(channelIds);
        execute(taskSiteId, taskUserId, taskName, Task.TYPE_HTML, true,
                taskId -> updateChannelsAndHome(ids, taskSiteId));
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
