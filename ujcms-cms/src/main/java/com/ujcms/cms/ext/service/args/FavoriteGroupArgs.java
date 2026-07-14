package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 收藏夹 查询参数
 *
 * @author PONY
 */
public class FavoriteGroupArgs extends BaseQueryArgs {
    public FavoriteGroupArgs userId(@Nullable Long userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Long", userId);
        }
        return this;
    }

    public static FavoriteGroupArgs of() {
        return of(new HashMap<>(16));
    }

    public static FavoriteGroupArgs of(Map<String, Object> queryMap) {
        return new FavoriteGroupArgs(queryMap);
    }

    private FavoriteGroupArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
