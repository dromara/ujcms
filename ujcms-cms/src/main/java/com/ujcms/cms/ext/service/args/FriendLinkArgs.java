package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * FriendLink 查询参数
 *
 * @author Generator
 */
public class FriendLinkArgs extends BaseQueryArgs {
    private FriendLinkArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public static FriendLinkArgs of() {
        return of(new HashMap<>(16));
    }

    public static FriendLinkArgs of(Map<String, Object> queryMap) {
        return new FriendLinkArgs(queryMap);
    }

    public FriendLinkArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public FriendLinkArgs typeId(@Nullable Long typeId) {
        if (typeId != null) {
            queryMap.put("EQ_typeId_Long", typeId);
        }
        return this;
    }

    public FriendLinkArgs enabled(@Nullable Boolean enabled) {
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }
        return this;
    }
}
