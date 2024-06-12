package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.ext.domain.LeaderBoard;
import com.ujcms.cms.ext.mapper.LeaderBoardMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 排行榜 Service
 *
 * @author PONY
 */
@Service
public class LeaderBoardService {
    private final LeaderBoardMapper mapper;

    public LeaderBoardService(LeaderBoardMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 栏目文章排行榜
     *
     * @param siteId 站点ID
     * @param status 文章状态数组
     * @param begin  开始日期
     * @param end    介绍日期
     * @return 统计结果
     */
    public List<LeaderBoard> channelLeaderBoardList(@Nullable Long siteId,
                                                    @Nullable Collection<Short> status,
                                                    @Nullable final OffsetDateTime begin,
                                                    @Nullable final OffsetDateTime end,
                                                    int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() ->
                mapper.channelLeaderBoard(siteId, status, begin, end));
    }

    /**
     * 栏目文章排行榜
     *
     * @param siteId 站点ID
     * @param status 文章状态数组
     * @param begin  开始日期
     * @param end    介绍日期
     * @return 统计结果
     */
    public Page<LeaderBoard> channelLeaderBoardPage(@Nullable Long siteId,
                                                    @Nullable Collection<Short> status,
                                                    @Nullable final OffsetDateTime begin,
                                                    @Nullable final OffsetDateTime end,
                                                    int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() ->
                mapper.channelLeaderBoard(siteId, status, begin, end));
    }

    /**
     * 组织文章排行榜
     *
     * @param orgId  组织ID
     * @param status 文章状态数组
     * @param begin  开始日期
     * @param end    介绍日期
     * @return 统计结果
     */
    public List<LeaderBoard> orgLeaderBoardList(@Nullable Long orgId,
                                                @Nullable Collection<Short> status,
                                                @Nullable OffsetDateTime begin,
                                                @Nullable OffsetDateTime end,
                                                int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() ->
                mapper.orgLeaderBoard(orgId, status, begin, end));
    }

    /**
     * 组织文章排行榜
     *
     * @param orgId  组织ID
     * @param status 文章状态数组
     * @param begin  开始日期
     * @param end    介绍日期
     * @return 统计结果
     */
    public Page<LeaderBoard> orgLeaderBoardPage(@Nullable Long orgId,
                                                @Nullable Collection<Short> status,
                                                @Nullable OffsetDateTime begin,
                                                @Nullable OffsetDateTime end,
                                                int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() ->
                mapper.orgLeaderBoard(orgId, status, begin, end));
    }

    /**
     * 用户文章排行榜
     *
     * @param orgId  组织ID
     * @param status 文章状态数组
     * @param begin  开始日期
     * @param end    介绍日期
     * @return 统计结果
     */
    public List<LeaderBoard> userLeaderBoardList(@Nullable Long orgId,
                                                 @Nullable Collection<Short> status,
                                                 @Nullable OffsetDateTime begin,
                                                 @Nullable OffsetDateTime end,
                                                 int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() ->
                mapper.userLeaderBoard(orgId, status, begin, end));
    }

    /**
     * 用户文章排行榜
     *
     * @param orgId  组织ID
     * @param status 文章状态数组
     * @param begin  开始日期
     * @param end    介绍日期
     * @return 统计结果
     */
    public Page<LeaderBoard> userLeaderBoardPage(@Nullable Long orgId,
                                                 @Nullable Collection<Short> status,
                                                 @Nullable OffsetDateTime begin, @Nullable OffsetDateTime end,
                                                 int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() ->
                mapper.userLeaderBoard(orgId, status, begin, end));
    }
}