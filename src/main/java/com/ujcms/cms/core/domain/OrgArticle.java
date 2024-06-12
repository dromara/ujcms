package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.OrgArticleBase;

import java.io.Serializable;

/**
 * 组织文章权限 实体类
 *
 * @author PONY
 */
public class OrgArticle extends OrgArticleBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public OrgArticle() {
    }

    public OrgArticle(Long orgId, Long channelId, Long siteId) {
        setOrgId(orgId);
        setChannelId(channelId);
        setSiteId(siteId);
    }
}