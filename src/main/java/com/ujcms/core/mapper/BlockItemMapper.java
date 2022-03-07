package com.ujcms.core.mapper;

import com.ujcms.core.domain.BlockItem;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 区块项 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface BlockItemMapper {
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
    int delete(Integer id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    BlockItem select(Integer id);

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
    int countByBlockIdAndArticleId(@Param("blockId") Integer blockId, @Param("articleId") Integer articleId);

    /**
     * 根据 文章ID 获取列表
     *
     * @param articleId 文章ID
     * @return 数据列表
     */
    List<BlockItem> listByArticleId(Integer articleId);
}