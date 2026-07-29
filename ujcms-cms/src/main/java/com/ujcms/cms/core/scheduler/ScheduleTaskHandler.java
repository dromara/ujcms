package com.ujcms.cms.core.scheduler;

import com.ujcms.cms.core.domain.ScheduleTask;

/**
 * 定时任务处理器接口。各功能模块实现此接口并注册为 Spring Bean，即可提供新的定时任务类型。
 * 商业版模块存在时会自动注册相应的处理器（如定时采集），开源版则只有核心处理器。
 *
 * @author PONY
 */
public interface ScheduleTaskHandler {
    /**
     * 任务类型唯一标识，如 html、fulltext、collection
     *
     * @return 类型标识
     */
    String getType();

    /**
     * 保存时校验任务配置（如 refId 是否有效、是否属于当前站点等）。
     * 校验失败抛出 {@link com.ujcms.common.web.exception.LogicException}
     *
     * @param task 定时任务
     */
    default void validate(ScheduleTask task) {
    }

    /**
     * 触发执行。允许抛出异常，异常将记录到任务的执行状态和错误信息中。
     * 实现应为触发式（立即返回），耗时操作应提交到各自的线程池执行，避免阻塞调度线程
     *
     * @param task 定时任务
     * @throws Exception 执行异常
     */
    void execute(ScheduleTask task) throws Exception;
}
