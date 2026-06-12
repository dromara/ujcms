package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.generated.GeneratedOrgPerm;

/**
 * 组织权限 实体类
 *
 * @author PONY
 */
public class OrgPerm extends GeneratedOrgPerm {

    public OrgPerm() {
    }

    public OrgPerm(Long orgId, Long permOrgId) {
        setOrgId(orgId);
        setPermOrgId(permOrgId);
    }
}