package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleI18n;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 文章翻译等价集 Mapper（multilingual P5）。命名空间挂共享 SiteCache 二级缓存：
 * 读路径随文章页渲染高频执行，写路径为低频后台操作（刷新共享缓存的成本与站点/栏目写一致）。
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ArticleI18nMapper {
    /**
     * 获取文章的翻译组成员行
     *
     * @param articleId 文章ID
     * @return 成员行。文章未入组则返回 {@code null}
     */
    @Nullable
    ArticleI18n select(Long articleId);

    /**
     * 插入成员行
     *
     * @param bean 成员行
     * @return 插入条数
     */
    int insert(ArticleI18n bean);

    /**
     * 更新成员的已同步锚点（追赶保存 / 草稿保存 / AI 填充标记）
     *
     * @param articleId  文章ID
     * @param syncedDate 已同步至此时间
     * @return 更新条数
     */
    int updateSyncedDate(@Param("articleId") Long articleId, @Param("syncedDate") OffsetDateTime syncedDate);

    /**
     * 更新成员的双锚点（原创编辑保存）
     *
     * @param articleId   文章ID
     * @param contentDate 最后原创内容修改时间
     * @param syncedDate  已同步至此时间
     * @return 更新条数
     */
    int updateAnchors(@Param("articleId") Long articleId, @Param("contentDate") OffsetDateTime contentDate,
                      @Param("syncedDate") OffsetDateTime syncedDate);

    /**
     * 删除文章的成员行（解除链接 / 文章删除清理）
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(Long articleId);

    /**
     * 根据站点ID删除成员行（站点删除级联；其它站点成员行不受影响，翻译组照常存续）
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 批量获取成员行（sitemap 分页批量查询用）
     *
     * @param articleIds 文章ID集合
     * @return 成员行列表
     */
    List<ArticleI18n> listByArticleIds(@Param("articleIds") Collection<Long> articleIds);

    /**
     * 获取多个翻译组的全部成员行
     *
     * @param groupIds 翻译组ID集合
     * @return 成员行列表
     */
    List<ArticleI18n> listByGroupIds(@Param("groupIds") Collection<Long> groupIds);

    /**
     * 站点内批量成员行（文章列表过期徽标用；按站点过滤，避免探测他站文章的入组状态）
     *
     * @param articleIds 文章ID集合
     * @param siteId     站点ID
     * @return 成员行列表
     */
    List<ArticleI18n> listBySiteArticleIds(@Param("articleIds") Collection<Long> articleIds,
                                           @Param("siteId") Long siteId);

    /**
     * 翻译组成员文章（仅 isNormal 状态：已发布/已归档；瘦身结果集，仅够 URL 组装与后台展示）
     *
     * @param groupId 翻译组ID
     * @return 成员文章列表
     */
    List<Article> listArticleByGroupId(Long groupId);

    /**
     * 批量获取多个翻译组的成员文章（sitemap 分页批量查询用；仅 isNormal 状态）
     *
     * @param groupIds 翻译组ID集合
     * @return 成员文章列表
     */
    List<Article> listArticleByGroupIds(@Param("groupIds") Collection<Long> groupIds);

    /**
     * 该文章所在翻译组内、指定站点的成员文章（仅 isNormal 状态）
     *
     * @param articleId 文章ID
     * @param siteId    目标站点ID
     * @return 成员文章。文章未入组或该站点无成员则返回 {@code null}
     */
    @Nullable
    Article findArticleByGroupAndSiteId(@Param("articleId") Long articleId, @Param("siteId") Long siteId);

    /**
     * 状态无关版：该文章所在翻译组内、指定站点的成员文章（含草稿等一切状态）。
     * 不变量检查（一站一员）与控制台读专用；公开读路径（hreflang/sitemap）仍用
     * {@link #findArticleByGroupAndSiteId}
     *
     * @param articleId 文章ID
     * @param siteId    目标站点ID
     * @return 成员文章。文章未入组或该站点无成员则返回 {@code null}
     */
    @Nullable
    Article findMemberArticleByGroupAndSiteId(@Param("articleId") Long articleId, @Param("siteId") Long siteId);

    /**
     * 备选文章查询（翻译选择器；仅 isNormal 状态；按ID倒序）。
     * 配合 PageHelper offsetPage 限制条数
     *
     * @param siteId    站点ID
     * @param channelId 栏目ID。可为 {@code null}
     * @param title     标题（模糊匹配）。可为 {@code null}
     * @return 文章列表（瘦身结果集）
     */
    List<Article> listCandidateArticles(@Param("siteId") Long siteId, @Param("channelId") @Nullable Long channelId,
                                        @Param("title") @Nullable String title);

    /**
     * 未入组匹配文章（批量翻译“先关联已有译文、后新建草稿”用）：目标栏目内、未加入任何翻译组、
     * 非回收站状态（状态无关——草稿译文同样占位）且别名或标题精确相等的文章，按ID升序。
     * 同一依据命中多篇的歧义由 Service 判定
     *
     * @param channelId 目标栏目ID
     * @param slug      源文章别名（仅自定义别名；ID 哨兵值不参与匹配）。可为 {@code null}（仅按标题）
     * @param title     源文章标题（精确相等）
     * @return 文章列表（瘦身结果集）
     */
    List<Article> listUnlinkedMatchArticles(@Param("channelId") Long channelId, @Param("slug") @Nullable String slug,
                                            @Param("title") String title);

    /**
     * 站点内来自主题种子的文章（安装引擎按来源键配对译文用）：状态无关，排除回收站，按ID升序
     *
     * @param siteId 站点ID
     * @return 文章列表（仅 id/siteId/status/modified/originId）
     */
    List<Article> listSeedArticles(Long siteId);
}
