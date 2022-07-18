package com.ujcms.util.sms;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

/**
 * 短信验证码缓存
 *
 * @author PONY
 */
@CacheConfig(cacheNames = SmsTokenCache.CACHE_NAME)
public class SmsTokenCache {
    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "smsToken";

    @Cacheable
    public boolean isAttempted(String sign) {
        return false;
    }

    @CachePut(key = "#sign")
    public boolean setAttempted(String sign) {
        return true;
    }
}
