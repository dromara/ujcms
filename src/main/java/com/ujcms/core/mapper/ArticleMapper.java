package com.ujcms.core.mapper;

import com.ujcms.core.domain.Article;
import com.ofwise.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

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
     * @return 数据列表
     */
    List<Article> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                            @Nullable @Param("customsCondition") List<QueryInfo.WhereCondition> customsCondition,
                            @Nullable @Param("channelId") Integer channelId);

    /**
     * 根据 id 列表查询
     *
     * @param ids id 列表
     * @return 数据列表
     */
    List<Article> listByIds(@Param("ids") Iterable<Integer> ids);

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
    int countByChannelId(Integer channelId);

    /**
     * 根据 用户ID 查询文章数量
     *
     * @param userId 用户ID
     * @return 文章数量
     */
    int countByUserId(Integer userId);
}