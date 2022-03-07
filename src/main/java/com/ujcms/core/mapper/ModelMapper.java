package com.ujcms.core.mapper;

import com.ujcms.core.domain.Model;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 模型 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ModelMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Model bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Model bean);

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
    Model select(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Model> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 获取全局设置模型（ID: 1）
     *
     * @return 全局设置模型
     */
    @Nullable
    Model selectGlobalModel();

    /**
     * 获取用户模型（ID: 2）
     *
     * @return 用户模型
     */
    @Nullable
    Model selectUserModel();
}