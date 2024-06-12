package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.User;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 用户 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface UserMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(User bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(User bean);

    /**
     * 删除数据
     *
     * @param id 主键ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 根据主键获取引用对象（不包括关联对象属性）
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Org selectRefer(Long id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    User select(Long id);

    /**
     * 根据用户名获取用户
     *
     * @param username 用户名
     * @return 用户对象
     */
    @Nullable
    User selectByUsername(String username);

    /**
     * 根据邮箱获取用户
     *
     * @param email 邮箱地址
     * @return 用户对象
     */
    @Nullable
    User selectByEmail(String email);

    /**
     * 根据手机号获取用户
     *
     * @param mobile 手机号
     * @return 用户对象
     */
    @Nullable
    User selectByMobile(String mobile);

    /**
     * 根据提供商和openid获取用户
     *
     * @param provider 提供商
     * @param openid   openid
     * @return 用户对象
     */
    @Nullable
    User selectByOpenid(@Param("provider") String provider, @Param("openid") String openid);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @param orgId     组织ID
     * @return 数据列表
     */
    List<User> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo, @Nullable @Param("orgId") Long orgId);

    /**
     * 统计用户数量
     *
     * @param created 创建日期
     * @return 用户数量
     */
    int countByCreated(@Param("created") @Nullable OffsetDateTime created);

    /**
     * 根据组织ID统计用户数量
     *
     * @param orgId 组织ID
     * @return 用户数量
     */
    int existsByOrgId(Long orgId);

    /**
     * 根据用户组ID统计用户数量
     *
     * @param groupId 用户组ID
     * @return 用户数量
     */
    int existsByGroupId(Long groupId);

    /**
     * 根据角色ID和组织ID统计数量
     *
     * @param roleId   角色ID
     * @param notOrgId 非本组织ID
     * @return 数据条数
     */
    int existsByRoleId(@Param("roleId") Long roleId, @Param("notOrgId") Long notOrgId);
}