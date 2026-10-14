package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.ArticleTopic;
import com.ujcms.cms.core.domain.Topic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章专题关联 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ArticleTopicMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ArticleTopic bean);

    /**
     * 批量插入
     *
     * @param list 实体对象列表
     * @return 插入条数
     */
    int insertAll(@Param("list") List<ArticleTopic> list);

    /**
     * 删除数据
     *
     * @param articleId 文章ID
     * @param topicId   专题ID
     * @return 删除条数
     */
    int delete(@Param("articleId") Long articleId, @Param("topicId") Long topicId);

    /**
     * 根据主键获取数据
     *
     * @param articleId 文章ID
     * @param topicId   专题ID
     * @return 实体对象，没有找到数据则返回 {@code null}
     */
    @Nullable
    ArticleTopic select(@Param("articleId") Long articleId, @Param("topicId") Long topicId);

    /**
     * 根据文章ID获取关联列表
     *
     * @param articleId 文章ID
     * @return 数据列表
     */
    List<ArticleTopic> listByArticleId(@Param("articleId") Long articleId);

    /**
     * 根据专题ID获取关联列表（按 order_ 升序）
     *
     * @param topicId 专题ID
     * @return 数据列表
     */
    List<ArticleTopic> listByTopicId(@Param("topicId") Long topicId);

    /**
     * 根据文章ID获取专题列表
     *
     * @param articleId 文章ID
     * @return 专题列表
     */
    List<Topic> listTopicsByArticleId(@Param("articleId") Long articleId);

    /**
     * 根据文章ID删除关联
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(@Param("articleId") Long articleId);

    /**
     * 根据专题ID删除关联
     *
     * @param topicId 专题ID
     * @return 删除条数
     */
    int deleteByTopicId(@Param("topicId") Long topicId);
}
