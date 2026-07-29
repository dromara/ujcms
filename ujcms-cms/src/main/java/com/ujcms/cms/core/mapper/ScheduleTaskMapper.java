package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ScheduleTask;
import com.ujcms.common.db.order.OrderEntityMapper;
import com.ujcms.common.query.QueryInfo;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 定时任务 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ScheduleTaskMapper extends OrderEntityMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ScheduleTask bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(ScheduleTask bean);

    /**
     * 更新执行结果。只更新执行结果相关字段，避免覆盖用户对任务配置的并发修改
     *
     * @param id         主键ID
     * @param lastRunAt  最近执行时间
     * @param lastStatus 最近执行状态
     * @param errorInfo  错误信息
     * @return 更新条数
     */
    int updateRunResult(@Param("id") Long id, @Param("lastRunAt") OffsetDateTime lastRunAt,
                        @Param("lastStatus") Short lastStatus, @Param("errorInfo") @Nullable String errorInfo);

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
    @Override
    ScheduleTask select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<ScheduleTask> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 获取所有已启用的定时任务。用于应用启动时重建调度
     *
     * @return 数据列表
     */
    List<ScheduleTask> listEnabled();

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 根据用户ID删除数据
     *
     * @param userId 用户ID
     * @return 被删除的数据条数
     */
    int deleteByUserId(Long userId);
}
