package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.generated.GeneratedOrgChannel;

/**
 * 组织栏目权限 实体类
 *
 * @author PONY
 */
public class OrgChannel extends GeneratedOrgChannel {

    public OrgChannel() {
    }

    public OrgChannel(Long orgId, Long channelId, Long siteId) {
        setOrgId(orgId);
        setChannelId(channelId);
        setSiteId(siteId);
    }
}