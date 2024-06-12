package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.VisitLog;
import com.ujcms.cms.ext.domain.VisitPage;
import com.ujcms.cms.ext.domain.VisitStat;
import com.ujcms.cms.ext.domain.VisitTrend;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 访问日志 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface VisitLogMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(VisitLog bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(VisitLog bean);

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
    VisitLog select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<VisitLog> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 批量插入数据
     *
     * @param list 待插入数据
     * @return 插入数据量
     */
    int insertBatch(List<VisitLog> list);

    /**
     * 删除指定日期之前的数据
     *
     * @param date 指定的日期
     * @return 被删除的数据条数
     */
    int deleteBeforeDate(@Param("date") OffsetDateTime date);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(@Param("siteId") Long siteId);

    /**
     * 根据用户ID删除数据
     *
     * @param userId 用户ID
     * @return 被删除的数据条数
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 统计访客数
     *
     * @param date 在此日期后
     * @return 访客数
     */
    int countVisitors(@Param("date") OffsetDateTime date);

    /**
     * 根据字段名称统计
     *
     * @param name  统计的字段名称
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    List<VisitStat> statByName(@Param("name") String name,
                               @Param("begin") OffsetDateTime begin, @Param("end") OffsetDateTime end);

    /**
     * 统计省份
     *
     * @param country 国家
     * @param begin   开始日期
     * @param end     介绍日期
     * @return 统计结果
     */
    List<VisitStat> statProvince(@Param("country") String country,
                                 @Param("begin") OffsetDateTime begin, @Param("end") OffsetDateTime end);

    /**
     * 统计受访页面
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    List<VisitPage> statUrl(@Param("begin") OffsetDateTime begin, @Param("end") OffsetDateTime end);

    /**
     * 统计入口页面
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    List<VisitPage> statEntryUrl(@Param("begin") OffsetDateTime begin, @Param("end") OffsetDateTime end);

    /**
     * 统计趋势
     *
     * @param begin 开始日期
     * @param end   介绍日期
     * @return 统计结果
     */
    List<VisitTrend> statTrend(@Param("begin") OffsetDateTime begin, @Param("end") OffsetDateTime end);
}