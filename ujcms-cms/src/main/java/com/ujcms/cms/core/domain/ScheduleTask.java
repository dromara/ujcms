package com.ujcms.cms.core.domain;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.quartz.CronExpression;
import org.springframework.lang.Nullable;

import com.ujcms.cms.core.domain.generated.GeneratedScheduleTask;
import com.ujcms.common.db.order.OrderEntity;

/**
 * 定时任务实体类
 *
 * @author PONY
 */
public class ScheduleTask extends GeneratedScheduleTask implements OrderEntity {

    /**
     * 获取下次执行时间。根据Cron表达式实时计算，不存储于数据库
     */
    @Nullable
    public OffsetDateTime getNextRunAt() {
        if (!Boolean.TRUE.equals(getEnabled()) || !CronExpression.isValidExpression(getCron())) {
            return null;
        }
        try {
            Date next = new CronExpression(getCron()).getNextValidTimeAfter(new Date());
            return next != null ? next.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime() : null;
        } catch (ParseException e) {
            return null;
        }
    }

    public void setError(Exception e) {
        StringWriter errorWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(errorWriter);
        e.printStackTrace(printWriter);
        setLastStatus(STATUS_ERROR);
        setErrorInfo(errorWriter.toString());
    }

    /**
     * 类型：更新全部静态页
     */
    public static final String TYPE_HTML = "html";
    /**
     * 类型：生成本站索引
     */
    public static final String TYPE_FULLTEXT = "fulltext";
    /**
     * 类型：定时采集
     */
    public static final String TYPE_COLLECTION = "collection";

    /**
     * 最近执行状态：未运行
     */
    public static final short STATUS_READY = 0;
    /**
     * 最近执行状态：成功
     */
    public static final short STATUS_SUCCESS = 1;
    /**
     * 最近执行状态：失败
     */
    public static final short STATUS_ERROR = 2;

    public static final String NOT_FOUND = "ScheduleTask not found. ID: ";
}
