package com.ujcms.cms.core.domain.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.Nullable;

import java.util.Objects;


/**
 * @author PONY
 */
@CacheConfig(cacheNames = GroupSpringCache.CACHE_NAME)
public class GroupSpringCache implements ApplicationContextAware {
    private static final Logger logger = LoggerFactory.getLogger(GroupSpringCache.class);

    /**
     * 缓存名称
     */
    public static final String CACHE_NAME = "groupSpringCache";
    /**
     * 缓存过期时间。默认10分钟。
     */
    public static final int EXPIRES = 10;
    /**
     * 最大缓存数量。默认 4096
     */
    public static final int MAXIMUM_SIZE = 512;

    @Cacheable
    @Nullable
    public Object getObject(Object key) {
        logger.debug("GroupSpringCache getObject({})", key);
        return null;
    }

    @CachePut(key = "#key")
    public Object putObject(Object key, Object value) {
        logger.debug("GroupSpringCache putObject({}, {})", key, value);
        return value;
    }

    @CacheEvict
    @Nullable
    public Object removeObject(Object key) {
        logger.debug("GroupSpringCache removeObject({})", key);
        return null;
    }

    @CacheEvict(allEntries = true)
    public void clear() {
        logger.debug("GroupSpringCache clear()");
    }

    public static GroupSpringCache me() {
        Objects.requireNonNull(applicationContext, "applicationContext must not be null");
        return applicationContext.getBean(GroupSpringCache.class);
    }

    @Nullable
    private static ApplicationContext applicationContext;

    @SuppressWarnings("java:S2696")
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        GroupSpringCache.applicationContext = applicationContext;
    }
}
