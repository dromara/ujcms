package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.ImportData;
import com.ujcms.commons.query.QueryInfo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 导入数据 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ImportDataMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ImportData bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(ImportData bean);

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
    ImportData select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<ImportData> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据 原ID 获取数据
     *
     * @param origId 原ID
     * @return 实体对象
     */
    @Nullable
    ImportData selectByOrigId(String origId);

    /**
     * 根据类型删除数据
     *
     * @param type 类型。为 null 则删除所有
     * @return 删除条数
     */
    int deleteByType(Short type);
}