package com.ujcms.cms.ext;

import com.ujcms.cms.ext.component.VisitService;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
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
public class ScheduleConfig {
    private final VisitService visitService;

    public ScheduleConfig(VisitService visitService) {
        this.visitService = visitService;
    }

    /**
     * 将访问日志缓存写入数据库。使用随机的定时任务，以避免集群环境下所有机器同时写数据库。
     * <p>
     * 不需要集群。只在本机执行，而且每台机器都必须执行。
     */
    @Scheduled(cron = "#{new java.util.Random().nextInt(25) + 5} * * * * ?")
    public void flushVisitLogTask() {
        visitService.flushVisitLog();
    }

    /**
     * 更新统计任务
     */
    @Bean("updateVisitStatJobDetail")
    public JobDetailFactoryBean updateVisitStatJobDetail() {
        final JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
        factoryBean.setJobClass(UpdateVisitStatJob.class);
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
    public CronTriggerFactoryBean updateVisitStatTrigger(@Qualifier("updateVisitStatJobDetail") JobDetail jobDetail) {
        CronTriggerFactoryBean factoryBean = new CronTriggerFactoryBean();
        factoryBean.setJobDetail(jobDetail);
        factoryBean.setCronExpression("40 * * * * ?");
        return factoryBean;
    }

    /**
     * 更新访问统计任务类
     */
    @Component
    public static class UpdateVisitStatJob extends QuartzJobBean {
        @Nullable
        private VisitService visitService;

        @Autowired
        public void setVisitService(VisitService visitService) {
            this.visitService = visitService;
        }

        @Override
        protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
            Assert.notNull(visitService, "VisitService must not be null");
            visitService.updateStat();
            visitService.deleteStatHistory();
            visitService.updateOnlineVisitors();
        }
    }
}
