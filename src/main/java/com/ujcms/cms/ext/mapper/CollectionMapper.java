package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Collection;
import com.ujcms.commons.db.order.OrderEntityMapper;
import com.ujcms.commons.query.QueryInfo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 采集 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface CollectionMapper extends OrderEntityMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Collection bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Collection bean);

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
    Collection select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Collection> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据 栏目ID 查询文章数量
     *
     * @param channelId 栏目ID
     * @return 文章数量
     */
    int existsByChannelId(@Param("channelId") Long channelId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}