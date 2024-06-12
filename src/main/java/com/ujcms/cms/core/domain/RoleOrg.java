package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.RoleOrgBase;

import java.io.Serializable;

/**
 * 角色组织权限 实体类
 *
 * @author PONY
 */
public class RoleOrg extends RoleOrgBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public RoleOrg() {
    }

    public RoleOrg(Long roleId, Long orgId, Long siteId) {
        setRoleId(roleId);
        setOrgId(orgId);
        setSiteId(siteId);
    }
}