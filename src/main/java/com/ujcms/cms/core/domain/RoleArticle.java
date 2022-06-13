package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.RoleArticleBase;

import java.io.Serializable;

/**
 * 角色文章权限 实体类
 *
 * @author PONY
 */
public class RoleArticle extends RoleArticleBase implements Serializable {
    public RoleArticle() {
    }

    public RoleArticle(Integer roleId, Integer channelId, Integer siteId) {
        setRoleId(roleId);
        setChannelId(channelId);
        setSiteId(siteId);
    }
}