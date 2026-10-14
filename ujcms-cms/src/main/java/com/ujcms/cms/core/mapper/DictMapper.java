package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Dict;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 字典 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface DictMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Dict bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Dict bean);

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
    Dict select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Dict> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据 字典类型ID 查询字典是否存在
     *
     * @param typeId 字典类型ID
     * @return 数据是否存在。0: 不存在, 1: 存在。
     */
    int existsByTypeId(Long typeId);

    /**
     * 同一类型内是否已有该 value
     *
     * @param typeId 字典类型ID
     * @param value  值
     * @return 存在返回 1，否则 0
     */
    int existsByValue(@Param("typeId") Long typeId, @Param("value") String value);

    /**
     * 根据 字典类型ID 删除数据（删除类型时级联其条目）
     *
     * @param typeId 字典类型ID
     * @return 被删除的数据条数
     */
    int deleteByTypeId(Long typeId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}