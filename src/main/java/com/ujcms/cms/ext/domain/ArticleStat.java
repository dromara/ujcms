package com.ujcms.cms.ext.domain;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.User;
import org.springframework.lang.Nullable;

import java.io.Serializable;

/**
 * 文章统计 实体类
 *
 * @author PONY
 */
public class ArticleStat implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 总录入数
     */
    private Integer total = 0;
    /**
     * 已发布数
     */
    private Integer published = 0;
    /**
     * 未发布数
     */
    private Integer unpublished = 0;

    /**
     * 用户ID
     */
    @Nullable
    private Long userId;
    /**
     * 栏目ID
     */
    @Nullable
    private Long channelId;
    /**
     * 组织ID
     */
    @Nullable
    private Long orgId;

    /**
     * 用户
     */
    @Nullable
    private User user;
    /**
     * 栏目
     */
    @Nullable
    private Channel channel;
    /**
     * 组织
     */
    @Nullable
    private Org org;

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getPublished() {
        return published;
    }

    public void setPublished(Integer published) {
        this.published = published;
    }

    public Integer getUnpublished() {
        return unpublished;
    }

    public void setUnpublished(Integer unpublished) {
        this.unpublished = unpublished;
    }

    @Nullable
    public Long getUserId() {
        return userId;
    }

    public void setUserId(@Nullable Long userId) {
        this.userId = userId;
    }

    @Nullable
    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(@Nullable Long channelId) {
        this.channelId = channelId;
    }

    @Nullable
    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(@Nullable Long orgId) {
        this.orgId = orgId;
    }

    @Nullable
    public User getUser() {
        return user;
    }

    public void setUser(@Nullable User user) {
        this.user = user;
    }

    @Nullable
    public Channel getChannel() {
        return channel;
    }

    public void setChannel(@Nullable Channel channel) {
        this.channel = channel;
    }

    @Nullable
    public Org getOrg() {
        return org;
    }

    public void setOrg(@Nullable Org org) {
        this.org = org;
    }
}
