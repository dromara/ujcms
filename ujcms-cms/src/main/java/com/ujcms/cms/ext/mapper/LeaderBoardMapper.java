package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.LeaderBoard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 排行榜 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface LeaderBoardMapper {
    /**
     * 栏目文章排行榜
     *
     * @param siteId 站点ID
     * @param status 文章状态数组
     * @param begin  开始日期
     * @param end    介绍日期
     * @return 统计结果
     */
    List<LeaderBoard> channelLeaderBoard(@Param("siteId") @Nullable Long siteId,
                                         @Param("status") @Nullable Collection<Short> status,
                                         @Param("begin") @Nullable OffsetDateTime begin,
                                         @Param("end") @Nullable OffsetDateTime end);

    /**
     * 组织文章排行榜
     *
     * @param orgId  组织ID
     * @param status 文章状态数组
     * @param begin  开始日期
     * @param end    介绍日期
     * @return 统计结果
     */
    List<LeaderBoard> orgLeaderBoard(@Param("orgId") @Nullable Long orgId,
                                     @Param("status") @Nullable Collection<Short> status,
                                     @Param("begin") @Nullable OffsetDateTime begin,
                                     @Param("end") @Nullable OffsetDateTime end);

    /**
     * 用户文章排行榜
     *
     * @param orgId  组织ID
     * @param status 文章状态数组
     * @param begin  开始日期
     * @param end    介绍日期
     * @return 统计结果
     */
    List<LeaderBoard> userLeaderBoard(@Param("orgId") @Nullable Long orgId,
                                      @Param("status") @Nullable Collection<Short> status,
                                      @Param("begin") @Nullable OffsetDateTime begin,
                                      @Param("end") @Nullable OffsetDateTime end);
}