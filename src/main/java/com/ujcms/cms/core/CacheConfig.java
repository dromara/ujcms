package com.ujcms.cms.core;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ujcms.util.captcha.CaptchaCache;
import com.ujcms.util.captcha.CaptchaProperties;
import com.ujcms.util.captcha.IpLoginCache;
import com.ujcms.util.sms.IpSmsCache;
import com.ujcms.util.sms.SmsTokenCache;
import com.ujcms.util.sms.SmsTokenProperties;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 缓存 配置
 *
 * @author PONY
 */
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    @ConditionalOnProperty(prefix = "spring.cache", name = "type", havingValue = "caffeine", matchIfMissing = true)
    public CacheManagerCustomizer<CaffeineCacheManager> caffeineCacheManagerCustomizer(
            CaptchaProperties captchaProps, SmsTokenProperties smsTokenProperties) {
        return (cacheManager) -> {
            // 验证码尝试次数缓存
            cacheManager.registerCustomCache(CaptchaCache.CACHE_NAME, Caffeine.newBuilder()
                    .expireAfterWrite(captchaProps.getExpires(), TimeUnit.MINUTES)
                    .maximumSize(captchaProps.getMaximumSize()).build());
            // IP登录尝试次数缓存
            cacheManager.registerCustomCache(IpLoginCache.CACHE_NAME, Caffeine.newBuilder()
                    .expireAfterWrite(IpLoginCache.EXPIRES, TimeUnit.MINUTES)
                    .maximumSize(IpLoginCache.MAXIMUM_SIZE).build());
            // 短信尝试次数缓存
            cacheManager.registerCustomCache(SmsTokenCache.CACHE_NAME, Caffeine.newBuilder()
                    .expireAfterWrite(smsTokenProperties.getExpires(), TimeUnit.MINUTES)
                    .maximumSize(smsTokenProperties.getMaximumSize()).build());
            // IP短信发送次数缓存
            cacheManager.registerCustomCache(IpSmsCache.CACHE_NAME, Caffeine.newBuilder()
                    .expireAfterWrite(IpSmsCache.EXPIRES, TimeUnit.MINUTES)
                    .maximumSize(IpSmsCache.MAXIMUM_SIZE).build());
        };
    }
}
