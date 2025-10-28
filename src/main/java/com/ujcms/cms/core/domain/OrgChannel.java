package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.OrgChannelBase;

/**
 * 组织栏目权限 实体类
 *
 * @author PONY
 */
public class OrgChannel extends OrgChannelBase {

    public OrgChannel() {
    }

    public OrgChannel(Long orgId, Long channelId, Long siteId) {
        setOrgId(orgId);
        setChannelId(channelId);
        setSiteId(siteId);
    }
}