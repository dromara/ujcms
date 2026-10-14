package com.ujcms.common.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exercises {@link SmsCooldownCache} through a real Spring cache proxy. The cooldown window itself is the
 * cache's expiry (configured where the cache is registered), so this covers the annotation contract only:
 * a receiver is cooling down only after {@code start}, and a negative lookup must not be cached —
 * otherwise the first check would pin the receiver to "not cooling down" and hide the later {@code start}.
 */
class SmsCooldownCacheTest {
    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(SmsCooldownCache.CACHE_NAME);
        }

        @Bean
        SmsCooldownCache smsCooldownCache() {
            return new SmsCooldownCache();
        }
    }

    @Test
    void receiverCoolsDownOnlyAfterStart() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            SmsCooldownCache cache = context.getBean(SmsCooldownCache.class);
            String key = SmsCooldownCache.key("13800138000");

            assertFalse(cache.isCoolingDown(key));
            // 未冷却的结果不进缓存
            assertNull(context.getBean(CacheManager.class).getCache(SmsCooldownCache.CACHE_NAME).get(key));

            cache.start(key);
            assertTrue(cache.isCoolingDown(key));
            assertFalse(cache.isCoolingDown(SmsCooldownCache.key("13900139000")));
        }
    }

    @Test
    void keyIgnoresCaseAndSurroundingWhitespace() {
        assertEquals("user@example.com", SmsCooldownCache.key(" User@Example.COM "));
    }
}
