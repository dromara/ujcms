package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.OrgPermBase;

/**
 * 组织权限 实体类
 *
 * @author PONY
 */
public class OrgPerm extends OrgPermBase {

    public OrgPerm() {
    }

    public OrgPerm(Long orgId, Long permOrgId) {
        setOrgId(orgId);
        setPermOrgId(permOrgId);
    }
}