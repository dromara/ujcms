package com.ujcms.cms.core.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.ujcms.cms.core.domain.Topic;
import com.ujcms.common.db.tree.TreeEntityMapper;
import com.ujcms.common.query.QueryInfo;

/**
 * 专题 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface TopicMapper extends TreeEntityMapper<Topic> {
    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo          查询条件
     * @param ancestorId         上级ID
     * @param isQueryHasChildren 是否查询包含子专题
     * @return 数据列表
     */
    List<Topic> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                          @Nullable @Param("ancestorId") Long ancestorId,
                          @Param("isQueryHasChildren") boolean isQueryHasChildren);

    /**
     * 根据站点ID和别名获取专题
     *
     * @param siteId 站点ID
     * @param slug   别名
     * @return 专题对象。没有找到数据则返回 {@code null}
     */
    @Nullable
    Topic findBySiteIdAndSlug(@Param("siteId") Long siteId, @Param("slug") String slug);

    /**
     * 占用该别名的站内专题ID，锁定读（{@code select … for update}）：生成别名时查重用
     * （docs/spec/article-data-permission/plan.md D22），须在站点行锁之下、一个已开启的事务里调用
     */
    @Nullable
    Long selectIdBySlugForUpdate(@Param("siteId") Long siteId, @Param("slug") String slug);

    /**
     * 根据站点ID获取专题列表
     *
     * @param siteId 站点ID
     * @return 专题列表
     */
    List<Topic> listBySiteId(@Param("siteId") Long siteId);

    /**
     * 根据父专题ID获取子专题列表
     *
     * @param parentId 父专题ID
     * @return 子专题列表
     */
    List<Topic> listChildren(@Param("parentId") Long parentId);

    /**
     * 根据站点ID查询专题。用于整理树形结构，只获取必须字段
     *
     * @param siteId 站点ID
     * @return 专题列表
     */
    List<Topic> listBySiteIdForTidy(@Param("siteId") Long siteId);

    /**
     * 根据 别名 查询专题数量
     *
     * @param slug   别名
     * @param siteId 站点ID
     * @return 专题数量
     */
    int existsBySlug(@Param("slug") String slug, @Param("siteId") Long siteId);

    /**
     * 根据 模型ID 查询专题数量
     *
     * @param modelId 模型ID
     * @return 专题数量
     */
    int existsByModelId(@Param("modelId") Long modelId);

    /**
     * 统计专题模板选择（{@code topic_template_}）非空且不在给定模板名集合内的专题数
     * （主题切换影响报告的孤立模板选择计数，template-track-variants D7）。
     *
     * @param siteId 站点ID
     * @param values 目标主题对模板名集合（须非空，空集合由调用方短路）
     */
    int countTopicTemplateNotIn(@Param("siteId") Long siteId, @Param("values") Collection<String> values);

    /**
     * 根据站点ID设置父专题ID为NULL
     *
     * @param siteId 站点ID
     * @return 更新条数
     */
    int updateParentIdToNull(@Param("siteId") Long siteId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(@Param("siteId") Long siteId);

    /**
     * 批量累加专题浏览次数。直接更新 {@code ujcms_topic} 的 {@code views_} /
     * {@code self_views_}（专题流量低，无独立 buffer 表）。
     *
     * @param list 仅需 {@code id} / {@code views} / {@code selfViews} 字段
     */
    void updateViewsBatch(List<Topic> list);

    /**
     * 按来源标记获取专题（内容种子导入幂等：select-then-insert，theme-marketplace §5.4）。
     * 站点限定：{@code origin_} 不含站点，同名主题可装于多个站点。
     *
     * @param siteId   站点ID
     * @param origin   来源（{@code theme:<themeName>}）
     * @param originId 种子文件内的稳定ID
     * @return 实体对象
     */
    @Nullable
    Topic selectByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin,
                         @Param("originId") String originId);

    /**
     * 按来源标记获取本站专题列表（卸载/移除演示数据策略用，站点限定缘由见 {@link #selectByOrigin}）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 专题列表
     */
    List<Topic> listByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 按来源标记统计本站专题数（影响报告 {@code demoDataPresent}，§6.2）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 行数
     */
    int countByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 清除本站来源标记（卸载“保留”策略：主题来源专题转为普通操作员专题）
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
     * @param id         专题ID
     * @param originHash 种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int updateOriginHash(@Param("id") Long id, @Param("originHash") String originHash);

    /**
     * 认领操作员行（theme-seed-export §4.2 adopt 模式，D2 唯一豁免）：写入铸造的来源三元组。
     * 仅无来源标记的行可认领（SQL 内附带条件）——他源行绝不认领（D3），并发下亦然。
     *
     * @param id         专题ID
     * @param origin     来源（{@code theme:<themeName>}）
     * @param originId   铸造的 originId
     * @param originHash 当前种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int adoptOrigin(@Param("id") Long id, @Param("origin") String origin,
                    @Param("originId") String originId, @Param("originHash") String originHash);
}
