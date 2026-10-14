package com.ujcms.cms.core.component;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 匿名互动（投票/调查/文章顶踩）IP 限流缓存。
 * <p>
 * 按“IP + 时间窗口序号”分桶计数：每个窗口对应独立的缓存键，窗口结束后该键自然过期、计数归零，
 * 因此是固定窗口限流，不会像“仅按 IP + expireAfterWrite”那样在持续访问下累积到永久封禁。
 *
 * @author PONY
 */
@Component
@CacheConfig(cacheNames = IpInteractionCache.CACHE_NAME)
public class IpInteractionCache {
    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "ipInteraction";
    /**
     * 限流时间窗口（秒）。计数按 IP + 窗口序号分桶。
     */
    public static final int WINDOW_SECONDS = 60;
    /**
     * 缓存过期时间（分钟）。需大于时间窗口，确保桶在其窗口内存活。
     */
    public static final int EXPIRES_MINUTE = 5;
    /**
     * 最大缓存数量
     */
    public static final int MAXIMUM_SIZE = 100_000;

    @Cacheable
    public int getCount(String key) {
        return 0;
    }

    @CachePut(key = "#key")
    public int updateCount(String key, int count) {
        return count;
    }
}
