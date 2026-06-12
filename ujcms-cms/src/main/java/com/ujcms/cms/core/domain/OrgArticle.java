package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.generated.GeneratedOrgArticle;

/**
 * 组织文章权限 实体类
 *
 * @author PONY
 */
public class OrgArticle extends GeneratedOrgArticle {

    public OrgArticle() {
    }

    public OrgArticle(Long orgId, Long channelId, Long siteId) {
        setOrgId(orgId);
        setChannelId(channelId);
        setSiteId(siteId);
    }
}