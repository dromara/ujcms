package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.OrgChannelBase;

import java.io.Serializable;

/**
 * 组织栏目权限 实体类
 *
 * @author PONY
 */
public class OrgChannel extends OrgChannelBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public OrgChannel() {
    }

    public OrgChannel(Long orgId, Long channelId, Long siteId) {
        setOrgId(orgId);
        setChannelId(channelId);
        setSiteId(siteId);
    }
}