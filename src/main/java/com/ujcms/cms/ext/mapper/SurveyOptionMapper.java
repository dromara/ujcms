package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.SurveyOption;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 调查问卷选项 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SurveyOptionMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(SurveyOption bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(SurveyOption bean);

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
    SurveyOption select(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<SurveyOption> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 投票
     *
     * @param itemId    调查问卷条目ID
     * @param optionIds 调查问卷选项ID列表
     * @return 更新条数
     */
    int cast(@Param("itemId") Integer itemId, @Param("optionIds") List<Integer> optionIds);

    /**
     * 根据调查问卷条目ID获取列表
     *
     * @param itemId 调查问卷条目ID
     * @return 数据列表
     */
    List<SurveyOption> listByItemId(Integer itemId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Integer siteId);

    /**
     * 删除属于某调查问卷ID，且不属于itemIds的调查问卷选项数据
     *
     * @param surveyId 调查问卷ID
     * @param itemIds  不包含的调查条目id列表
     * @return 被删除的数据条数
     */
    int deleteBySurveyId(@Param("surveyId") Integer surveyId, @Param("itemIds") List<Integer> itemIds);

    /**
     * 删删除属于某调查问卷条目ID，且不包含的相应调查问卷选项id列表的数据
     *
     * @param itemId 调查问卷条目ID
     * @param ids    不包含的调查选项id列表
     * @return 被删除的数据条数
     */
    int deleteByItemId(@Param("itemId") Integer itemId, @Param("ids") List<Integer> ids);
}