package com.ujcms.common.sms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies that the per-IP send limit is a natural-day counter, matching the console's
 * "IP每日最大量" setting. The cache here never expires, so a reset can only come from the dated key —
 * which is the point: cache expiry restarts on every count and cannot deliver a daily reset on its own.
 */
class IpSmsCounterServiceTest {
    private static final String IP = "203.0.113.7";

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(IpSmsCache.CACHE_NAME);
        }

        @Bean
        IpSmsCache ipSmsCache() {
            return new IpSmsCache();
        }
    }

    /** A clock that can be moved forward. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Test
    void countsPerNaturalDayAndResetsAtMidnight() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            MutableClock clock = new MutableClock(Instant.parse("2026-09-18T08:00:00Z"));
            IpSmsCounterService service = new IpSmsCounterService(context.getBean(IpSmsCache.class), 64, clock);

            assertFalse(service.isExcessive(IP, 2));
            service.updateCount(IP);
            assertFalse(service.isExcessive(IP, 2));

            // late the same day: still the same counter
            clock.set(Instant.parse("2026-09-18T23:59:59Z"));
            service.updateCount(IP);
            assertTrue(service.isExcessive(IP, 2));
            // another IP is unaffected
            assertFalse(service.isExcessive("203.0.113.8", 2));

            // one second later it is a new day
            clock.set(Instant.parse("2026-09-19T00:00:00Z"));
            assertFalse(service.isExcessive(IP, 2));
        }
    }

    @Test
    void nonPositiveMaxMeansUnlimited() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            IpSmsCounterService service = new IpSmsCounterService(context.getBean(IpSmsCache.class));
            service.updateCount(IP);

            assertFalse(service.isExcessive(IP, 0));
        }
    }
}
