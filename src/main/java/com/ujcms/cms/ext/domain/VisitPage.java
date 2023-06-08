package com.ujcms.cms.ext.domain;

import com.ujcms.cms.ext.domain.base.VisitPageBase;
import com.ujcms.cms.ext.domain.support.StatDateString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 访问页面实体类
 *
 * @author PONY
 */
public class VisitPage extends VisitPageBase implements StatDateString, Serializable {
    private static final long serialVersionUID = 1L;

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