package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * LinkGroup 查询参数
 *
 * @author Generator
 */
public class LinkGroupArgs extends BaseQueryArgs {
    private boolean queryLinkCount = false;

    /**
     * 同时查询各链接组的链接数（{@code LinkGroup.linkCount}）。仅后台链接组列表需要，其余调用方不付子查询的代价
     */
    public LinkGroupArgs queryLinkCount() {
        queryLinkCount = true;
        return this;
    }

    public boolean isQueryLinkCount() {
        return queryLinkCount;
    }

    private LinkGroupArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public static LinkGroupArgs of() {
        return of(new HashMap<>(16));
    }

    public static LinkGroupArgs of(Map<String, Object> queryMap) {
        return new LinkGroupArgs(queryMap);
    }

    public LinkGroupArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public LinkGroupArgs slot(@Nullable String slot) {
        if (slot != null) {
            queryMap.put("EQ_slot", slot);
        }
        return this;
    }
}
