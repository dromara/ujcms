package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.base.TagBase;

import java.io.Serializable;

/**
 * Tag 实体类
 *
 * @author PONY
 */
public class Tag extends TagBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public Tag() {
    }

    public Tag(Long siteId, Long userId, String name) {
        setSiteId(siteId);
        setUserId(userId);
        setName(name);
    }

    public Tag(Long siteId, Long userId, String name, String description) {
        this(siteId, userId, name);
        setDescription(description);
    }

    /**
     * 创建人
     */
    @JsonIncludeProperties({"id", "username", "nickname"})
    private User user = new User();
    /**
     * 站点
     */
    @JsonIncludeProperties({"id", "name"})
    private Site site = new Site();

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public static final String NOT_FOUND = "Tag not found. ID: ";
}