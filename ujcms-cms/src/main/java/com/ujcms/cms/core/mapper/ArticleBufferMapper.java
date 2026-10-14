package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ArticleBuffer;
import org.apache.ibatis.annotations.Mapper;
import org.jspecify.annotations.Nullable;
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
     * 顶次数加一（数据库内原子自增）
     *
     * @param id 文章ID
     * @return 更新条数
     */
    int incrementUps(Long id);

    /**
     * 踩次数加一（数据库内原子自增）
     *
     * @param id 文章ID
     * @return 更新条数
     */
    int incrementDowns(Long id);

    /**
     * 下载次数加一（数据库内原子自增）
     *
     * @param id 文章ID
     * @return 更新条数
     */
    int incrementDownloads(Long id);

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