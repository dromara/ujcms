package com.ujcms.common.social;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

/**
 * 好友申请计数缓存。按申请人用户ID计数，用于限制每日申请数量。
 * <p>
 * 与短信计数缓存同一模式：单节点、重启清零、滚动窗口，尽力而为的防滥用手段。
 *
 * @author PONY
 */
@CacheConfig(cacheNames = FriendRequestCache.CACHE_NAME)
public class FriendRequestCache {
    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "friendRequest";
    /**
     * 缓存过期时间。默认1440分钟（一天）。
     */
    public static final int EXPIRES = 1440;
    /**
     * 最大缓存数量。默认 100,000
     */
    public static final int MAXIMUM_SIZE = 100_000;

    @Cacheable
    public int getCount(String userId) {
        return 0;
    }

    @CachePut(key = "#userId")
    public int updateCount(String userId, int count) {
        return count;
    }
}
