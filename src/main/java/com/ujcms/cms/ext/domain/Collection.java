package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.collector.CollectorUtils;
import com.ujcms.cms.ext.domain.base.CollectionBase;
import com.ujcms.commons.db.order.OrderEntity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集实体类
 *
 * @author PONY
 */
public class Collection extends CollectionBase implements Serializable, OrderEntity {
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    public long getInterval() {
        long min = getIntervalMin() * 1000L;
        long max = getIntervalMax() * 1000L;
        return CollectorUtils.randomBetween(min, max);
    }

    public int getTotalPages() {
        return CollectorUtils.assembleListUrls(getListUrls(), getPageBegin(), getPageEnd(), getListDesc()).size();
    }

    private Site site = new Site();
    private Channel channel = new Channel();
    private User user = new User();
    private List<CollectionField> fields = new ArrayList<>();

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<CollectionField> getFields() {
        return fields;
    }

    public void setFields(List<CollectionField> fields) {
        this.fields = fields;
    }

    /**
     * 类型：系统字段
     */
    public static final short TYPE_SYSTEM = 1;
    /**
     * 类型：自定义字段
     */
    public static final short TYPE_CUSTOM = 2;

    /**
     * 状态：未运行
     */
    public static final short STATUS_READY = 0;
    /**
     * 状态：运行中
     */
    public static final short STATUS_RUNNING = 1;
    /**
     * 状态：暂停
     */
    public static final short STATUS_PAUSE = 2;
    /**
     * 状态：完成
     */
    public static final short STATUS_DONE = 3;
    /**
     * 状态：出错
     */
    public static final short STATUS_ERROR = 4;

    public static final String NOT_FOUND = "Collection not found. ID: ";
}