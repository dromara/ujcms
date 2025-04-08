package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.CollectionField;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 采集字段 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface CollectionFieldMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(CollectionField bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(CollectionField bean);

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
    CollectionField select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<CollectionField> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据采集ID获取列表
     *
     * @param collectionId 采集ID
     * @return 数据列表
     */
    List<CollectionField> listByCollectionId(@Param("collectionId")Long collectionId);

    /**
     * 根据采集ID删除列表
     *
     * @param collectionId 采集ID
     * @return 被删除的数据条数
     */
    int deleteByCollectionId(@Param("collectionId")Long collectionId, @Param("ids") List<Long> ids);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(@Param("siteId") Long siteId);
}