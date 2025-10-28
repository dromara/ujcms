package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.RoleChannelBase;

/**
 * 角色栏目权限实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class RoleChannel extends RoleChannelBase {

    public RoleChannel() {
    }

    public RoleChannel(Long roleId, Long channelId, Long siteId) {
        setRoleId(roleId);
        setChannelId(channelId);
        setSiteId(siteId);
    }
}