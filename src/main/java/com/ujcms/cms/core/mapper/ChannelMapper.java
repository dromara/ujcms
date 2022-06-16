package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Org;
import com.ujcms.util.db.tree.TreeEntityMapper;
import com.ujcms.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

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
     * 根据查询条件获取列表
     *
     * @param queryInfo        查询条件
     * @param customsCondition 自定义字段查询条件
     * @return 数据列表
     */
    List<Channel> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                            @Nullable @Param("customsCondition") List<QueryInfo.WhereCondition> customsCondition);

    /**
     * 根据 模型ID 查询栏目数量
     *
     * @param modelId 模型ID
     * @return 栏目数量
     */
    int countByModelId(Integer modelId);

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
     * @param parentId 父栏目ID
     * @return 子栏目列表
     */
    List<Channel> listChildren(Integer parentId);

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