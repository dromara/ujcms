package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ArticleTag;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章TAG关联 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ArticleTagMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ArticleTag bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(ArticleTag bean);

    /**
     * 删除数据
     *
     * @param articleId 文章ID
     * @param tagId     TagID
     * @return 删除条数
     */
    int delete(@Param("articleId") Long articleId, @Param("tagId") Long tagId);

    /**
     * 根据主键获取数据
     *
     * @param articleId 文章ID
     * @param tagId     TagID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    ArticleTag select(@Param("articleId") Long articleId, @Param("tagId") Long tagId);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<ArticleTag> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据文章ID获取列表
     *
     * @param articleId 文章ID
     * @return 数据列表
     */
    List<ArticleTag> listByArticleId(Long articleId);

    /**
     * 根据文章ID删除数据
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(Long articleId);

    /**
     * 根据TagID删除数据
     *
     * @param tagId TagID
     * @return 删除条数
     */
    int deleteByTagId(Long tagId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);


}