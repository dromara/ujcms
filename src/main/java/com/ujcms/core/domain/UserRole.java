package com.ujcms.core.domain;

import com.ujcms.core.domain.base.UserRoleBase;

import java.io.Serializable;

/**
 * 用户角色关系 实体类
 *
 * @author PONY
 */
public class UserRole extends UserRoleBase implements Serializable {
    public UserRole(Integer userId, Integer roleId) {
        setUserId(userId);
        setRoleId(roleId);
    }
}