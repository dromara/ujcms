package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ArticleStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 文章浏览统计 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ArticleStatMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ArticleStat bean);

    /**
     * 获取 统计日期 前 8,31,91,366 天的统计对象列表
     *
     * @param articleId 文章ID
     * @param statDays  统计日列表
     * @return 对象列表
     */
    List<ArticleStat> listByStatDay(@Param("articleId") Integer articleId,
                                    @Param("statDays") Collection<Integer> statDays);

    /**
     * 根据 文章ID 删除数据
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(@Param("articleId") Integer articleId);

    /**
     * 删除 统计日期 之前所有数据
     *
     * @param articleId 文章ID
     * @param statDay   统计日期
     * @return 删除条数
     */
    int deleteByStatDay(@Param("articleId") Integer articleId, @Param("statDay") int statDay);

    /**
     * 根据栏目ID删除数据
     *
     * @param channelId 栏目ID
     * @return 被删除的数据条数
     */
    int deleteByChannelId(Integer channelId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Integer siteId);
}