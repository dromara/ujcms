package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.common.query.QueryInfo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 短信 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ShortMessageMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ShortMessage bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(ShortMessage bean);

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
    ShortMessage select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<ShortMessage> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 占用一次尝试机会：仅当短信未使用且尝试次数未达上限时，尝试次数加一。
     * <p>
     * 由数据库原子完成“判断 + 自增”，并发提交同一验证码时，总校验次数也不会超过上限。
     *
     * @param id          主键ID
     * @param maxAttempts 允许的最大尝试次数
     * @return 更新条数。{@code 0} 代表已使用或尝试次数已达上限
     */
    int increaseAttempts(@Param("id") Long id, @Param("maxAttempts") int maxAttempts);

    /**
     * 仅当短信未使用时更新状态。用于消费（验证正确）或作废（过期）验证码，并发时只有一个请求能成功。
     *
     * @param id     主键ID
     * @param status 新状态
     * @return 更新条数。{@code 0} 代表已被其它请求使用
     */
    int updateStatusIfUnused(@Param("id") Long id, @Param("status") short status);

    /**
     * 尝试次数达到上限时作废验证码。尝试次数以数据库当前值为准。
     *
     * @param id          主键ID
     * @param maxAttempts 允许的最大尝试次数
     * @param status      作废状态
     * @return 更新条数
     */
    int exceedIfAttemptsReached(@Param("id") Long id, @Param("maxAttempts") int maxAttempts,
                                @Param("status") short status);
}