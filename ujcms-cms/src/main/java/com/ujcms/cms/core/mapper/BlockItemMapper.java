package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.common.db.order.OrderEntityMapper;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 区块项 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface BlockItemMapper extends OrderEntityMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(BlockItem bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(BlockItem bean);

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
    @Override
    BlockItem select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<BlockItem> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据 区块ID 和 文章ID 查询条数
     *
     * @param blockId   区块ID
     * @param articleId 文章ID
     * @return 数据条数
     */
    int countByBlockIdAndArticleId(@Param("blockId") Long blockId, @Param("articleId") Long articleId);

    /**
     * 根据 区块ID 统计条目数（移除演示数据的区块定义守卫，theme-seed-export E4a / D26）
     *
     * @param blockId 区块ID
     * @return 数据条数
     */
    int countByBlockId(Long blockId);

    /**
     * 根据 文章ID 获取列表
     *
     * @param articleId 文章ID
     * @return 数据列表
     */
    List<BlockItem> listByArticleId(Long articleId);

    /**
     * 一批文章的区块项，带上所属区块的 ID 与名称（文章列表用）。只含列表要显示的列，不加载关联的文章与站点
     *
     * @param articleIds 文章ID，不可为空
     */
    List<BlockItem> listByArticleIds(@Param("articleIds") Collection<Long> articleIds);

    /**
     * 根据 文章ID 删除数据
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(Long articleId);

    /**
     * 根据 区块ID 删除数据
     *
     * @param blockId 区块ID
     * @return 删除条数
     */
    int deleteByBlockId(Long blockId);

    /**
     * 根据区块ID和站点ID统计数量
     *
     * @param blockId   区块ID
     * @param notSiteId 非本站点ID
     * @return 数据条数
     */
    int existsByBlockId(@Param("blockId") Long blockId, @Param("notSiteId") Long notSiteId);

    /**
     * 根据栏目ID删除数据
     *
     * @param channelId 栏目ID
     * @return 被删除的数据条数
     */
    int deleteByChannelId(Long channelId);

    /**
     * 根据 站点ID 删除数据
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);
}