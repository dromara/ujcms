package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleExportRow;
import com.ujcms.cms.core.domain.ArticleTagName;
import com.ujcms.cms.core.domain.ArticleStatusCount;
import com.ujcms.cms.core.domain.ArticlePlacement;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.common.db.order.OrderEntityMapper;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 文章 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ArticleMapper extends OrderEntityMapper {
    /**
     * 根据主键获取引用对象（不包括关联对象属性）
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Article selectRefer(Long id);

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
    int delete(Long id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    @Override
    Article select(Long id);

    /**
     * 对文章行加行锁（{@code select … for update}）：写入其落位（推送、扩展栏目同步）前先执行，使并发的两个请求
     * 依次进行（docs/spec/article-data-permission/plan.md D17）。须在一个已开启的事务里调用。
     *
     * @param id 文章主键ID
     * @return 行存在时返回其ID，否则 {@code null}
     */
    @Nullable
    Long lockById(@Param("id") Long id);

    /**
     * 对一批文章行加行锁（{@code select … for update}），按ID升序：与 {@link #lockById} 同一把锁，供栏目合并锁住
     * 落位家族的源文章（docs/spec/article-data-permission/plan.md D21）。须在一个已开启的事务里调用
     *
     * @return 存在的那些ID，升序
     */
    List<Long> lockByIds(@Param("ids") Collection<Long> ids);

    /**
     * 读取并锁住这些栏目里的全部文章落位（{@code select … for update}）：锁定读读到的是最新提交的行，不是事务快照。
     * 栏目合并前核对落位家族用（docs/spec/article-data-permission/plan.md D21）。须在一个已开启的事务里调用
     */
    List<ArticlePlacement> lockPlacementsInChannels(@Param("channelIds") Collection<Long> channelIds);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo          查询条件
     * @param channelAncestorIds 栏目ID列表
     * @param orgIds             组织ID列表
     * @param articleRoleIds     文章权限角色ID列表
     * @param articleOrgIds      文章权限组织ID列表
     * @param orgRoleIds         组织权限角色ID列表
     * @param orgPermIds         组织权限组织ID列表
     * @return 数据列表
     */
    List<Article> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                            @Nullable @Param("channelAncestorIds") Collection<Long> channelAncestorIds,
                            @Nullable @Param("orgIds") Collection<Long> orgIds,
                            @Nullable @Param("articleRoleIds") Collection<Long> articleRoleIds,
                            @Nullable @Param("articleOrgIds") Collection<Long> articleOrgIds,
                            @Nullable @Param("orgRoleIds") Collection<Long> orgRoleIds,
                            @Nullable @Param("orgPermIds") Collection<Long> orgPermIds);

    /**
     * 根据 id 列表查询
     *
     * @param ids id 列表
     * @return 数据列表
     */
    List<Article> listByIds(@Param("ids") Iterable<Long> ids);

    /**
     * 站点可收录文章列表，用于 sitemap（docs/spec/sitemap/plan.md）。数据量大，只查询组装 URL 与 lastmod 所需字段；
     * 状态与非链接条件在 SQL 内过滤，按 id_ 升序，调用方用 PageHelper offsetPage 分页。
     *
     * @param siteId 站点ID
     * @param status 可收录状态（已发布、已归档）
     * @return 文章列表（含 channel.alias、ext.alias、ext.linkUrl、created、modified）
     */
    List<Article> listBySiteIdForSitemap(@Param("siteId") Long siteId, @Param("status") Collection<Short> status);

    /**
     * 站点全部可收录文章的修改时间，按 id_ 升序。sitemap 索引文件据此计算页数与每页 lastmod。
     *
     * @param siteId 站点ID
     * @param status 可收录状态
     * @return 修改时间列表（与 {@link #listBySiteIdForSitemap} 同序）
     */
    List<OffsetDateTime> listModifiedForSitemap(@Param("siteId") Long siteId, @Param("status") Collection<Short> status);

    /**
     * 各栏目（含祖先栏目）子树内最新可收录文章的修改时间，用于栏目 sitemap 的 lastmod。
     *
     * @param siteId 站点ID
     * @param status 可收录状态
     * @return 以 {@link Channel} 为载体的列表：id 为栏目ID，modified 为该栏目子树内最新文章修改时间
     */
    List<Channel> listChannelModifiedForSitemap(@Param("siteId") Long siteId, @Param("status") Collection<Short> status);

    /**
     * 根据栏目ID查询文章列表
     *
     * @param channelId 栏目ID
     * @return 文章列表
     */
    List<Article> listByChannelId(@Param("channelId") Long channelId);

    /**
     * 根据源文章ID查询文章列表
     *
     * @param srcId 源文章ID
     * @return 文章列表
     */
    List<Article> listBySrcId(@Param("srcId") Long srcId);

    /**
     * 根据源文章ID聚合扩展栏目（即此文章 REFER/COPY 子行所在的栏目去重列表）
     *
     * @param srcId 源文章ID
     * @return 栏目列表
     */
    List<Channel> listChannelsBySrcId(@Param("srcId") Long srcId);

    /**
     * 根据专题ID查询专题下的文章列表（按 ujcms_article_topic.order_ 升序）。
     * 仅返回指定状态（通常为已发布或已归档）的文章。
     *
     * @param topicId 专题ID
     * @param status  允许的状态集合
     * @return 文章列表
     */
    List<Article> listByTopicId(@Param("topicId") Long topicId,
                                @Param("status") Collection<Short> status);

    /**
     * 查询下一条文章
     *
     * @param id        文章ID
     * @param order     文章排序值
     * @param channelId 栏目ID
     * @param status    状态
     * @return 下一条文章
     */
    List<Article> findNext(@Param("id") Long id, @Param("order") Long order,
                           @Param("channelId") Long channelId, @Param("status") Collection<Short> status);

    /**
     * 查询上一条文章
     *
     * @param id        文章ID
     * @param order     文章排序值
     * @param channelId 栏目ID
     * @param status    状态
     * @return 上一条文章
     */
    List<Article> findPrev(@Param("id") Long id, @Param("order") Long order,
                           @Param("channelId") Long channelId, @Param("status") Collection<Short> status);

    /**
     * 根据 栏目ID 查询文章数量
     *
     * @param channelId 栏目ID
     * @return 文章数量
     */
    int existsByChannelId(@Param("channelId") Long channelId);

    /**
     * 栏目及其后代栏目下是否存在文章（按闭包表）。删除整棵栏目子树前的预检
     *
     * @param channelId 栏目ID
     * @return 1:存在; 0:不存在
     */
    int existsByChannelAncestorId(@Param("channelId") Long channelId);

    /**
     * 根据 用户ID 查询文章是否存在
     *
     * @param userId 用户ID
     * @return 文章是否存在。0代表不存在，1代表存在
     */
    int existsByUserId(Long userId);

    /**
     * 根据 标题 和 站点ID 查询文章是否存在
     *
     * @param title  标题
     * @param siteId 站点ID
     * @return 文章是否存在。0代表不存在，1代表存在
     */
    int existsByTitleAndSiteId(@Param("title") String title, @Param("siteId") Long siteId);

    /**
     * 统计文章模板选择（{@code article_template_}，扩展表）非空且不在给定模板名集合内的文章数
     * （主题切换影响报告的孤立模板选择计数，template-track-variants D7；不含回收站）。
     *
     * @param siteId 站点ID
     * @param values 目标主题对模板名集合（须非空，空集合由调用方短路）
     */
    int countArticleTemplateNotIn(@Param("siteId") Long siteId, @Param("values") Collection<String> values);

    /**
     * 统计文章数量
     *
     * @param siteId      站点ID
     * @param publishDate 发布日期
     * @param status      状态
     * @return 文章数量
     */
    int countByPublishDate(@Param("siteId") Long siteId, @Param("publishDate") @Nullable OffsetDateTime publishDate,
                           @Param("status") Collection<Short> status);

    /**
     * 更新栏目ID
     *
     * @param fromChannelId 原栏目ID
     * @param toChannelId   新栏目ID
     * @return 被更新的条数
     */
    int updateChannelId(@Param("fromChannelId") Long fromChannelId, @Param("toChannelId") Long toChannelId);

    /**
     * 更新修改人员
     *
     * @param fromUserId 原修改用户ID
     * @param toUserId   新修改用户ID
     * @return 被更新的条数
     */
    int updateModifiedUser(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId);

    /**
     * 更新置顶日期。所有已到期的置顶文章，将置顶设置成`0`，并将置顶日期设置为`null`
     *
     * @param now 当前时间
     * @return 被更新的条数
     */
    int updateStickyDate(OffsetDateTime now);

    /**
     * 只更新状态（列定向写入，不回写整行）
     *
     * @param id     文章ID
     * @param status 状态
     * @return 更新条数
     */
    int updateStatus(@Param("id") Long id, @Param("status") short status);

    /**
     * 只更新置顶与置顶截止时间
     *
     * @param id         文章ID
     * @param sticky     置顶值，0 为不置顶
     * @param stickyDate 置顶截止时间，可为 {@code null}
     * @return 更新条数
     */
    int updateSticky(@Param("id") Long id, @Param("sticky") short sticky,
                     @Param("stickyDate") @Nullable OffsetDateTime stickyDate);

    /**
     * 解除与源文章的关联（彻底删除源文章时，复制类子行自此独立）
     *
     * @param id 文章ID
     * @return 更新条数
     */
    int updateSrcIdToNull(@Param("id") Long id);

    /**
     * 只更新所在栏目（批量移动栏目）
     *
     * @param id        文章ID
     * @param channelId 栏目ID
     * @return 更新条数
     */
    int updateChannel(@Param("id") Long id, @Param("channelId") Long channelId);

    /**
     * 只取主键。条件、排序与 {@link #selectAll} 相同：文章导出按列表的顺序分批取 ID
     */
    List<Long> selectIds(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                         @Nullable @Param("channelAncestorIds") Collection<Long> channelAncestorIds,
                         @Nullable @Param("orgIds") Collection<Long> orgIds,
                         @Nullable @Param("articleRoleIds") Collection<Long> articleRoleIds,
                         @Nullable @Param("articleOrgIds") Collection<Long> articleOrgIds,
                         @Nullable @Param("orgRoleIds") Collection<Long> orgRoleIds,
                         @Nullable @Param("orgPermIds") Collection<Long> orgPermIds);

    /**
     * 导出用的行：文章、扩展表、组织、用户的列一次取出，不含正文
     *
     * @param ids 文章ID，不可为空
     */
    List<ArticleExportRow> listForExport(@Param("ids") Collection<Long> ids);

    /**
     * 这些标题里，本站已有文章用了的那些。回收站里的、推送与扩展栏目生成的子行不算。文章批量导入查重用
     *
     * @param titles 标题，不可为空
     */
    List<String> listExistingTitles(@Param("siteId") Long siteId, @Param("titles") Collection<String> titles);

    /**
     * 一批文章的 TAG 名称，按文章内的顺序
     *
     * @param ids 文章ID，不可为空
     */
    List<ArticleTagName> listTagNames(@Param("ids") Collection<Long> ids);

    /**
     * 各状态的文章数。条件与 {@link #selectAll} 相同（查询键、栏目、数据权限），按状态汇总
     *
     * @return 有文章的各状态及其文章数
     */
    List<ArticleStatusCount> countByStatus(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                                           @Nullable @Param("channelAncestorIds") Collection<Long> channelAncestorIds,
                                           @Nullable @Param("orgIds") Collection<Long> orgIds,
                                           @Nullable @Param("articleRoleIds") Collection<Long> articleRoleIds,
                                           @Nullable @Param("articleOrgIds") Collection<Long> articleOrgIds,
                                           @Nullable @Param("orgRoleIds") Collection<Long> orgRoleIds,
                                           @Nullable @Param("orgPermIds") Collection<Long> orgPermIds);

    /**
     * 更新文章的上线状态。状态为`ready`，并且已到上线日期的文章，更新为`published`
     *
     * @param now       当前时间
     * @param ready     待发布状态码
     * @param published 已发布状态码
     * @return 被更新的条数
     */
    int updateOnlineStatus(@Param("now") OffsetDateTime now,
                           @Param("ready") short ready, @Param("published") short published);

    /**
     * 更新文章的上线状态。状态为`ready`，并且已到上线日期的文章，更新为`published`
     *
     * @param now     当前时间
     * @param normals 已发布和已归档状态码
     * @param offline 已下线状态码
     * @return 被更新的条数
     */
    int updateOfflineStatus(@Param("now") OffsetDateTime now,
                            @Param("normals") Collection<Short> normals, @Param("offline") short offline);

    /**
     * 根据栏目ID删除数据
     *
     * @param channelId 栏目ID
     * @return 被删除的数据条数
     */
    int deleteByChannelId(Long channelId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 根据站点ID清空文章的 srcId（站点删除级联：src_id_ 自引用外键在整站单语句删除时
     * 可能先删父行后删子行而触发约束失败，须先解除引用；引用/复制子行与源文章同站点，整站删除下一并清除）
     *
     * @param siteId 站点ID
     * @return 被更新的条数
     */
    int updateSrcIdToNullBySiteId(Long siteId);

    /**
     * 按来源标记获取文章（内容种子导入幂等：select-then-insert，theme-marketplace §5.4；
     * 卡片 {@code {{article:<originId>}}} 令牌解析）。站点限定：{@code origin_} 不含站点，
     * 同名主题可装于多个站点。
     *
     * @param siteId   站点ID
     * @param origin   来源（{@code theme:<themeName>}）
     * @param originId 种子文件内的稳定ID
     * @return 实体对象
     */
    @Nullable
    Article selectByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin,
                           @Param("originId") String originId);

    /**
     * 按来源标记获取本站文章列表（卸载/移除演示数据策略用，站点限定缘由见 {@link #selectByOrigin}）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 文章列表
     */
    List<Article> listByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 按来源标记统计本站文章数（影响报告 {@code demoDataPresent} / 移除演示数据干跑，§6.2）
     *
     * @param siteId 站点ID
     * @param origin 来源（{@code theme:<themeName>}）
     * @return 行数
     */
    int countByOrigin(@Param("siteId") Long siteId, @Param("origin") String origin);

    /**
     * 统计栏目下的文章总数（任意状态，含回收站——移除演示数据的「栏目是否为空」守卫，§6.1）
     *
     * @param channelId 栏目ID
     * @return 行数
     */
    int countByChannelId(@Param("channelId") Long channelId);

    /**
     * 清除本站来源标记（卸载“保留”策略：主题来源文章转为普通操作员文章）
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
     * @param id         文章ID
     * @param originHash 种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int updateOriginHash(@Param("id") Long id, @Param("originHash") String originHash);

    /**
     * 根据 站点ID 与 别名 查询文章（固定链接解析，docs/spec/permalinks/plan.md D7）。
     * (site_id_, slug_) 唯一，调用方以 offsetPage(0,1) 取首行
     *
     * @param siteId 站点ID
     * @param slug   别名
     * @return 文章列表（至多一条）
     */
    List<Article> findBySiteIdAndSlug(@Param("siteId") Long siteId, @Param("slug") String slug);

    /**
     * 别名是否已被站内文章占用
     *
     * @param slug      别名
     * @param siteId    站点ID
     * @param excludeId 排除的文章ID（更新时排除自身），可为 {@code null}
     * @return 占用返回 1，否则 0
     */
    int existsBySlug(@Param("slug") String slug, @Param("siteId") Long siteId, @Param("excludeId") @Nullable Long excludeId);

    /**
     * 占用该别名的站内文章ID，锁定读（{@code select … for update}）：读到的是最新提交的行，不是本事务的快照。
     * 生成别名时查重用（docs/spec/article-data-permission/plan.md D22），须在站点行锁之下、一个已开启的事务里调用
     *
     * @param excludeId 排除的文章ID（更新时排除自身），可为 {@code null}
     */
    List<Long> listIdsBySlugForUpdate(@Param("slug") String slug, @Param("siteId") Long siteId,
                                      @Param("excludeId") @Nullable Long excludeId);

    /**
     * 认领操作员行（theme-seed-export §4.2 adopt 模式，D2 唯一豁免）：写入铸造的来源三元组。
     * 仅无来源标记的行可认领（SQL 内附带条件）——他源行绝不认领（D3），并发下亦然。
     *
     * @param id         文章ID
     * @param origin     来源（{@code theme:<themeName>}）
     * @param originId   铸造的 originId
     * @param originHash 当前种子形投影散列（SHA-256 十六进制）
     * @return 更新条数
     */
    int adoptOrigin(@Param("id") Long id, @Param("origin") String origin,
                    @Param("originId") String originId, @Param("originHash") String originHash);
}