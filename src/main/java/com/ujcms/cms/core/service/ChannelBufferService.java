package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.ChannelBuffer;
import com.ujcms.cms.core.mapper.ChannelBufferMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 栏目缓冲 Service
 *
 * @author PONY
 */
@Service
public class ChannelBufferService {
    private final ChannelBufferMapper mapper;
    private final SeqService seqService;

    public ChannelBufferService(ChannelBufferMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(ChannelBuffer bean) {
        bean.setId(seqService.getNextVal(ChannelBuffer.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ChannelBuffer bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public long updateViews(Integer id, int viewsToPlus) {
        ChannelBuffer buffer = mapper.select(id);
        if (buffer == null) {
            return 0;
        }
        mapper.updateViews(id, viewsToPlus);
        return buffer.getViews() + viewsToPlus;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public ChannelBuffer select(Integer id) {
        return mapper.select(id);
    }
}