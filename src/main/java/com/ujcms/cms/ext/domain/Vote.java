package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.ext.domain.base.VoteBase;
import com.ujcms.cms.ext.domain.base.VoteOptionBase;
import com.ujcms.commons.db.order.OrderEntity;
import com.ujcms.commons.web.Views;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 投票 实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Vote extends VoteBase implements Serializable, OrderEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 总票数
     */
    public int getTotal() {
        return getOptions().stream().mapToInt(VoteOptionBase::getCount).sum();
    }

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
    private List<VoteOption> options = new ArrayList<>();

    public List<VoteOption> getOptions() {
        return options;
    }

    public void setOptions(List<VoteOption> options) {
        this.options = options;
    }

    public static final String ACTION_TYPE = "Vote";

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

    public static final String NOT_FOUND = "Vote not found. ID: ";
}