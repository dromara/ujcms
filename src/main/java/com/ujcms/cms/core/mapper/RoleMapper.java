package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Role;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface RoleMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Role bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Role bean);

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
    Role select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Role> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据用户ID获取角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    List<Role> listByUserId(Long userId);

    /**
     * 根据栏目ID获取文章角色列表
     *
     * @param channelId 栏目ID
     * @return 角色列表
     */
    List<Role> articleRoleList(Long channelId);

    /**
     * 根据栏目ID获取栏目角色列表
     *
     * @param channelId 栏目ID
     * @return 角色列表
     */
    List<Role> channelRoleList(Long channelId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}