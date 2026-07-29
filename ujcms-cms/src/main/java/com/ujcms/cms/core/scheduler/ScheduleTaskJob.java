package com.ujcms.cms.core.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * 定时任务 Quartz Job。从 JobDataMap 中获取任务ID并交由 {@link ScheduleTaskRunner} 执行
 *
 * @author PONY
 */
@DisallowConcurrentExecution
public class ScheduleTaskJob extends QuartzJobBean {
    /**
     * JobDataMap 中定时任务ID的键名
     */
    public static final String DATA_ID = "scheduleTaskId";

    private final ScheduleTaskRunner runner;

    public ScheduleTaskJob(ScheduleTaskRunner runner) {
        this.runner = runner;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        runner.run(context.getMergedJobDataMap().getLong(DATA_ID), false);
    }
}
