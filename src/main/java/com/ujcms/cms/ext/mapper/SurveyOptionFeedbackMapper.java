package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.SurveyOptionFeedback;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 调查问卷选项反馈 实体类
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SurveyOptionFeedbackMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(SurveyOptionFeedback bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(SurveyOptionFeedback bean);

    /**
     * 删除数据
     *
     * @param id 主键ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 根据调查问卷选项ID列表删除数据
     *
     * @param optionIds 调查问卷选项ID列表
     * @return 被删除的数据条数
     */
    int deleteByOptionIds(List<Long> optionIds);

    /**
     * 删删除属于某调查问卷条目ID，且不包含的相应调查问卷选项id列表的数据
     *
     * @param itemId    调查问卷条目ID
     * @param optionIds 不包含的调查选项id列表
     * @return 被删除的数据条数
     */
    int deleteByItemId(@Param("itemId") Long itemId, @Param("optionIds") List<Long> optionIds);

    /**
     * 根据调查反馈ID删除数据
     *
     * @param feedbackId 调查反馈ID
     * @return 被删除的数据条数
     */
    int deleteByFeedbackId(@Param("feedbackId") Long feedbackId);

    /**
     * 根据调查问卷ID删除数据
     *
     * @param surveyId 调查问卷ID
     * @param itemIds  不包含的调查条目d列表
     * @return 被删除的数据条数
     */
    int deleteBySurveyId(@Param("surveyId") Long surveyId, @Param("itemIds") List<Long> itemIds);

    /**
     * 根据用户ID删除数据
     *
     * @param userId 用户ID
     * @return 被删除的数据条数
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(@Param("siteId") Long siteId);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    SurveyOptionFeedback select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<SurveyOptionFeedback> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);
}