package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Global;
import com.ujcms.commons.query.QueryInfo;
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
public interface GlobalMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Global bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Global bean);

    /**
     * 删除数据
     *
     * @param name 键名
     * @return 删除条数
     */
    int delete(String name);

    /**
     * 根据主键获取数据
     *
     * @param name 键名
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Global select(String name);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Global> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);
}