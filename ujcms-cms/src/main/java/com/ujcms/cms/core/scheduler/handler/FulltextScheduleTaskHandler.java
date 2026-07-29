package com.ujcms.cms.core.scheduler.handler;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.ujcms.cms.core.domain.ScheduleTask;
import com.ujcms.cms.core.generator.LuceneGenerator;
import com.ujcms.cms.core.scheduler.ScheduleTaskHandler;

/**
 * 生成本站索引 定时任务处理器。触发式执行：任务提交至生成器线程池后立即返回，
 * 进度记录在任务列表（ujcms_task）中，可在“生成管理”页面查看
 *
 * @author PONY
 */
@Component
public class FulltextScheduleTaskHandler implements ScheduleTaskHandler {
    private final LuceneGenerator luceneGenerator;
    private final MessageSource messageSource;

    public FulltextScheduleTaskHandler(LuceneGenerator luceneGenerator, MessageSource messageSource) {
        this.luceneGenerator = luceneGenerator;
        this.messageSource = messageSource;
    }

    @Override
    public String getType() {
        return ScheduleTask.TYPE_FULLTEXT;
    }

    @Override
    public void execute(ScheduleTask task) {
        String taskName = messageSource.getMessage("task.fulltext.site", null, "task.fulltext.site",
                Locale.getDefault());
        luceneGenerator.reindex(task.getSiteId(), task.getUserId(), taskName, task.getSiteId());
    }
}
