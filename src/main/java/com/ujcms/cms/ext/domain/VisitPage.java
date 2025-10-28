package com.ujcms.cms.ext.domain;

import java.time.LocalDateTime;

import com.ujcms.cms.ext.domain.base.VisitPageBase;
import com.ujcms.cms.ext.domain.support.StatDateString;

/**
 * 访问页面实体类
 *
 * @author PONY
 */
public class VisitPage extends VisitPageBase implements StatDateString {

    public VisitPage() {
    }

    public VisitPage(String dateString, LocalDateTime date) {
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
     * 类型：访问地址
     */
    public static final short TYPE_VISITED_URL = 1;
    /**
     * 类型：入口地址
     */
    public static final short TYPE_ENTRY_URL = 2;
}