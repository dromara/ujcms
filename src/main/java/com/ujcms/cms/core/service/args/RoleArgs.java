package com.ujcms.cms.core.service.args;

import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 角色查询参数
 *
 * @author PONY
 */
public class RoleArgs extends BaseQueryArgs {
    public RoleArgs scopeSiteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_1_siteId_Long", siteId);
            queryMap.put("EQ_1_scope_Short", String.valueOf(EntityConstants.SCOPE_GLOBAL));
        }
        return this;
    }

    public RoleArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public RoleArgs allArticlePermission(boolean allArticlePermission) {
        queryMap.put("EQ_allArticlePermission_Boolean", allArticlePermission);
        return this;
    }

    public RoleArgs allChannelPermission(boolean allChannelPermission) {
        queryMap.put("EQ_allChannelPermission_Boolean", allChannelPermission);
        return this;
    }

    public static RoleArgs of() {
        return of(new HashMap<>(16));
    }

    public static RoleArgs of(Map<String, Object> queryMap) {
        return new RoleArgs(queryMap);
    }

    private RoleArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
