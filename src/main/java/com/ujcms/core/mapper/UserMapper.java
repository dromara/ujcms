package com.ujcms.core.mapper;

import com.ujcms.core.domain.User;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

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
    int delete(Integer id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    User select(Integer id);

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
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<User> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据组织ID统计用户数量
     *
     * @param orgId 组织ID
     * @return 用户数量
     */
    int countByOrgId(Integer orgId);
}