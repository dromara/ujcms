package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.ArticleBuffer;
import com.ujcms.cms.core.mapper.ArticleBufferMapper;
import org.jspecify.annotations.Nullable;
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

    /**
     * 顶次数加一，返回加一之后的次数。自增在数据库里完成：并发的两次点击各算一次，也不会盖掉期间刷入的浏览量
     *
     * @return 文章不存在时返回 0
     */
    @Transactional(rollbackFor = Exception.class)
    public int incrementUps(Long id) {
        mapper.incrementUps(id);
        ArticleBuffer buffer = mapper.select(id);
        return buffer != null ? buffer.getUps() : 0;
    }

    /**
     * 踩次数加一，返回加一之后的次数
     *
     * @return 文章不存在时返回 0
     */
    @Transactional(rollbackFor = Exception.class)
    public int incrementDowns(Long id) {
        mapper.incrementDowns(id);
        ArticleBuffer buffer = mapper.select(id);
        return buffer != null ? buffer.getDowns() : 0;
    }

    /**
     * 下载次数加一，返回加一之后的次数
     *
     * @return 文章不存在时返回 0
     */
    @Transactional(rollbackFor = Exception.class)
    public int incrementDownloads(Long id) {
        mapper.incrementDownloads(id);
        ArticleBuffer buffer = mapper.select(id);
        return buffer != null ? buffer.getDownloads() : 0;
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