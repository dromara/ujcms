package com.ujcms.cms.core;

import com.ujcms.cms.core.component.ViewCountService;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

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
        viewCountService.flushArticleViews();
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
     * 更新访问统计信息。定时时间要比写入时间晚一些，以免数据未写入完成就开始统计。
     * <p>
     * 需要集群。只要在一台机器上执行即可，不需要在多台机器同时运行。
     */
    @Bean
    public CronTriggerFactoryBean updateViewsStatTrigger(@Qualifier("updateViewsStatJobDetail") JobDetail jobDetail) {
        CronTriggerFactoryBean factoryBean = new CronTriggerFactoryBean();
        factoryBean.setJobDetail(jobDetail);
        factoryBean.setCronExpression("45 * * * * ?");
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
        public void setViewCountService(@Nullable ViewCountService viewCountService) {
            this.viewCountService = viewCountService;
        }

        @Override
        protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
            Assert.notNull(viewCountService, "ViewCountService must not be null");
            viewCountService.updateViewsStat();
        }
    }
}
