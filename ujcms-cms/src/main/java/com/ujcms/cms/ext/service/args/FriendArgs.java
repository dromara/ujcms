package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 好友 查询参数
 *
 * @author PONY
 */
public class FriendArgs extends BaseQueryArgs {
    public FriendArgs userId(@Nullable Long userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Long", userId);
        }
        return this;
    }

    public FriendArgs friendId(@Nullable Long friendId) {
        if (friendId != null) {
            queryMap.put("EQ_friendId_Long", friendId);
        }
        return this;
    }

    public FriendArgs status(@Nullable Short status) {
        if (status != null) {
            queryMap.put("EQ_status_Short", status);
        }
        return this;
    }

    public static FriendArgs of() {
        return of(new HashMap<>(16));
    }

    public static FriendArgs of(Map<String, Object> queryMap) {
        return new FriendArgs(queryMap);
    }

    private FriendArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
