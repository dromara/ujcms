package com.ujcms.cms.core.scheduler.handler;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import com.ujcms.cms.core.domain.ScheduleTask;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.scheduler.ScheduleTaskHandler;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.common.web.exception.LogicException;

/**
 * 更新全部静态页 定时任务处理器。触发式执行：任务提交至生成器线程池后立即返回，
 * 进度记录在任务列表（ujcms_task）中，可在“生成管理”页面查看
 *
 * @author PONY
 */
@Component
public class HtmlScheduleTaskHandler implements ScheduleTaskHandler {
    private final SiteService siteService;
    private final HtmlGenerator htmlGenerator;
    private final MessageSource messageSource;

    public HtmlScheduleTaskHandler(SiteService siteService, HtmlGenerator htmlGenerator,
                                   MessageSource messageSource) {
        this.siteService = siteService;
        this.htmlGenerator = htmlGenerator;
        this.messageSource = messageSource;
    }

    @Override
    public String getType() {
        return ScheduleTask.TYPE_HTML;
    }

    @Override
    public void execute(ScheduleTask task) {
        Site site = siteService.select(task.getSiteId());
        if (site == null) {
            throw new LogicException("error.siteIdNotExist", String.valueOf(task.getSiteId()));
        }
        String taskName = messageSource.getMessage("task.html.all", null, "task.html.all", Locale.getDefault());
        htmlGenerator.updateAllHtml(site.getId(), task.getUserId(), taskName, site);
    }
}
