package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.ViewHistory;
import com.ujcms.common.query.QueryInfo;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * @author PONY
 */
@Mapper
@Repository
public interface ViewHistoryMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ViewHistory bean);

    /**
     * 删除数据
     *
     * @param id 主键ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    ViewHistory select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<ViewHistory> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据文章ID和用户ID获取数据
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    ViewHistory findByArticleIdAndUserId(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 更新最后浏览日期。浏览历史行除此之外不可变
     *
     * @param id      主键ID
     * @param updated 最后浏览日期
     * @return 更新条数
     */
    int touch(@Param("id") Long id, @Param("updated") OffsetDateTime updated);

    /**
     * 根据文章ID删除数据
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(Long articleId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 根据用户ID删除数据。清空浏览记录及删除用户时使用
     *
     * @param userId 用户ID
     * @return 删除条数
     */
    int deleteByUserId(Long userId);

    /**
     * 删除用户过旧的浏览历史（裁剪至每用户上限）
     *
     * @param userId 用户ID
     * @param before 截止日期。删除最后浏览日期早于该值的数据
     * @return 删除条数
     */
    int deleteStale(@Param("userId") Long userId, @Param("before") OffsetDateTime before);
}
