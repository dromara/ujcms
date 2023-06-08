package com.ujcms.cms.ext.domain;

import com.ujcms.cms.ext.domain.base.VisitTrendBase;
import com.ujcms.cms.ext.domain.support.StatDateString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 访问趋势实体类
 *
 * @author PONY
 */
public class VisitTrend extends VisitTrendBase implements StatDateString, Serializable {
    private static final long serialVersionUID = 1L;

    public VisitTrend() {
    }

    public VisitTrend(String dateString, LocalDateTime date) {
        setDateString(dateString);
        setDate(date);
    }

    private LocalDateTime date = LocalDateTime.now();

    public LocalDateTime getDate() {
        return date;
    }

    @Override
    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    /**
     * 数据保留天数：分（十二时内按分显示）
     */
    public static final int DATA_MAX_DAY_FOR_MINUTE = 2;
    /**
     * 数据保留天数：时（十四天内按时显示）
     */
    public static final int DATA_MAX_DAY_FOR_HOUR = 31;
    /**
     * 数据保留天数：日（六月内按天显示）
     * <p>
     * 月数据永久保存
     */
    public static final int DATA_MAX_DAY_FOR_DAY = 732;
    /**
     * 统计周期：分
     */
    public static final short PERIOD_MINUTE = 1;
    /**
     * 统计周期：时
     */
    public static final short PERIOD_HOUR = 2;
    /**
     * 统计周期：日
     */
    public static final short PERIOD_DAY = 3;
    /**
     * 统计周期：月
     */
    public static final short PERIOD_MONTH = 4;
}