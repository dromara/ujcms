package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.ChannelBuffer;
import com.ujcms.cms.core.mapper.ChannelBufferMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 栏目缓冲 Service
 *
 * @author PONY
 */
@Service
public class ChannelBufferService {
    private final ChannelBufferMapper mapper;

    public ChannelBufferService(ChannelBufferMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ChannelBuffer bean) {
        mapper.update(bean);
    }

    @Nullable
    public ChannelBuffer select(Integer id) {
        return mapper.select(id);
    }
}