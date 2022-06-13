package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.GroupAccessBase;

import java.io.Serializable;

/**
 * 用户组浏览权限 实体类
 *
 * @author PONY
 */
public class GroupAccess extends GroupAccessBase implements Serializable {
    public GroupAccess() {
    }

    public GroupAccess(Integer groupId, Integer channelId, Integer siteId) {
        setGroupId(groupId);
        setChannelId(channelId);
        setSiteId(siteId);
    }
}