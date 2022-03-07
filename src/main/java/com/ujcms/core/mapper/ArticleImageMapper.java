package com.ujcms.core.mapper;

import com.ujcms.core.domain.ArticleImage;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章图片集 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ArticleImageMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ArticleImage bean);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<ArticleImage> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据文章ID删除数据
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(Integer articleId);

    /**
     * 根据文章ID获取列表
     *
     * @param articleId 文章ID
     * @return 数据列表
     */
    List<ArticleImage> listByArticleId(Integer articleId);
}