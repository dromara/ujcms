package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.core.domain.PerformanceType;
import com.ujcms.cms.ext.domain.PerformanceStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 绩效统计 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface PerformanceStatMapper {
    /**
     * 绩效统计
     *
     * @param type             类型 (user: 用户绩效; org: 组织绩效)
     * @param performanceTypes 绩效类型列表
     * @param siteId           站点ID
     * @param begin            开始日期
     * @param end              介绍日期
     * @return 计结果
     */
    List<PerformanceStat> statBy(@Param("type") String type,
                                 @Param("performanceTypes") List<PerformanceType> performanceTypes,
                                 @Param("siteId") @Nullable Long siteId,
                                 @Param("begin") @Nullable OffsetDateTime begin,
                                 @Param("end") @Nullable OffsetDateTime end);
}