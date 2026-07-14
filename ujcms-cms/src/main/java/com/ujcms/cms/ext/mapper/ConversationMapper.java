package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Conversation;
import com.ujcms.common.query.QueryInfo;
import java.time.OffsetDateTime;
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
public interface ConversationMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Conversation bean);

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
    Conversation select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Conversation> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据用户ID和对方用户ID获取数据（唯一键查询，不加载关联对象）
     *
     * @param userId 用户ID（行所有者）
     * @param peerId 对方用户ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Conversation findByUserIdAndPeerId(@Param("userId") Long userId, @Param("peerId") Long peerId);

    /**
     * 用户所有会话的未读消息总数（角标用）
     *
     * @param userId 用户ID
     * @return 未读消息总数
     */
    long sumUnread(Long userId);

    /**
     * 更新最后消息摘要和时间（发送方会话行）
     *
     * @param id       主键ID
     * @param lastText 最后消息摘要
     * @param lastTime 最后消息时间
     * @return 更新条数
     */
    int updateLast(@Param("id") Long id, @Param("lastText") String lastText,
                   @Param("lastTime") OffsetDateTime lastTime);

    /**
     * 更新最后消息摘要和时间，并将未读数加一（接收方会话行）。SQL原子自增，避免读改写竞态
     *
     * @param id       主键ID
     * @param lastText 最后消息摘要
     * @param lastTime 最后消息时间
     * @return 更新条数
     */
    int updateLastAndIncUnread(@Param("id") Long id, @Param("lastText") String lastText,
                               @Param("lastTime") OffsetDateTime lastTime);

    /**
     * 清零会话未读数（标记已读）
     *
     * @param userId 用户ID（行所有者）
     * @param peerId 对方用户ID
     * @return 更新条数
     */
    int clearUnread(@Param("userId") Long userId, @Param("peerId") Long peerId);

    /**
     * 根据用户ID删除数据
     *
     * @param userId 用户ID
     * @return 删除条数
     */
    int deleteByUserId(Long userId);

    /**
     * 根据对方用户ID删除数据
     *
     * @param peerId 对方用户ID
     * @return 删除条数
     */
    int deleteByPeerId(Long peerId);
}
