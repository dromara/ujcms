package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.base.TaskBase;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.time.OffsetDateTime;

/**
 * 任务实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Task extends TaskBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public Task() {
    }

    public Task(Long siteId, Long userId, String name, short type) {
        setSiteId(siteId);
        setUserId(userId);
        setName(name);
        setType(type);
    }

    /**
     * 获取运行时间
     */
    public long getProcessedIn() {
        OffsetDateTime endDate = getEndDate();
        long endMillis = endDate != null ? endDate.toInstant().toEpochMilli() : System.currentTimeMillis();
        return (endMillis - getBeginDate().toInstant().toEpochMilli()) / 1000;
    }

    public void setError(Exception e) {
        StringWriter errorWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(errorWriter);
        e.printStackTrace(printWriter);
        setEndDate(OffsetDateTime.now());
        setStatus(Task.STATUS_ERROR);
        setErrorInfo(errorWriter.toString());
    }

    @JsonIncludeProperties({"id", "username", "nickname"})
    private User user = new User();

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    /**
     * 类型：生成静态页
     */
    public static final short TYPE_HTML = 1;
    /**
     * 类型：生成全文索引
     */
    public static final short TYPE_LUCENE = 2;


    /**
     * 状态：等待
     */
    public static final short STATUS_WAITING = 0;
    /**
     * 状态：运行中
     */
    public static final short STATUS_RUNNING = 1;
    /**
     * 状态：出错
     */
    public static final short STATUS_ERROR = 2;
    /**
     * 状态：停止
     */
    public static final short STATUS_STOP = 3;
    /**
     * 状态：完成
     */
    public static final short STATUS_DONE = 4;

    public static final String NOT_FOUND = "Task not found. ID: ";
}