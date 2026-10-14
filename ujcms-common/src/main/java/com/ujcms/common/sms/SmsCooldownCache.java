package com.ujcms.common.sms;

import java.util.Locale;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

/**
 * 验证码发送冷却缓存。
 * <p>
 * 同一接收者（手机号码或邮箱地址）发送成功后，在 {@link #EXPIRES_SECONDS} 秒内不可再次发送。冷却时间由缓存过期时间实现，
 * 缓存中只保存冷却中的接收者。
 *
 * @author PONY
 */
@CacheConfig(cacheNames = SmsCooldownCache.CACHE_NAME)
public class SmsCooldownCache {
    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "smsCooldown";
    /**
     * 冷却时间（缓存过期时间）。默认 60 秒。
     */
    public static final int EXPIRES_SECONDS = 60;
    /**
     * 最大缓存数量。默认 100,000
     */
    public static final int MAXIMUM_SIZE = 100_000;

    /**
     * 接收者是否处于冷却中
     *
     * @param key 由 {@link #key(String)} 生成的缓存键
     */
    @Cacheable(unless = "!#result")
    public boolean isCoolingDown(String key) {
        return false;
    }

    /**
     * 开始冷却。发送成功后调用
     *
     * @param key 由 {@link #key(String)} 生成的缓存键
     */
    @CachePut(key = "#key")
    public boolean start(String key) {
        return true;
    }

    /**
     * 生成缓存键。邮箱地址不区分大小写
     */
    public static String key(String receiver) {
        return receiver.trim().toLowerCase(Locale.ROOT);
    }
}
