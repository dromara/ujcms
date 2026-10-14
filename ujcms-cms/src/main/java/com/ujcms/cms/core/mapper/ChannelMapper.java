package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.common.db.tree.TreeEntityMapper;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
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
    Channel selectReferParent(Long id);

    /**
     * 根据主键获取引用对象（不包括关联对象属性）
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Channel selectRefer(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo          查询条件
     * @param isQueryHasChildren 是否查询包含子栏目
     * @param articleRoleIds     文章权限角色ID列表
     * @param articleOrgIds      文章权限组织ID列表
     * @return 数据列表
     */
    List<Channel> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
            @Param("isQueryHasChildren") boolean isQueryHasChildren,
            @Nullable @Param("articleRoleIds") Collection<Long> articleRoleIds,
            @Nullable @Param("articleOrgIds") Collection<Long> articleOrgIds);

    /**
     * 根据 模型ID 查询栏目数量
     *
     * @param modelId 模型ID
     * @return 栏目数量
     */
    int existsByModelId(Long modelId);

    /**
     * 根据 栏目别名 查询栏目数量
     *
     * @param slug   栏目别名
     * @param siteId 站点ID
     * @return 栏目数量
     */
    int existsBySlug(@Param("slug") String slug, @Param("siteId") Long siteId);

    /**
     * 根据 栏目别名 查询栏目ID。别名唯一性校验用：只取 ID，不装配关联对象
     *
     * @param siteId 站点ID
     * @param slug   栏目别名
     * @return 栏目ID。不存在则返回 {@code null}
     */
    @Nullable
    Long selectIdBySlug(@Param("siteId") Long siteId, @Param("slug") String slug);

    /**
     * 同 {@link #selectIdBySlug}，锁定读（{@code select … for update}）：生成别名时查重用
     * （docs/spec/article-data-permission/plan.md D22），须在站点行锁之下、一个已开启的事务里调用
     */
    @Nullable
    Long selectIdBySlugForUpdate(@Param("siteId") Long siteId, @Param("slug") String slug);

    /**
     * 查询栏目及其所有后代栏目的ID（按闭包表，含自身）
     *
     * @param ancestorId 栏目ID
     * @return 栏目ID列表
     */
    List<Long> listIdsByAncestorId(@Param("ancestorId") Long ancestorId);

    /**
     * 根据 角色ID列表 查询文章权限是否存在
     *
     * @param channelId 栏目ID
     * @param roleIds   角色ID列表
     * @return 1:存在; 0:不存在
     */
    int existsByArticleRoleId(@Param("channelId") Long channelId, @Param("roleIds") Collection<Long> roleIds);

    /**
     * 统计栏目数量
     *
     * @param siteId  站点ID
     * @param created 创建日期
     * @return 栏目数量
     */
    int countByCreated(@Param("siteId") Long siteId, @Param("created") @Nullable OffsetDateTime created);

    /**
     * 统计栏目模板选择（{@code channel_template_}）非空且不在给定模板名集合内的栏目数
     * （主题切换影响报告的孤立模板选择计数，template-track-variants D7）。
     *
     * @param siteId 站点ID
     * @param values 目标主题对模板名集合（须非空，空集合由调用方短路）
     */
    int countChannelTemplateNotIn(@Param("siteId") Long siteId, @Param("values") Collection<String> values);

    /**
     * 统计栏目的文章模板默认选择（{@code article_template_}）非空且不在给定模板名集合内的栏目数。
     *
     * @param siteId 站点ID
     * @param values 目标主题对模板名集合（须非空，空集合由调用方短路）
     */
    int countArticleTemplateNotIn(@Param("siteId") Long siteId, @Param("values") Collection<String> values);

    /**
     * 根据上级ID获取第一个子栏目
     *
     * @param parentId 上级栏目ID
     * @return 第一个子栏目
     */
    @Nullable
    Channel findFirstByParentId(Long parentId);

    /**
     * 根据 父栏目ID 获取子栏目列表
     *
     * @param siteId 站点ID
     * @param slug   栏目别名
     * @return 栏目列表
     */
    List<Channel> findBySiteIdAndSlug(@Param("siteId") Long siteId, @Param("slug") String slug);

    /**
     * 获取栏目权限的 栏目ID 列表
     *
     * @param roleIds 角色ID 列表
     * @param orgIds  组织ID 列表
     * @param siteId  站点ID
     * @return 栏目ID 列表
     */
    List<Long> listChannelPermissions(@Param("roleIds") Collection<Long> roleIds,
            @Param("orgIds") Collection<Long> orgIds, @Param("siteId") @Nullable Long siteId);

    /**
     * 获取文章权限的 栏目ID 列表
     *
     * @param roleIds 角色ID 列表
     * @param orgIds  组织ID 列表
     * @param siteId  站点ID
     * @return 栏目ID 列表
     */
    List<Long> listArticlePermissions(@Param("roleIds") Collection<Long> roleIds,
            @Param("orgIds") Collection<Long> orgIds, @Param("siteId") @Nullable Long siteId);

    /**
     * 根据父栏目ID获取子栏目列表
     *
     * @param parentId 父栏目ID
     * @return 子栏目列表
     */
    List<Channel> listChildren(Long parentId);

    /**
     * 根据父栏目ID获取子栏目列表（不包括关联对象属性）
     *
     * @param parentId 父栏目ID
     * @return 子栏目列表
     */
    List<Channel> listChildrenLink(Long parentId);

    /**
     * 查询栏目用于sitemap。sitemap查询的数据量较大，只获取必须的字段。
     *
     * @param siteId 站点ID
     * @return 栏目列表
     */
    List<Channel> listByChannelForSitemap(Long siteId);

    /**
     * 根据站点ID查询栏目。用于整理树形结构，只获取必须字段
     *
     * @param siteId 站点ID
     * @return 栏目列表
     */
    List<Channel> listBySiteIdForTidy(Long siteId);

    /**
     * 设置是否导航
     *
     * @param ids 待设置的栏目ID
     * @param nav 是否导航
     * @return 更新条数
     */
    int updateNav(@Param("ids") List<Long> ids, @Param("nav") Boolean nav);

    /**
     * 设置是否文章栏目
     *
     * @param ids  待设置的栏目ID
     * @param real 是否文章栏目
     * @return 更新条数
     */
    int updateReal(@Param("ids") List<Long> ids, @Param("real") Boolean real);

    /**
     * 批量设置：只改写 {@code fields} 列出的列，并更新修改时间；其余各列（含自定义字段）不动
     *
     * @param siteId   站点ID。只更新本站的栏目
     * @param ids      待设置的栏目ID
     * @param fields   要改写的属性名，如 {@code processKey}、{@code pageSize}
     * @param bean     承载新值的栏目对象：只读取 {@code fields} 列出的属性，值为 {@code null} 即清空该列
     * @param modified 修改时间
     * @return 更新条数
     */
    int updateSettings(@Param("siteId") Long siteId, @Param("ids") Collection<Long> ids,
                       @Param("fields") Collection<String> fields, @Param("bean") Channel bean,
                       @Param("modified") OffsetDateTime modified);

    /**
     * 给定栏目中，属于该站点的栏目ID
     *
     * @param siteId 站点ID
     * @param ids    栏目ID
     * @return 属于该站点的栏目ID
     */
    List<Long> listIdsInSite(@Param("siteId") Long siteId, @Param("ids") Collection<Long> ids);

    /**
     * 给定栏目中，已有文章（任何状态）且文章模型不是 {@code articleModelId} 的栏目ID。
     * 改换这些栏目的文章模型，文章的自定义字段会在下次保存时丢失
     *
     * @param ids            栏目ID
     * @param articleModelId 文章模型ID
     * @return 栏目ID，按栏目顺序
     */
    List<Long> listIdsHoldingArticlesUnderOtherModel(@Param("ids") Collection<Long> ids,
                                                     @Param("articleModelId") Long articleModelId);

    /**
     * 设置绩效类型ID为NULL
     *
     * @param performanceTypeId 绩效类型ID
     * @return 更新条数
     */
    int updatePerformanceTypeIdToNull(@Param("performanceTypeId") Long performanceTypeId);

    /**
     * 根据站点ID设置父栏目ID为NULL
     *
     * @param siteId 站点ID
     * @return 更新条数
     */
    int updateParentIdToNull(Long siteId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 按来源标记获取栏目（内容种子导入幂等：select-then-insert，theme-marketplace §5.4）。
     * 站点限定：{@code origin_} 不含站点，同名主题可装于多个站点。
     *
     * @param siteId   站点ID
     * @param origin   来源（{@code theme:<themeName>}）
     * @param originId 种子文件内的稳定ID
     * @return 实体对象
     */
    @Nullable
    Channel selectByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin,
                           @Param("originId") String originId);

    /**
     * 按来源标记获取本站栏目列表（卸载/移除演示数据策略用，站点限定缘由见 {@link #selectByOrigin}）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 栏目列表
     */
    List<Channel> listByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 按来源标记统计本站栏目数（影响报告 {@code demoDataPresent}，§6.2）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 行数
     */
    int countByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 清除本站来源标记（卸载“保留”策略：主题来源栏目转为普通操作员栏目）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 更新条数
     */
    int clearOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 写入来源散列（R4：种子导入落库后，以持久化的最终行状态计算——服务层保存会清洗字段值，
     * 散列必须与行内实际存储一致才能据以重导出比对）
     *
     * @param id         栏目ID
     * @param originHash 种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int updateOriginHash(@Param("id") Long id, @Param("originHash") String originHash);

    /**
     * 认领操作员行（theme-seed-export §4.2 adopt 模式，D2 唯一豁免）：写入铸造的来源三元组。
     * 仅无来源标记的行可认领（SQL 内附带条件）——他源行绝不认领（D3），并发下亦然。
     *
     * @param id         栏目ID
     * @param origin     来源（{@code theme:<themeName>}）
     * @param originId   铸造的 originId
     * @param originHash 当前种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int adoptOrigin(@Param("id") Long id, @Param("origin") String origin,
                    @Param("originId") String originId, @Param("originHash") String originHash);
}