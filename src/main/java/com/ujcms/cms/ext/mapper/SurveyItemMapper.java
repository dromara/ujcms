package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.SurveyItem;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 调查问卷条目 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SurveyItemMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(SurveyItem bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(SurveyItem bean);

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
    SurveyItem select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<SurveyItem> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据问卷调查ID获取列表
     *
     * @param surveyId 问卷调查ID
     * @return 数据列表
     */
    List<SurveyItem> listBySurveyId(Long surveyId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 删除属于某调查问卷ID，且不包含的相应调查问卷条目id列表的数据
     *
     * @param surveyId 调查问卷ID
     * @param ids      不包含的调查问卷条目id列表
     * @return 被删除的数据条数
     */
    int deleteBySurveyId(@Param("surveyId") Long surveyId, @Param("ids") List<Long> ids);
}