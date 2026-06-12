package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.SiteBuffer;
import com.ujcms.cms.core.mapper.SiteBufferMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 站点缓冲 Service
 *
 * @author PONY
 */
@Service
public class SiteBufferService {
    private final SiteBufferMapper mapper;

    public SiteBufferService(SiteBufferMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SiteBuffer bean) {
        mapper.update(bean);
    }

    @Nullable
    public SiteBuffer select(Long id) {
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