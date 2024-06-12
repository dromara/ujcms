package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.SurveyFeedback;
import com.ujcms.commons.query.QueryInfo;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 调查反馈 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SurveyFeedbackMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(SurveyFeedback bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(SurveyFeedback bean);

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
    SurveyFeedback select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<SurveyFeedback> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据调查问卷ID删除数据
     *
     * @param surveyId 调查问卷ID
     * @return 被删除的数据条数
     */
    int deleteBySurveyId(Long surveyId);

    /**
     * 根据用户ID删除数据
     *
     * @param userId 用户ID
     * @return 被删除的数据条数
     */
    int deleteByUserId(Long userId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 统计反馈数量，用于判断是否已经参与过调查。
     *
     * @param surveyId 调查问卷ID
     * @param date     此日期之后
     * @param userId   用户ID
     * @param ip       IP地址
     * @param cookie   cookie标识
     * @return 数据数量
     */
    int countBy(@Nullable @Param("surveyId") Long surveyId,
                @Nullable @Param("date") OffsetDateTime date,
                @Nullable @Param("userId") Long userId,
                @Nullable @Param("ip") String ip,
                @Nullable @Param("cookie") Long cookie);
}