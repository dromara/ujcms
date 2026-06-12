package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.UserOrg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 用户扩展组织关联 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface UserOrgMapper {
    /**
     * 根据主键获取数据
     *
     * @param userId 用户ID
     * @param orgId  组织ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    UserOrg select(@Param("userId") Long userId, @Param("orgId") Long orgId);

    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(UserOrg bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(UserOrg bean);

    /**
     * 根据用户ID删除数据
     *
     * @param userId 用户ID
     * @return 删除条数
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 根据组织ID删除数据
     *
     * @param orgId 组织ID
     * @return 删除条数
     */
    int deleteByOrgId(@Param("orgId") Long orgId);

    /**
     * 根据 用户ID、祖先组织ID 删除数据
     *
     * @param userId        用户ID
     * @param ancestorOrgId 祖先组织ID
     * @return 删除条数
     */
    int deleteByUserIdAndAncestorOrgId(@Param("userId") Long userId,
                                       @Nullable @Param("ancestorOrgId") Long ancestorOrgId);
}