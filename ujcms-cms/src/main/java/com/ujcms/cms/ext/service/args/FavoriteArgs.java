package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 收藏 查询参数
 *
 * @author PONY
 */
public class FavoriteArgs extends BaseQueryArgs {
    public FavoriteArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public FavoriteArgs articleId(@Nullable Long articleId) {
        if (articleId != null) {
            queryMap.put("EQ_articleId_Long", articleId);
        }
        return this;
    }

    public FavoriteArgs userId(@Nullable Long userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Long", userId);
        }
        return this;
    }

    public FavoriteArgs groupId(@Nullable Long groupId) {
        if (groupId != null) {
            queryMap.put("EQ_groupId_Long", groupId);
        }
        return this;
    }

    public FavoriteArgs groupIdIsNull() {
        queryMap.put("IsNull_groupId", null);
        return this;
    }

    public static FavoriteArgs of() {
        return of(new HashMap<>(16));
    }

    public static FavoriteArgs of(Map<String, Object> queryMap) {
        return new FavoriteArgs(queryMap);
    }

    private FavoriteArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
