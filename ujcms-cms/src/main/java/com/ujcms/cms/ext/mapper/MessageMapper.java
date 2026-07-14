package com.ujcms.cms.ext.mapper;

import com.ujcms.cms.ext.domain.Message;
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
public interface MessageMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Message bean);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Message select(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<Message> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据会话ID删除数据（删除己方会话时清理己方副本）
     *
     * @param conversationId 会话ID
     * @return 删除条数
     */
    int deleteByConversationId(Long conversationId);

    /**
     * 删除用户所有会话中的消息副本（IN子查询，兼容各数据库，不使用关联删除）
     *
     * @param userId 用户ID（会话行所有者）
     * @return 删除条数
     */
    int deleteByConversationUserId(Long userId);

    /**
     * 删除以用户为对方的所有会话中的消息副本（IN子查询，兼容各数据库，不使用关联删除）
     *
     * @param peerId 对方用户ID
     * @return 删除条数
     */
    int deleteByConversationPeerId(Long peerId);
}
