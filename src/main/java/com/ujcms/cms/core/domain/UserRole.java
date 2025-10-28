package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.UserRoleBase;

/**
 * 用户角色关系实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class UserRole extends UserRoleBase {

    public UserRole(Long userId, Long roleId) {
        setUserId(userId);
        setRoleId(roleId);
    }
}