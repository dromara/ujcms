package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.RoleOrg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色组织权限 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface RoleOrgMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(RoleOrg bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(RoleOrg bean);


    /**
     * 根据主键获取数据
     *
     * @param roleId 角色ID
     * @param orgId  组织ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    RoleOrg select(@Param("roleId") Long roleId, @Param("orgId") Long orgId);

    /**
     * 根据 角色ID 查询组织ID列表
     *
     * @param roleId 角色ID
     * @param siteId 站点ID
     * @return 组织ID列表
     */
    List<Long> listOrgByRoleId(@Param("roleId") Long roleId, @Nullable @Param("siteId") Long siteId);
    /**
     * 根据 组织ID 删除数据
     *
     * @param orgId 组织ID
     * @return 删除条数
     */
    int deleteByOrgId(@Param("orgId") Long orgId);

    /**
     * 根据 角色ID 删除数据
     *
     * @param roleId 角色ID
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteByRoleId(@Param("roleId") Long roleId, @Nullable @Param("siteId") Long siteId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}