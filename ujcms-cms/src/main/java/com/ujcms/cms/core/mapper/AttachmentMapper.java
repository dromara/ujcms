package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 附件 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface AttachmentMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Attachment bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Attachment bean);

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
    Attachment select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Attachment> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 共享资源库列表。{@code categoryId} 为空时返回站点全部共享附件，否则按分类过滤。
     *
     * @param siteId     站点ID
     * @param categoryId 共享资源库分类ID，可为 {@code null}
     * @param extensions 文件名后缀 LIKE 模式清单（形如 {@code %.jpg}），可为 {@code null} 表示不限类型
     * @return 数据列表
     */
    List<Attachment> selectShared(@Param("siteId") Long siteId, @Param("categoryId") @Nullable Long categoryId,
                                  @Param("keyword") @Nullable String keyword,
                                  @Param("extensions") @Nullable List<String> extensions);

    /**
     * 我的资源库列表（当前用户收藏的附件，按收藏时间倒序）。
     *
     * @param siteId     站点ID
     * @param userId     用户ID
     * @param categoryId 我的资源库分类ID过滤；{@code null} 表示全部收藏，{@code 0} 表示未分类收藏
     * @param extensions 文件名后缀 LIKE 模式清单（形如 {@code %.jpg}），可为 {@code null} 表示不限类型
     * @return 数据列表
     */
    List<Attachment> selectByFavorite(@Param("siteId") Long siteId, @Param("userId") Long userId,
                                      @Param("categoryId") @Nullable Long categoryId,
                                      @Param("keyword") @Nullable String keyword,
                                      @Param("extensions") @Nullable List<String> extensions);

    /**
     * 统计附件数量
     *
     * @param siteId  站点ID
     * @param created 创建日期
     * @return 附件数量
     */
    int countByCreated(@Param("siteId") Long siteId, @Param("created") @Nullable OffsetDateTime created);

    /**
     * 统计某用户自某时刻起上传的附件数（会员上传配额，docs/spec/member-contribute/plan.md D10）。不分站点：会员身份是全局的
     *
     * @param userId  用户ID
     * @param created 创建日期（含）
     * @return 附件数量
     */
    int countByUserCreated(@Param("userId") Long userId, @Param("created") OffsetDateTime created);

    /**
     * 根据 url 附件对象
     *
     * @param url URL
     * @return 附件对象
     */
    @Nullable
    Attachment findByUrl(String url);

    /**
     * 在给定 url 集合中，查询已登记为附件的 url（用于服务器文件导入时过滤已存在的文件）
     *
     * @param urls URL 集合（不可为空）
     * @return 已存在的 url 列表
     */
    List<String> selectExistingUrls(@Param("urls") List<String> urls);

    /**
     * 更新附件是否使用字段
     *
     * @param ids 附件ID集
     * @return 更新条数
     */
    int updateUsed(@Param("ids") Set<Long> ids);

    /**
     * 更新附件的共享资源库分类（加入/移动/移出共享资源库）
     *
     * @param id         附件ID
     * @param categoryId 共享资源库分类ID，{@code null} 表示移出共享资源库
     * @return 更新条数
     */
    int updateSharedCategory(@Param("id") Long id, @Param("categoryId") @Nullable Long categoryId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Long siteId);
}