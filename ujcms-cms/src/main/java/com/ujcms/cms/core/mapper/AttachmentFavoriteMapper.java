package com.ujcms.cms.core.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 我的资源库（用户收藏附件）Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface AttachmentFavoriteMapper {
    /**
     * 添加收藏
     *
     * @param attachmentId 附件ID
     * @param userId       用户ID
     * @param categoryId   我的资源库分类ID，可为 {@code null}（未分类）
     * @param created      收藏时间
     * @return 插入条数
     */
    int insert(@Param("attachmentId") Long attachmentId, @Param("userId") Long userId,
               @Param("categoryId") @Nullable Long categoryId, @Param("created") OffsetDateTime created);

    /**
     * 修改收藏所属分类（我的资源库分组）
     *
     * @param attachmentId 附件ID
     * @param userId       用户ID
     * @param categoryId   我的资源库分类ID，{@code null} 表示未分类
     * @return 更新条数
     */
    int updateCategory(@Param("attachmentId") Long attachmentId, @Param("userId") Long userId,
                       @Param("categoryId") @Nullable Long categoryId);

    /**
     * 统计某个我的资源库分类下的收藏数量（用于删除分类前的非空校验）
     *
     * @param categoryId 我的资源库分类ID
     * @return 收藏数量
     */
    int countByCategory(@Param("categoryId") Long categoryId);

    /**
     * 取消收藏
     *
     * @param attachmentId 附件ID
     * @param userId       用户ID
     * @return 删除条数
     */
    int delete(@Param("attachmentId") Long attachmentId, @Param("userId") Long userId);

    /**
     * 是否已收藏
     *
     * @param attachmentId 附件ID
     * @param userId       用户ID
     * @return 收藏记录数（0 表示未收藏）
     */
    int exists(@Param("attachmentId") Long attachmentId, @Param("userId") Long userId);

    /**
     * 在给定附件ID范围内，查询当前用户已收藏的附件ID（用于列表填充收藏标记）
     *
     * @param userId        用户ID
     * @param attachmentIds 附件ID范围（不可为空）
     * @return 已收藏的附件ID列表
     */
    List<Long> listAttachmentIdsByUser(@Param("userId") Long userId,
                                       @Param("attachmentIds") Collection<Long> attachmentIds);

    /**
     * 根据附件ID删除收藏（附件被删除时调用）
     *
     * @param attachmentId 附件ID
     * @return 删除条数
     */
    int deleteByAttachmentId(Long attachmentId);

    /**
     * 某用户收藏的全部附件ID（用户删除时用于回收 pin 状态）
     */
    List<Long> listAttachmentIdsByUserId(Long userId);

    int deleteByUserId(Long userId);

    /**
     * 根据站点ID删除收藏（站点删除时调用）
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);
}
