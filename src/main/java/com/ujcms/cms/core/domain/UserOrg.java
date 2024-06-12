package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.UserOrgBase;

import java.io.Serializable;

/**
 * 用户扩展组织关联实体类
 *
 * @author PONY
 */
public class UserOrg extends UserOrgBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public UserOrg() {
    }

    public UserOrg(Long userId, Long orgId) {
        setUserId(userId);
        setOrgId(orgId);
    }

    public UserOrg(Long userId, Long orgId, Integer order) {
        setUserId(userId);
        setOrgId(orgId);
        setOrder(order);
    }
}