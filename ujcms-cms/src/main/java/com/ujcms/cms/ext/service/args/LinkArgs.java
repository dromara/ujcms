package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Link 查询参数
 *
 * @author Generator
 */
public class LinkArgs extends BaseQueryArgs {
    private LinkArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public static LinkArgs of() {
        return of(new HashMap<>(16));
    }

    public static LinkArgs of(Map<String, Object> queryMap) {
        return new LinkArgs(queryMap);
    }

    public LinkArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public LinkArgs groupId(@Nullable Long groupId) {
        if (groupId != null) {
            queryMap.put("EQ_groupId_Long", groupId);
        }
        return this;
    }

    public LinkArgs enabled(@Nullable Boolean enabled) {
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }
        return this;
    }
}
