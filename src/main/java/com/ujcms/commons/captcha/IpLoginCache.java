package com.ujcms.commons.captcha;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

/**
 * IP登录缓存
 *
 * @author PONY
 */
@CacheConfig(cacheNames = IpLoginCache.CACHE_NAME)
public class IpLoginCache {
    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "ipLogin";
    /**
     * 缓存过期时间。默认120分钟。
     */
    public static final int EXPIRES = 120;
    /**
     * 最大缓存数量。默认 100,000
     */
    public static final int MAXIMUM_SIZE = 100_000;

    @Cacheable
    public int getAttempts(String ip) {
        return 0;
    }

    @CachePut(key = "#ip")
    public int updateAttempts(String ip, int count) {
        return count;
    }

    @CacheEvict
    public void removeAttempts(String ip) {
    }
}
