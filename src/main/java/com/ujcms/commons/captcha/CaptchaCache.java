package com.ujcms.commons.captcha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(CaptchaCache.class);

    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "captcha";

    @Cacheable
    public int getAttempts(String sign) {
        logger.debug("getAttempts({})", sign);
        return 0;
    }

    @CachePut(key = "#sign")
    public int updateAttempts(String sign, int count) {
        return count;
    }
}
