package com.ujcms.core.service;

import com.ujcms.core.domain.Seq;
import com.ujcms.core.mapper.SeqMapper;
import com.ujcms.core.support.Props;
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
    private SeqMapper mapper;
    private Props props;

    public SeqService(SeqMapper mapper, Props props) {
        this.mapper = mapper;
        this.props = props;
    }

    /**
     * 获取下一个序列值
     *
     * @param name 序列名称
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int getNextVal(final String name) {
        return (int) getNextValLong(name);
    }

    /**
     * 获取下一个长整型序列值
     *
     * @param name 序列名称
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public long getNextValLong(final String name) {
        Seq seq;
        synchronized (seq = MAP.computeIfAbsent(name, Seq::new)) {
            if (!seq.hasNextVal()) {
                insertOrUpdate(seq);
            }
            return seq.fetchNextVal();
        }
    }

    /**
     * 插入或更新序列
     */
    protected void insertOrUpdate(final Seq seq) {
        Seq entity = mapper.selectForUpdate(seq.getName());
        if (entity != null) {
            entity.copyToCache(seq, props.getSequenceCacheSize());
            mapper.update(entity);
        } else {
            entity = new Seq(seq.getName());
            entity.copyToCache(seq, props.getSequenceCacheSize());
            mapper.insert(entity);
        }
    }

    private static final Map<String, Seq> MAP = new ConcurrentHashMap<>();
}