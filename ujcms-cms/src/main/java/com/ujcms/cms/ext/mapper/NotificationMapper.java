package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Notification;
import com.ujcms.common.query.QueryInfo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * @author PONY
 */
@Mapper
@Repository
public interface NotificationMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Notification bean);

    /**
     * 批量插入数据
     *
     * @param list 实体对象列表
     * @return 插入条数
     */
    int insertBatch(@Param("list") List<Notification> list);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Notification select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Notification> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 统计用户未读通知数量
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    long countUnread(Long userId);

    /**
     * 标记已读。只更新属于该用户的数据
     *
     * @param ids    通知ID列表
     * @param userId 用户ID
     * @return 更新条数
     */
    int updateRead(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    /**
     * 全部标记已读
     *
     * @param userId 用户ID
     * @return 更新条数
     */
    int updateAllRead(Long userId);

    /**
     * 批量删除。只删除属于该用户的数据
     *
     * @param ids    通知ID列表
     * @param userId 用户ID
     * @return 删除条数
     */
    int deleteByIds(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    /**
     * 删除用户的已读通知
     *
     * @param userId 用户ID
     * @return 删除条数
     */
    int deleteReadByUserId(Long userId);

    /**
     * 根据文章ID删除数据
     *
     * @param articleId 文章ID
     * @return 删除条数
     */
    int deleteByArticleId(Long articleId);

    /**
     * 根据栏目ID删除数据
     *
     * @param channelId 栏目ID
     * @return 删除条数
     */
    int deleteByChannelId(Long channelId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(Long siteId);

    /**
     * 根据接收用户ID删除数据
     *
     * @param userId 用户ID
     * @return 删除条数
     */
    int deleteByUserId(Long userId);

    /**
     * 清空发送用户ID（发送用户被删除后，显示为系统）
     *
     * @param userId 发送用户ID
     * @return 更新条数
     */
    int clearSenderId(Long userId);

    /**
     * 获取文章发布通知分发标记
     *
     * @param articleId 文章ID
     * @return {@code '0'} 未分发，{@code '1'} 已分发。没有找到数据，则返回 {@code null}
     */
    @Nullable
    String findArticlePublishNotified(Long articleId);

    /**
     * 原子认领文章发布通知分发标记
     *
     * @param articleId 文章ID
     * @return 更新条数。{@code 0} 为已被认领
     */
    int claimArticlePublishNotified(Long articleId);

    /**
     * 获取评论通知分发标记
     *
     * @param commentId 评论ID
     * @return {@code '0'} 未分发，{@code '1'} 已分发。没有找到数据，则返回 {@code null}
     */
    @Nullable
    String findCommentNotified(Long commentId);

    /**
     * 原子认领评论通知分发标记
     *
     * @param commentId 评论ID
     * @return 更新条数。{@code 0} 为已被认领
     */
    int claimCommentNotified(Long commentId);
}
