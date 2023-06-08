package com.ujcms.cms.core;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ujcms.cms.core.component.ContentStatCache;
import com.ujcms.cms.core.domain.cache.GlobalSpringCache;
import com.ujcms.cms.core.domain.cache.GroupSpringCache;
import com.ujcms.cms.core.domain.cache.SiteSpringCache;
import com.ujcms.commons.captcha.CaptchaCache;
import com.ujcms.commons.captcha.CaptchaProperties;
import com.ujcms.commons.captcha.IpLoginCache;
import com.ujcms.commons.sms.IpSmsCache;
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
            CaptchaProperties captchaProps) {
        return cacheManager -> {
            // 内容统计缓存
            cacheManager.registerCustomCache(ContentStatCache.CACHE_NAME, Caffeine.newBuilder()
                    .expireAfterWrite(ContentStatCache.EXPIRES_MINUTE, TimeUnit.MINUTES)
                    .maximumSize(ContentStatCache.MAXIMUM_SIZE).build());
            // 验证码尝试次数缓存
            cacheManager.registerCustomCache(CaptchaCache.CACHE_NAME, Caffeine.newBuilder()
                    .expireAfterWrite(captchaProps.getExpires(), TimeUnit.MINUTES)
                    .maximumSize(captchaProps.getMaximumSize()).build());
            // IP登录尝试次数缓存
            cacheManager.registerCustomCache(IpLoginCache.CACHE_NAME, Caffeine.newBuilder()
                    .expireAfterWrite(IpLoginCache.EXPIRES, TimeUnit.MINUTES)
                    .maximumSize(IpLoginCache.MAXIMUM_SIZE).build());
            // IP短信发送次数缓存
            cacheManager.registerCustomCache(IpSmsCache.CACHE_NAME, Caffeine.newBuilder()
                    .expireAfterWrite(IpSmsCache.EXPIRES, TimeUnit.MINUTES)
                    .maximumSize(IpSmsCache.MAXIMUM_SIZE).build());
            // MyBatis二级缓存：GlobalSpringCache
            cacheManager.registerCustomCache(GlobalSpringCache.CACHE_NAME, Caffeine.newBuilder()
                    .expireAfterWrite(GlobalSpringCache.EXPIRES, TimeUnit.MINUTES)
                    .maximumSize(GlobalSpringCache.MAXIMUM_SIZE).build());
            // MyBatis二级缓存：ConfigSpringCache
            cacheManager.registerCustomCache(SiteSpringCache.CACHE_NAME, Caffeine.newBuilder()
                    .expireAfterWrite(SiteSpringCache.EXPIRES, TimeUnit.MINUTES)
                    .maximumSize(SiteSpringCache.MAXIMUM_SIZE).build());
            // MyBatis二级缓存：GroupSpringCache
            cacheManager.registerCustomCache(GroupSpringCache.CACHE_NAME, Caffeine.newBuilder()
                    .expireAfterWrite(GroupSpringCache.EXPIRES, TimeUnit.MINUTES)
                    .maximumSize(GroupSpringCache.MAXIMUM_SIZE).build());
        };
    }

    @Bean
    public GlobalSpringCache globalSpringCache() {
        return new GlobalSpringCache();
    }

    @Bean
    public SiteSpringCache siteSpringCache() {
        return new SiteSpringCache();
    }

    @Bean
    public GroupSpringCache groupSpringCache() {
        return new GroupSpringCache();
    }
}
