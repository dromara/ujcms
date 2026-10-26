package com.ujcms.common.captcha;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

/**
 * IP 提交计数缓存：各互动场景（评论、留言……）同一 IP 在时间窗内被接受的提交次数，决定是否要求可见验证。
 * <p>
 * 缓存写后过期 {@value #EXPIRES} 分钟（Caffeine / Redis 配置均据此注册），计数随每次写入续期，
 * 即“最近一小时内持续提交”的 IP 一直处于计数中。
 *
 * @author PONY
 */
@CacheConfig(cacheNames = IpSubmitCache.CACHE_NAME)
public class IpSubmitCache {
    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "ipSubmit";
    /**
     * 缓存过期时间。单位：分钟
     */
    public static final int EXPIRES = 60;
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
