package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedGroupAccess;

/**
 * 用户组浏览权限实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class GroupAccess extends GeneratedGroupAccess {

    public GroupAccess() {
    }

    public GroupAccess(Long groupId, Long channelId, Long siteId) {
        setGroupId(groupId);
        setChannelId(channelId);
        setSiteId(siteId);
    }
}