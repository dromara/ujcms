package com.ujcms.cms.core.scheduler;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import com.ujcms.cms.core.domain.ScheduleTask;
import com.ujcms.cms.core.service.ScheduleTaskService;

/**
 * 定时任务调度器。负责向 Quartz 注册、注销任务。
 * <p>
 * 数据库为唯一事实来源：默认使用 Quartz 内存 JobStore（RAMJobStore），应用启动时重建所有已启用任务的调度。
 * <p>
 * 集群注意：使用 RAMJobStore 时每个节点都会触发任务。采集任务通过数据库状态抢占（CAS）避免重复执行，
 * 但静态页生成会在每个节点各执行一次。集群环境应在 application.yaml 中启用
 * {@code spring.quartz.job-store-type: jdbc} 及 Quartz 集群配置（Quartz 表结构已随 Liquibase 提供）
 *
 * @author PONY
 */
@Component
public class ScheduleTaskScheduler implements SmartInitializingSingleton {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleTaskScheduler.class);

    /**
     * Quartz 任务分组名
     */
    private static final String GROUP = "ujcmsScheduleTask";

    private final Scheduler scheduler;
    private final ScheduleTaskService service;

    public ScheduleTaskScheduler(Scheduler scheduler, ScheduleTaskService service) {
        this.scheduler = scheduler;
        this.service = service;
    }

    /**
     * 注册或更新任务调度。采用先删后建的方式，可处理 Cron 修改、启用、禁用等所有情况
     */
    public void schedule(ScheduleTask task) {
        try {
            scheduler.deleteJob(jobKey(task.getId()));
            if (!Boolean.TRUE.equals(task.getEnabled())) {
                return;
            }
            JobDetail job = JobBuilder.newJob(ScheduleTaskJob.class)
                    .withIdentity(jobKey(task.getId()))
                    .usingJobData(ScheduleTaskJob.DATA_ID, task.getId())
                    .build();
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(task.getId()))
                    // 错过触发时间（如应用停机期间）不补偿执行，等待下一次触发
                    .withSchedule(CronScheduleBuilder.cronSchedule(task.getCron())
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Schedule task failed. ID: " + task.getId(), e);
        }
    }

    /**
     * 注销任务调度
     */
    public void unschedule(Long id) {
        try {
            scheduler.deleteJob(jobKey(id));
        } catch (SchedulerException e) {
            throw new IllegalStateException("Unschedule task failed. ID: " + id, e);
        }
    }

    /**
     * 应用启动时重建所有已启用任务的调度
     */
    @Override
    public void afterSingletonsInstantiated() {
        for (ScheduleTask task : service.listEnabled()) {
            try {
                schedule(task);
            } catch (Exception e) {
                // 单个任务调度失败（如无效的Cron表达式）不影响其它任务
                logger.error("Invalid schedule task: {}({})", task.getName(), task.getId(), e);
            }
        }
    }

    private static JobKey jobKey(Long id) {
        return JobKey.jobKey("scheduleTask-" + id, GROUP);
    }

    private static TriggerKey triggerKey(Long id) {
        return TriggerKey.triggerKey("scheduleTask-" + id, GROUP);
    }
}
