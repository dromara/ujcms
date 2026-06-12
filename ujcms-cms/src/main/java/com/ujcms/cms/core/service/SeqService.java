package com.ujcms.cms.core.service;

import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.domain.Seq;
import com.ujcms.cms.core.mapper.SeqMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列 Service
 *
 * @author PONY
 */
@Service
public class SeqService {
    private final SeqMapper mapper;
    private final Props props;

    public SeqService(SeqMapper mapper, Props props) {
        this.mapper = mapper;
        this.props = props;
    }

    /**
     * 获取下一个序列值
     *
     * @param name 序列名称。一般为表名。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int getNextVal(final String name) {
        return (int) getNextLongVal(name, props.getSequenceCacheSize());
    }

    /**
     * 获取下一个序列值
     *
     * @param name 序列名称。一般为表名。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int getNextVal(final String name, final int cacheSize) {
        return (int) getNextLongVal(name, cacheSize);
    }

    /**
     * 获取下一个长整型序列值
     *
     * @param name 序列名称。一般为表名。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public long getNextLongVal(final String name) {
        return getNextLongVal(name, props.getSequenceCacheSize());
    }

    /**
     * 获取下一个长整型序列值
     *
     * @param name 序列名称。一般为表名。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public long getNextLongVal(final String name, final int cacheSize) {
        Seq seq = SEQ_MAP.computeIfAbsent(name, Seq::new);
        return seq.getNextLongVal(cacheSize, this::insertOrUpdate);
    }

    /**
     * 插入或更新序列
     */
    protected void insertOrUpdate(final Seq seq, final int cacheSize) {
        Seq entity = mapper.selectForUpdate(seq.getName());
        if (entity != null) {
            entity.copyToCache(seq, cacheSize);
            mapper.update(entity);
        } else {
            entity = new Seq(seq.getName());
            entity.copyToCache(seq, cacheSize);
            mapper.insert(entity);
        }
    }

    private static final Map<String, Seq> SEQ_MAP = new ConcurrentHashMap<>();
}