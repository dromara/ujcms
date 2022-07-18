package com.ujcms.util.captcha;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

/**
 * 验证码缓存
 *
 * @author PONY
 */
@CacheConfig(cacheNames = CaptchaCache.CACHE_NAME)
public class CaptchaCache {
    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "captcha";

    @Cacheable
    public int getAttempts(String sign) {
        return 0;
    }

    @CachePut(key = "#sign")
    public int updateAttempts(String sign, int count) {
        return count;
    }
}
