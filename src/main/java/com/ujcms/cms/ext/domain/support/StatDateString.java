package com.ujcms.cms.ext.domain.support;

import java.time.LocalDateTime;

/**
 * @author PONY
 */
public interface StatDateString {
    /**
     * 获取统计日期字符串
     *
     * @return 统计日期字符串
     */
    String getDateString();

    /**
     * 设置统计日期字符串
     *
     * @param dateString 统计日期字符串
     */
    void setDateString(String dateString);

    /**
     * 设置日期
     *
     * @param date 日期
     */
    void setDate(LocalDateTime date);
}
