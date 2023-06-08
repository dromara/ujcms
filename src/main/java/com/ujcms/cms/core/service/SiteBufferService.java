package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.SiteBuffer;
import com.ujcms.cms.core.domain.base.SiteBufferBase;
import com.ujcms.cms.core.mapper.SiteBufferMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * 站点缓冲 Service
 *
 * @author PONY
 */
@Service
public class SiteBufferService {
    private final SiteBufferMapper mapper;
    private final SeqService seqService;

    public SiteBufferService(SiteBufferMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(SiteBuffer bean) {
        bean.setId(seqService.getNextVal(SiteBufferBase.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SiteBuffer bean) {
        mapper.update(bean);
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
    public SiteBuffer select(Integer id) {
        return mapper.select(id);
    }

    /**
     * 更新浏览统计
     *
     * @return 更新条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateStat() {
        OffsetDateTime yesterday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).minusDays(1).toOffsetDateTime();
        return mapper.updateStat(yesterday);
    }
}