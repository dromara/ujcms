package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleRelated;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 相关文章关联 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ArticleRelatedMapper {
    /**
     * 插入一行关联
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(ArticleRelated bean);

    /**
     * 批量插入
     *
     * @param list 实体对象列表
     * @return 插入条数
     */
    int insertAll(@Param("list") List<ArticleRelated> list);

    /**
     * 删除一行关联
     *
     * @param articleId 文章ID
     * @param relatedId 相关文章ID
     * @return 删除条数
     */
    int delete(@Param("articleId") Long articleId, @Param("relatedId") Long relatedId);

    /**
     * 关联是否存在
     *
     * @param articleId 文章ID
     * @param relatedId 相关文章ID
     * @return 存在则大于 0
     */
    int exists(@Param("articleId") Long articleId, @Param("relatedId") Long relatedId);

    /**
     * 文章列表内最大排序值
     *
     * @param articleId 文章ID
     * @return 最大排序值，无关联则为 {@code null}
     */
    @Nullable
    Integer maxOrderByArticleId(@Param("articleId") Long articleId);

    /**
     * 文章的出向关联行（按 order_ 升序）
     *
     * @param articleId 文章ID
     * @return 关联行列表
     */
    List<ArticleRelated> listByArticleId(@Param("articleId") Long articleId);

    /**
     * 文章手选的相关文章（按 order_ 升序）。{@code status} 为空则不限状态（控制台需要看到已下线/回收站的行）
     *
     * @param articleId 文章ID
     * @param status    状态集合，可为 {@code null}
     * @return 文章列表
     */
    List<Article> listRelatedArticles(@Param("articleId") Long articleId,
                                      @Nullable @Param("status") Collection<Short> status);

    /**
     * 反向：把该文章列为相关文章的文章（"被关联"）
     *
     * @param articleId 文章ID
     * @param status    状态集合，可为 {@code null}
     * @return 文章列表
     */
    List<Article> listIncomingArticles(@Param("articleId") Long articleId,
                                       @Nullable @Param("status") Collection<Short> status);

    /**
     * 删除文章的出向关联行
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteOutgoing(@Param("articleId") Long articleId);

    /**
     * 删除文章的全部关联行（出向与入向）
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(@Param("articleId") Long articleId);

    /**
     * 删除站点全部文章的关联行（任一端属于该站点）
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(@Param("siteId") Long siteId);
}
