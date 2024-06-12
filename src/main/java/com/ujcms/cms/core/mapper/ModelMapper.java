package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Model;
import com.ujcms.commons.query.QueryInfo;
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
    int delete(Long id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Model select(Long id);

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
    Model selectConfigModel();

    /**
     * 获取用户模型（ID: 2）
     *
     * @return 用户模型
     */
    @Nullable
    Model selectUserModel();

    /**
     * 根据站点ID删除模型
     *
     * @param siteId 站点ID
     * @return 删除的条数
     */
    int deleteBySiteId(Long siteId);
}