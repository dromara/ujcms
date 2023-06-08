package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ArticleBuffer;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ArticleBuffer bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(ArticleBuffer bean);

    /**
     * 删除数据
     *
     * @param id 主键ID
     * @return 删除条数
     */
    int delete(Integer id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    ArticleBuffer select(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<ArticleBuffer> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

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

    /**
     * 根据栏目ID删除数据
     *
     * @param channelId 栏目ID
     * @return 被删除的数据条数
     */
    int deleteByChannelId(Integer channelId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Integer siteId);
}