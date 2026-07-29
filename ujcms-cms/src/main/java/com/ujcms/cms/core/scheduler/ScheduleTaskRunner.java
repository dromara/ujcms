package com.ujcms.cms.core.scheduler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ujcms.cms.core.domain.ScheduleTask;
import com.ujcms.cms.core.service.ScheduleTaskService;

/**
 * 定时任务执行器。负责执行任务并记录执行结果
 *
 * @author PONY
 */
@Component
public class ScheduleTaskRunner {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleTaskRunner.class);

    private final ScheduleTaskService service;
    private final ScheduleTaskHandlerRegistry registry;

    public ScheduleTaskRunner(ScheduleTaskService service, ScheduleTaskHandlerRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    /**
     * 执行定时任务
     *
     * @param id     定时任务ID
     * @param manual 是否手动触发。手动触发时忽略启用状态
     */
    public void run(Long id, boolean manual) {
        ScheduleTask task = service.select(id);
        // 任务已删除或已禁用：数据库为唯一事实来源，调度器中的陈旧触发器安全空转（重启后消失）
        if (task == null || (!manual && !Boolean.TRUE.equals(task.getEnabled()))) {
            return;
        }
        OffsetDateTime begin = OffsetDateTime.now();
        try {
            ScheduleTaskHandler handler = registry.getHandler(task.getType()).orElseThrow(
                    () -> new IllegalStateException("ScheduleTask handler not found: " + task.getType()));
            handler.execute(task);
            service.updateRunResult(id, begin, ScheduleTask.STATUS_SUCCESS, null);
        } catch (Exception e) {
            logger.error("ScheduleTask failed: {}({})", task.getName(), id, e);
            StringWriter errorWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(errorWriter);
            e.printStackTrace(printWriter);
            service.updateRunResult(id, begin, ScheduleTask.STATUS_ERROR, errorWriter.toString());
        }
    }
}
