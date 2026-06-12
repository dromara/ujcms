package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ArticleBuffer;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章缓冲 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ArticleBufferMapper {
    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(ArticleBuffer bean);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    ArticleBuffer select(Long id);

    /**
     * 重置日浏览量
     *
     * @return 更新条数
     */
    int resetDayViews();

    /**
     * 重置周浏览量
     *
     * @return 更新条数
     */
    int resetWeekViews();

    /**
     * 重置月浏览量
     *
     * @return 更新条数
     */
    int resetMonthViews();

    /**
     * 重置季浏览量
     *
     * @return 更新条数
     */
    int resetQuarterViews();

    /**
     * 重置年浏览量
     *
     * @return 更新条数
     */
    int resetYearViews();

    /**
     * 批量更新浏览次数
     *
     * @param list 待更新列表
     * @return 更新条数
     */
    int updateBatch(List<ArticleBuffer> list);
}