package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.ArticleBuffer;
import com.ujcms.cms.core.domain.base.ArticleBufferBase;
import com.ujcms.cms.core.mapper.ArticleBufferMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 文章缓冲 Service
 *
 * @author PONY
 */
@Service
public class ArticleBufferService {
    private final ArticleBufferMapper mapper;
    private final SeqService seqService;

    public ArticleBufferService(ArticleBufferMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(ArticleBuffer bean) {
        bean.setId(seqService.getNextVal(ArticleBufferBase.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ArticleBuffer bean) {
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
    public ArticleBuffer select(Integer id) {
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