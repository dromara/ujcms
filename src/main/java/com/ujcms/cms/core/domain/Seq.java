package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.SeqBase;

import java.io.Serializable;

/**
 * 序列实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Seq extends SeqBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public Seq() {
    }

    public Seq(String name) {
        setName(name);
    }

    /**
     * 如果数据库中 {@code cacheSize > 0}，则使用数据库中的cacheSize，否则使用程序中设定的cacheSize
     */
    public void copyToCache(Seq seq, int defaultCacheSize) {
        seq.setNextVal(getNextVal());
        long cachedVal = getNextVal() + (getCacheSize() > 0 ? getCacheSize() : defaultCacheSize);
        seq.setCachedVal(cachedVal);
        setNextVal(cachedVal);
    }

    public boolean hasNextVal() {
        return getNextVal() < getCachedVal();
    }

    public long fetchNextVal() {
        long val = getNextVal();
        setNextVal(val + 1);
        return val;
    }

    private long cachedVal = 0;

    public long getCachedVal() {
        return cachedVal;
    }

    public void setCachedVal(long cachedVal) {
        this.cachedVal = cachedVal;
    }
}