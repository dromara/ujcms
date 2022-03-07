package com.ujcms.core.domain;

import com.ujcms.core.domain.base.ChannelGroupBase;

import java.io.Serializable;

/**
 * 栏目与用户组关联 实体类
 *
 * @author PONY
 */
public class ChannelGroup extends ChannelGroupBase implements Serializable {
    public ChannelGroup() {
    }

    public ChannelGroup(Integer channelId, Integer groupId) {
        setChannelId(channelId);
        setGroupId(groupId);
    }
}