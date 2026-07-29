package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 浏览历史 查询参数
 *
 * @author PONY
 */
public class ViewHistoryArgs extends BaseQueryArgs {
    public ViewHistoryArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public ViewHistoryArgs articleId(@Nullable Long articleId) {
        if (articleId != null) {
            queryMap.put("EQ_articleId_Long", articleId);
        }
        return this;
    }

    public ViewHistoryArgs userId(@Nullable Long userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Long", userId);
        }
        return this;
    }

    public static ViewHistoryArgs of() {
        return of(new HashMap<>(16));
    }

    public static ViewHistoryArgs of(Map<String, Object> queryMap) {
        return new ViewHistoryArgs(queryMap);
    }

    private ViewHistoryArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
