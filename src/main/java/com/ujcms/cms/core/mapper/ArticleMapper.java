package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 文章 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ArticleMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Article bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Article bean);

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
    Article select(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo        查询条件
     * @param customsCondition 自定义字段查询条件
     * @param channelId        栏目ID
     * @param orgId            组织ID
     * @return 数据列表
     */
    List<Article> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                            @Nullable @Param("customsCondition") List<QueryInfo.WhereCondition> customsCondition,
                            @Nullable @Param("channelId") Integer channelId,
                            @Nullable @Param("orgId") Integer orgId);

    /**
     * 根据 id 列表查询
     *
     * @param ids id 列表
     * @return 数据列表
     */
    List<Article> listByIds(@Param("ids") Iterable<Integer> ids);

    /**
     * 根据站点ID获取文章列表用于sitemap。由于sitemap获取的数据量较大，只查询必须字段。
     *
     * @param siteId 站点ID
     * @param minId  最小的文章ID。从此文章ID开始获取数据。
     * @return 数据列表
     */
    List<Article> listBySiteIdForSitemap(Integer siteId, @Nullable Integer minId);

    /**
     * 根据栏目ID查询文章列表
     *
     * @param channelId 栏目ID
     * @return 文章列表
     */
    List<Article> listByChannelId(@Param("channelId") Integer channelId);

    /**
     * 根据源文章ID查询文章列表
     *
     * @param srcId 源文章ID
     * @return 文章列表
     */
    List<Article> listBySrcId(@Param("srcId") Integer srcId);

    /**
     * 查询下一条文章
     *
     * @param id          文章ID
     * @param publishDate 文章发布时间
     * @param channelId   栏目ID
     * @return 下一条文章
     */
    List<Article> findNext(@Param("id") Integer id, @Param("publishDate") OffsetDateTime publishDate, @Param("channelId") Integer channelId);

    /**
     * 查询上一条文章
     *
     * @param id          文章ID
     * @param publishDate 文章发布时间
     * @param channelId   栏目ID
     * @return 上一条文章
     */
    List<Article> findPrev(@Param("id") Integer id, @Param("publishDate") OffsetDateTime publishDate, @Param("channelId") Integer channelId);

    /**
     * 根据 栏目ID 查询文章数量
     *
     * @param channelId 栏目ID
     * @return 文章数量
     */
    int countByChannelId(@Param("channelId") Integer channelId);

    /**
     * 根据 用户ID 查询文章数量
     *
     * @param userId 用户ID
     * @return 文章数量
     */
    int countByUserId(Integer userId);

    /**
     * 根据 站点ID 查询文章数量
     *
     * @param siteId 用户ID
     * @param status 文章状态
     * @return 文章数量
     */
    int countBySiteId(@Param("siteId") Integer siteId, @Param("status") Collection<Short> status);

    /**
     * 查询最大的ID值
     *
     * @param siteId 用户ID
     * @param status 文章状态
     * @return 最大的ID值
     */
    Map<String, Object> statForSitemap(@Param("siteId") Integer siteId, @Param("status") Collection<Short> status);

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

    /**
     * 根据栏目ID获取第一条文章
     *
     * @param channelId 栏目ID
     * @return 第一条文章
     */
    @Nullable
    Article findFirstByChannelId(Integer channelId);
}