package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * FriendLinkType 查询参数
 *
 * @author Generator
 */
public class FriendLinkTypeArgs extends BaseQueryArgs {
    private FriendLinkTypeArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public static FriendLinkTypeArgs of() {
        return of(new HashMap<>(16));
    }

    public static FriendLinkTypeArgs of(Map<String, Object> queryMap) {
        return new FriendLinkTypeArgs(queryMap);
    }

    public FriendLinkTypeArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }
}
