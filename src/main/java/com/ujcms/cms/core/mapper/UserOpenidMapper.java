package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.UserOpenid;
import com.ujcms.commons.query.QueryInfo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 用户Openid Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface UserOpenidMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(UserOpenid bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(UserOpenid bean);

    /**
     * 删除数据
     *
     * @param userId   用户ID
     * @param provider 提供商
     * @return 删除条数
     */
    int delete(@Param("userId") Long userId, @Param("provider") String provider);

    /**
     * 根据用户ID删除数据
     *
     * @param userId 用户ID
     * @return 删除条数
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 根据主键获取数据
     *
     * @param userId   用户ID
     * @param provider 提供商
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    UserOpenid select(@Param("userId") Long userId, @Param("provider") String provider);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<UserOpenid> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);
}