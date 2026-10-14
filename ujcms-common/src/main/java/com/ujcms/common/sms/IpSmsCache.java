package com.ujcms.common.sms;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

/**
 * IP短信缓存
 *
 * @author PONY
 */
@CacheConfig(cacheNames = IpSmsCache.CACHE_NAME)
public class IpSmsCache {
    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "ipSms";
    /**
     * 缓存过期时间（分钟）。默认 24 小时。只用于清理：缓存键包含日期（见 {@link IpSmsCounterService}），
     * 每日计数由键决定，前一天的键不再被访问，到期后自动清除。
     */
    public static final int EXPIRES = 1440;
    /**
     * 最大缓存数量。默认 100,000
     */
    public static final int MAXIMUM_SIZE = 100_000;

    @Cacheable
    public int getCount(String ip) {
        return 0;
    }

    @CachePut(key = "#ip")
    public int updateCount(String ip, int count) {
        return count;
    }

    @CacheEvict
    public void removeAttempts(String ip) {
    }
}
