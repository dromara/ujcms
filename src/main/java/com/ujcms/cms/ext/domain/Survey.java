package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.ext.domain.base.SurveyBase;
import com.ujcms.commons.db.order.OrderEntity;
import com.ujcms.commons.web.Views;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 调查问卷 实体类
 *
 * @author PONY
 */
public class Survey extends SurveyBase implements Serializable, OrderEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 状态
     */
    public short getStatus() {
        OffsetDateTime now = OffsetDateTime.now();
        if (Boolean.FALSE.equals(getEnabled())) {
            return STATUS_DISABLED;
        } else if (getBeginDate() != null && now.compareTo(getBeginDate()) < 0) {
            return STATUS_NOT_STARTED;
        } else if (getEndDate() != null && now.compareTo(getEndDate()) > 0) {
            return STATUS_HAS_ENDED;
        } else {
            return STATUS_NORMAL;
        }
    }

    @JsonView(Views.Whole.class)
    private List<SurveyItem> items = new ArrayList<>();

    public List<SurveyItem> getItems() {
        return items;
    }

    public void setItems(List<SurveyItem> items) {
        this.items = items;
    }

    /**
     * 模式：独立访客
     */
    public static final short MODE_COOKIE = 1;
    /**
     * 模式：独立IP
     */
    public static final short MODE_IP = 2;
    /**
     * 模式：独立用户
     */
    public static final short MODE_USER = 3;

    /**
     * 状态：正常
     */
    public static final short STATUS_NORMAL = 0;
    /**
     * 状态：未启用
     */
    public static final short STATUS_DISABLED = 1;
    /**
     * 状态：未开始
     */
    public static final short STATUS_NOT_STARTED = 2;
    /**
     * 状态：已结束
     */
    public static final short STATUS_HAS_ENDED = 3;

    public static final String NOT_FOUND = "Survey not found. ID: ";
}