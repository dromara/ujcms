package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.OrgPermBase;

import java.io.Serializable;

/**
 * 组织权限 实体类
 *
 * @author PONY
 */
public class OrgPerm extends OrgPermBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public OrgPerm() {
    }

    public OrgPerm(Long orgId, Long permOrgId) {
        setOrgId(orgId);
        setPermOrgId(permOrgId);
    }
}