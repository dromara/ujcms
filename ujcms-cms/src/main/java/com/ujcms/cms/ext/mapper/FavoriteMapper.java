package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Favorite;
import com.ujcms.common.query.QueryInfo;
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
public interface FavoriteMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Favorite bean);

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
    Favorite select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Favorite> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据文章ID和用户ID获取数据
     *
     * @param articleId 文章ID
     * @param userId    用户ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Favorite findByArticleIdAndUserId(@Param("articleId") Long articleId, @Param("userId") Long userId);

    /**
     * 更新收藏夹ID
     *
     * @param id      主键ID
     * @param groupId 收藏夹ID。{@code null} 为不属于任何收藏夹
     * @return 更新条数
     */
    int updateGroupId(@Param("id") Long id, @Nullable @Param("groupId") Long groupId);

    /**
     * 清除收藏夹ID。删除收藏夹时，将收藏夹内的收藏移至未分组
     *
     * @param groupId 收藏夹ID
     * @return 更新条数
     */
    int clearGroupId(Long groupId);

    /**
     * 清除用户所有收藏的收藏夹ID。删除用户时，使收藏与收藏夹的删除顺序无关
     *
     * @param userId 用户ID
     * @return 更新条数
     */
    int clearGroupIdByUserId(Long userId);

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
     * 增减文章收藏次数
     *
     * @param articleId 文章ID
     * @param amount    增减数量。负数为减少
     * @return 更新条数
     */
    int addArticleFavorites(@Param("articleId") Long articleId, @Param("amount") int amount);
}
