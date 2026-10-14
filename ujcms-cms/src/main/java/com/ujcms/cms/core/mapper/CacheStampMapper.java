package com.ujcms.cms.core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

/**
 * 跨节点缓存作废戳记 Mapper（composition-invalidation 计划 §2）。按缓存名单行计数：
 * 写路径在业务事务内递增（提交即对轮询节点原子可见），各节点定时轮询比对，值变则本地全量作废。
 * 单行计数器天然幂等——漏读若干次递增与读到一次的效果相同（都是全量作废），无事件日志的
 * 乱序可见 / 清理问题。种子行由 changelog 预置（{@code composition}），避免并发首插的跨方言竞态。
 *
 * @author PONY
 */
@Mapper
@Repository
public interface CacheStampMapper {
    /**
     * 取当前计数（无行时为 {@code null}）
     *
     * @param name 缓存名
     * @return 计数值
     */
    @Nullable
    Long selectValue(@Param("name") String name);

    /**
     * 计数递增（在调用方事务内执行）
     *
     * @param name      缓存名
     * @param updatedAt 递增时间
     * @return 更新条数（0 表示行缺失，需补插）
     */
    int increment(@Param("name") String name, @Param("updatedAt") OffsetDateTime updatedAt);

    /**
     * 补插戳记行（仅种子行被人工删除后的兜底路径）
     *
     * @param name      缓存名
     * @param value     初始计数
     * @param updatedAt 时间
     * @return 插入条数
     */
    int insert(@Param("name") String name, @Param("value") long value,
               @Param("updatedAt") OffsetDateTime updatedAt);
}
