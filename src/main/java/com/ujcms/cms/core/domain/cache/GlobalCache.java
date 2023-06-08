package com.ujcms.cms.core.domain.cache;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.ibatis.cache.Cache;
import org.springframework.lang.Nullable;

import java.io.Serializable;

import static com.ujcms.commons.db.MyBatis.deserialize;

/**
 * @author PONY
 */
public class GlobalCache implements Cache {
    private final String id;

    public GlobalCache(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public void putObject(Object key, Object value) {
        getSpringCache().putObject(key, SerializationUtils.serialize((Serializable) value));
    }

    @Override
    @Nullable
    public Object getObject(Object key) {
        Object value = getSpringCache().getObject(key);
        if (value == null) {
            return null;
        }
        return deserialize((byte[]) value);
    }

    @Override
    @Nullable
    public Object removeObject(Object key) {
        return getSpringCache().removeObject(key);
    }

    @Override
    public void clear() {
        getSpringCache().clear();
    }

    @Override
    public int getSize() {
        return GlobalSpringCache.MAXIMUM_SIZE;
    }

    private GlobalSpringCache getSpringCache() {
        return GlobalSpringCache.me();
    }
}
