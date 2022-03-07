package com.ujcms.core.mapper;

import com.ujcms.core.domain.UserRole;
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
    int delete(@Param("userId") Integer userId, @Param("roleId") Integer roleId);

    /**
     * 根据 用户ID 删除数据
     *
     * @param userId 用户ID
     * @return 删除条数
     */
    int deleteByUserId(@Param("userId") Integer userId);

    /**
     * 根据主键获取数据
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    UserRole select(@Param("userId") Integer userId, @Param("roleId") Integer roleId);
}