package com.ujcms.cms.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.ujcms.cms.core.component.ViewCountService;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.SiteService;

/**
 * 定时任务 配置
 *
 * @author PONY
 */
@Configuration
@EnableScheduling
public class ScheduleConfig {
    private final ViewCountService viewCountService;

    public ScheduleConfig(ViewCountService viewCountService) {
        this.viewCountService = viewCountService;
    }

    @Scheduled(cron = "#{new java.util.Random().nextInt(25) + 5} * * * * ?")
    public void flushViewCountTask() {
        viewCountService.flushSiteViews();
        viewCountService.flushChannelViews();
        viewCountService.flushTopicViews();
        viewCountService.flushArticleViews();
    }

    /**
     * 更新访问统计信息。定时时间要比写入时间晚一些，以免数据未写入完成就开始统计。
     * <p>
     * 需要集群。只要在一台机器上执行即可，不需要在多台机器同时运行。
     */
    @Bean("updateViewsStatTrigger")
    public CronTriggerFactoryBean updateViewsStatTrigger(
            @Qualifier("updateViewsStatJobDetail") JobDetail updateViewsStatJobDetail) {
        CronTriggerFactoryBean factoryBean = new CronTriggerFactoryBean();
        factoryBean.setJobDetail(updateViewsStatJobDetail);
        factoryBean.setCronExpression("45 * * * * ?");
        return factoryBean;
    }

    /**
     * 更新统计任务
     */
    @Bean("updateViewsStatJobDetail")
    public JobDetailFactoryBean updateViewsStatJobDetail() {
        final JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
        factoryBean.setJobClass(UpdateViewsStatJob.class);
        // 没有绑定触发器时，必须设置持久性为 true
        factoryBean.setDurability(true);
        return factoryBean;
    }

    /**
     * 更新访问统计任务类
     */
    @Component
    public static class UpdateViewsStatJob extends QuartzJobBean {
        @Nullable
        private ViewCountService viewCountService;

        @Autowired
        public void setViewCountService(ViewCountService viewCountService) {
            this.viewCountService = viewCountService;
        }

        @Override
        protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
            Assert.notNull(viewCountService, "ViewCountService must not be null");
            viewCountService.updateViewsStat();
        }
    }

    /**
     * 更新文章状态 JobDetail
     */
    @Bean("updateArticleStatusJobDetail")
    public JobDetailFactoryBean updateArticleStatusJobDetail() {
        final JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
        factoryBean.setJobClass(UpdateArticleStatusJob.class);
        // 没有绑定触发器时，必须设置持久性为 true
        factoryBean.setDurability(true);
        return factoryBean;
    }

    /**
     * 更新文章上下线状态 Trigger。包括文章置顶和文章上下线。每10分钟执行一次。
     * <p>
     * 需要集群。只要在一台机器上执行即可，不需要在多台机器同时运行。
     */
    @Bean("updateArticleStatusTrigger")
    public CronTriggerFactoryBean updateArticleStatusTrigger(
            @Qualifier("updateArticleStatusJobDetail") JobDetail updateArticleStatusJobDetail) {
        CronTriggerFactoryBean factoryBean = new CronTriggerFactoryBean();
        factoryBean.setJobDetail(updateArticleStatusJobDetail);
        factoryBean.setCronExpression("0 0/10 * * * ?");
        return factoryBean;
    }

    /**
     * 更新文章上下线状态 Job
     */
    @Component
    public static class UpdateArticleStatusJob extends QuartzJobBean {
        private final ArticleService articleService;
        private final HtmlGenerator htmlGenerator;
        private final SiteService siteService;

        public UpdateArticleStatusJob(ArticleService articleService, HtmlGenerator htmlGenerator,
                                      SiteService siteService) {
            this.articleService = articleService;
            this.htmlGenerator = htmlGenerator;
            this.siteService = siteService;
        }

        /**
         * 置顶到期、到点上线、到点下线由 {@link ArticleService#applySchedule()} 以定向 SQL 完成并逐篇收尾（重建索引、
         * 发布监听）。这里只负责重新生成静态页：按站点分组，没有开启静态化的站点不必生成。
         * <p>
         * 以前在这里对每篇文章 {@code adjustStatus()} 后整行写回，写回的是批量 SQL 之前读出的旧值——到期的置顶就这样
         * 又被写了回来，永不到期（docs/spec/article-management/plan.md H1）
         */
        @Override
        protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
            List<Article> articles = articleService.applySchedule();
            if (articles.isEmpty()) {
                return;
            }
            Map<Long, List<Article>> bySite = articles.stream().collect(
                    Collectors.groupingBy(Article::getSiteId, LinkedHashMap::new, Collectors.toList()));
            String taskName = "task.html.articleRelated";
            bySite.forEach((siteId, list) -> {
                Site site = siteService.select(siteId);
                if (site != null && site.isHtmlEnabled()) {
                    htmlGenerator.updateArticleRelatedHtml(siteId, User.ANONYMOUS_ID, taskName, list, null);
                }
            });
        }
    }

}
