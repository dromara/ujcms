package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.SurveyItemFeedback;
import com.ujcms.commons.query.QueryInfo;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 调查问卷条目反馈 实体类
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SurveyItemFeedbackMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(SurveyItemFeedback bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(SurveyItemFeedback bean);

    /**
     * 删除数据
     *
     * @param id 主键ID
     * @return 删除条数
     */
    int delete(Integer id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    SurveyItemFeedback select(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<SurveyItemFeedback> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据调查问卷条目ID列表删除数据
     *
     * @param itemIds 调查问卷条目ID列表
     * @return 被删除的数据条数
     */
    int deleteByItemIds(List<Integer> itemIds);

    /**
     * 根据调查反馈ID删除数据
     *
     * @param feedbackId 调查反馈ID
     * @return 被删除的数据条数
     */
    int deleteByFeedbackId(@Param("feedbackId") Integer feedbackId);

    /**
     * 根据调查问卷ID删除数据
     *
     * @param surveyId 调查问卷ID
     * @param itemIds  不包含的调查问卷条目id列表
     * @return 被删除的数据条数
     */
    int deleteBySurveyId(@Param("surveyId") Integer surveyId, @Param("itemIds") List<Integer> itemIds);

    /**
     * 根据用户ID删除数据
     *
     * @param userId 用户ID
     * @return 被删除的数据条数
     */
    int deleteByUserId(@Param("userId") Integer userId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(@Param("siteId") Integer siteId);

}