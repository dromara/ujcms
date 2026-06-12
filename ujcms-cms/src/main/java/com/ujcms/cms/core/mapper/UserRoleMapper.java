package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 用户角色关系 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface UserRoleMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(UserRole bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(UserRole bean);

    /**
     * 删除数据
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 删除条数
     */
    int delete(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 根据 用户ID 删除数据
     *
     * @param userId 用户ID
     * @return 删除条数
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 根据 角色ID 删除数据
     *
     * @param roleId 角色ID
     * @return 删除条数
     */
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 根据主键获取数据
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    UserRole select(@Param("userId") Long userId, @Param("roleId") Long roleId);
}