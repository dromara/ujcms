package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.RoleArticleBase;

/**
 * 角色文章权限实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class RoleArticle extends RoleArticleBase {

    public RoleArticle() {
    }

    public RoleArticle(Long roleId, Long channelId, Long siteId) {
        setRoleId(roleId);
        setChannelId(channelId);
        setSiteId(siteId);
    }
}