package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 关注 查询参数
 *
 * @author PONY
 */
public class FollowArgs extends BaseQueryArgs {
    public FollowArgs userId(@Nullable Long userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Long", userId);
        }
        return this;
    }

    public FollowArgs type(@Nullable Short type) {
        if (type != null) {
            queryMap.put("EQ_type_Short", type);
        }
        return this;
    }

    public FollowArgs articleId(@Nullable Long articleId) {
        if (articleId != null) {
            queryMap.put("EQ_articleId_Long", articleId);
        }
        return this;
    }

    public FollowArgs channelId(@Nullable Long channelId) {
        if (channelId != null) {
            queryMap.put("EQ_channelId_Long", channelId);
        }
        return this;
    }

    public FollowArgs toUserId(@Nullable Long toUserId) {
        if (toUserId != null) {
            queryMap.put("EQ_toUserId_Long", toUserId);
        }
        return this;
    }

    public FollowArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public static FollowArgs of() {
        return of(new HashMap<>(16));
    }

    public static FollowArgs of(Map<String, Object> queryMap) {
        return new FollowArgs(queryMap);
    }

    private FollowArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
