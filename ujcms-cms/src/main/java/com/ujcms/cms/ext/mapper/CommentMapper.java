package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Comment;
import com.ujcms.common.query.QueryInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * @author PONY
 */
@Mapper
@Repository
public interface CommentMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Comment bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Comment bean);

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
    Comment select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Comment> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据上级评论ID删除数据
     *
     * @param parentId 上级评论ID
     * @return 删除条数
     */
    int deleteByParentId(Long parentId);

    /**
     * 根据文章ID删除数据
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(Long articleId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 增减文章评论次数
     *
     * @param articleId 文章ID
     * @param amount    增减数量。负数为减少
     * @return 更新条数
     */
    int addArticleComments(@Param("articleId") Long articleId, @Param("amount") int amount);
}
