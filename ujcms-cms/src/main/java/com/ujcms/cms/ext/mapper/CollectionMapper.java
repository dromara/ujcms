package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Collection;
import com.ujcms.common.db.order.OrderEntityMapper;
import com.ujcms.common.query.QueryInfo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 采集 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface CollectionMapper extends OrderEntityMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Collection bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Collection bean);

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
    Collection select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Collection> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据 栏目ID 查询文章数量
     *
     * @param channelId 栏目ID
     * @return 文章数量
     */
    int existsByChannelId(@Param("channelId") Long channelId);

    /**
     * 获取采集状态。采集线程在循环中通过此方法从数据库重新读取状态，以便及时响应用户的停止、暂停操作
     *
     * @param id 主键ID
     * @return 状态值。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Short selectStatus(Long id);

    /**
     * 抢占运行状态（CAS）。仅当状态不是运行中时，才将状态设置为运行中，保证并发启动时只有一个成功
     *
     * @param id 主键ID
     * @return 更新条数。为 0 代表已在运行中，抢占失败
     */
    int startRunning(Long id);

    /**
     * 更新采集进度。只更新进度相关字段，且仅在运行中状态下生效，避免覆盖用户的停止、暂停操作及其它配置修改
     *
     * @param id           主键ID
     * @param currentPages 当前页数
     * @param currentItems 当前条数
     * @param successCount 成功条数
     * @param totalCount   总条数
     * @return 更新条数
     */
    int updateProgress(@Param("id") Long id, @Param("currentPages") Integer currentPages,
                       @Param("currentItems") Integer currentItems, @Param("successCount") Integer successCount,
                       @Param("totalCount") Integer totalCount);

    /**
     * 更新采集状态和错误信息。只更新状态相关字段，避免覆盖运行中的进度及其它配置修改
     *
     * @param id        主键ID
     * @param status    状态值
     * @param errorInfo 错误信息
     * @return 更新条数
     */
    int updateStatus(@Param("id") Long id, @Param("status") Short status,
                     @Param("errorInfo") @Nullable String errorInfo);

    /**
     * 仅当处于运行中状态时更新状态。用于任务正常完成置为“完成”、用户暂停等场景，避免覆盖已发生的停止操作
     *
     * @param id     主键ID
     * @param status 状态值
     * @return 更新条数
     */
    int updateStatusIfRunning(@Param("id") Long id, @Param("status") Short status);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}