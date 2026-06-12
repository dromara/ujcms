package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.generated.GeneratedRoleOrg;

/**
 * 角色组织权限 实体类
 *
 * @author PONY
 */
public class RoleOrg extends GeneratedRoleOrg {

    public RoleOrg() {
    }

    public RoleOrg(Long roleId, Long orgId, Long siteId) {
        setRoleId(roleId);
        setOrgId(orgId);
        setSiteId(siteId);
    }
}