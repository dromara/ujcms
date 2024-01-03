package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.commons.db.tree.TreeEntityMapper;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 栏目 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ChannelMapper extends TreeEntityMapper<Channel> {
    /**
     * 根据主键获取引用对象（不包括关联对象属性）
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Channel selectReferParent(Integer id);

    /**
     * 根据主键获取引用对象（不包括关联对象属性）
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Channel selectRefer(Integer id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo          查询条件
     * @param customsCondition   自定义字段查询条件
     * @param isQueryHasChildren 是否查询包含子栏目
     * @param isOnlyParent       是否只获取父栏目
     * @param articleRoleIds     文章角色IDs
     * @return 数据列表
     */
    List<Channel> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                            @Nullable @Param("customsCondition") List<QueryInfo.WhereCondition> customsCondition,
                            @Param("isQueryHasChildren") boolean isQueryHasChildren,
                            @Param("isOnlyParent") boolean isOnlyParent,
                            @Nullable @Param("articleRoleIds") Collection<Integer> articleRoleIds);

    /**
     * 根据 模型ID 查询栏目数量
     *
     * @param modelId 模型ID
     * @return 栏目数量
     */
    int existsByModelId(Integer modelId);

    /**
     * 根据 栏目别名 查询栏目数量
     *
     * @param alias  栏目别名
     * @param siteId 站点ID
     * @return 栏目数量
     */
    int existsByAlias(@Param("alias") String alias, @Param("siteId") Integer siteId);

    /**
     * 根据 角色ID列表 查询文章权限是否存在
     *
     * @param channelId 栏目ID
     * @param roleIds   角色ID列表
     * @return 1:存在; 0:不存在
     */
    int existsByArticleRoleId(@Param("channelId") Integer channelId, @Param("roleIds") Collection<Integer> roleIds);

    /**
     * 统计栏目数量
     *
     * @param siteId  站点ID
     * @param created 创建日期
     * @return 栏目数量
     */
    int countByCreated(@Param("siteId") Integer siteId, @Param("created") @Nullable OffsetDateTime created);

    /**
     * 根据上级ID获取第一个子栏目
     *
     * @param parentId 上级栏目ID
     * @return 第一个子栏目
     */
    @Nullable
    Channel findFirstByParentId(Integer parentId);

    /**
     * 根据父栏目ID获取子栏目列表
     *
     * @param siteId 站点ID
     * @param alias  栏目别名
     * @return 栏目列表
     */
    List<Channel> findBySiteIdAndAlias(@Param("siteId") Integer siteId, @Param("alias") String alias);

    /**
     * 根据父栏目ID获取子栏目列表
     *
     * @param parentId 父栏目ID
     * @return 子栏目列表
     */
    List<Channel> listChildren(Integer parentId);

    /**
     * 根据父栏目ID获取子栏目列表（不包括关联对象属性）
     *
     * @param parentId 父栏目ID
     * @return 子栏目列表
     */
    List<Channel> listChildrenLink(Integer parentId);

    /**
     * 查询栏目用于sitemap。sitemap查询的数据量较大，只获取必须的字段。
     *
     * @param id 栏目ID
     * @return 栏目对象
     */
    @Nullable
    Channel selectForSitemap(Integer id);

    /**
     * 查询栏目用于sitemap。sitemap查询的数据量较大，只获取必须的字段。
     *
     * @param siteId 站点ID
     * @return 栏目列表
     */
    List<Channel> listByChannelForSitemap(Integer siteId);

    /**
     * 设置绩效类型ID为NULL
     *
     * @param performanceTypeId 绩效类型ID
     * @return 更新条数
     */
    int updatePerformanceTypeIdToNull(@Param("performanceTypeId") Integer performanceTypeId);

    /**
     * 根据站点ID设置父栏目ID为NULL
     *
     * @param siteId 站点ID
     * @return 更新条数
     */
    int updateParentIdToNull(Integer siteId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Integer siteId);
}