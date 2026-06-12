package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.ArticleBuffer;
import com.ujcms.cms.core.mapper.ArticleBufferMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文章缓冲 Service
 *
 * @author PONY
 */
@Service
public class ArticleBufferService {
    private final ArticleBufferMapper mapper;

    public ArticleBufferService(ArticleBufferMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ArticleBuffer bean) {
        mapper.update(bean);
    }

    @Nullable
    public ArticleBuffer select(Long id) {
        return mapper.select(id);
    }

    /**
     * 重置日浏览量
     *
     * @return 更新条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int resetDayViews() {
        return mapper.resetDayViews();
    }

    /**
     * 重置周浏览量
     *
     * @return 更新条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int resetWeekViews() {
        return mapper.resetWeekViews();
    }

    /**
     * 重置月浏览量
     *
     * @return 更新条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int resetMonthViews() {
        return mapper.resetMonthViews();
    }

    /**
     * 重置季浏览量
     *
     * @return 更新条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int resetQuarterViews() {
        return mapper.resetQuarterViews();
    }

    /**
     * 重置年浏览量
     *
     * @return 更新条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int resetYearViews() {
        return mapper.resetYearViews();
    }
}