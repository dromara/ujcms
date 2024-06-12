package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户查询参数
 *
 * @author PONY
 */
public class UserArgs extends BaseQueryArgs {
    @Nullable
    private Long orgId;

    public UserArgs orgId(@Nullable Long orgId) {
        if (orgId != null) {
            this.orgId = orgId;
        }
        return this;
    }

    public static UserArgs of() {
        return of(new HashMap<>(16));
    }

    public static UserArgs of(Map<String, Object> queryMap) {
        return new UserArgs(queryMap);
    }

    private UserArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    @Nullable
    public Long getOrgId() {
        return orgId;
    }
}
