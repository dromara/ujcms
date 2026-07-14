package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.ext.domain.Conversation;
import com.ujcms.cms.ext.domain.generated.GeneratedConversation;
import com.ujcms.cms.ext.mapper.ConversationMapper;
import com.ujcms.cms.ext.mapper.MessageMapper;
import com.ujcms.cms.ext.service.args.ConversationArgs;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 私信会话 Service
 * <p>
 * 信箱模型：一对用户各有一行会话，所有查询均以 {@code userId} 等值过滤，只能操作本人的会话行。
 *
 * @author PONY
 */
@Service
public class ConversationService implements UserDeleteListener {
    private final ConversationMapper mapper;
    private final MessageMapper messageMapper;

    public ConversationService(ConversationMapper mapper, MessageMapper messageMapper) {
        this.mapper = mapper;
        this.messageMapper = messageMapper;
    }

    @Nullable
    public Conversation select(Long id) {
        return mapper.select(id);
    }

    @Nullable
    public Conversation findByUserIdAndPeerId(Long userId, Long peerId) {
        return mapper.findByUserIdAndPeerId(userId, peerId);
    }

    public List<Conversation> selectList(ConversationArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedConversation.TABLE_NAME,
                "lastTime_desc,id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Conversation> selectList(ConversationArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Conversation> selectPage(ConversationArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    /**
     * 用户所有会话的未读消息总数（角标轮询用，单次索引范围扫描）
     *
     * @param userId 用户ID
     * @return 未读消息总数
     */
    public long unreadTotal(Long userId) {
        return mapper.sumUnread(userId);
    }

    /**
     * 标记与对方的会话已读。无会话时不生效，幂等
     *
     * @param userId 用户ID
     * @param peerId 对方用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long peerId) {
        mapper.clearUnread(userId, peerId);
    }

    /**
     * 删除己方会话（会话行及己方消息副本）。对方会话不受影响，收到新消息时会话行重建。幂等
     *
     * @param userId 用户ID
     * @param peerId 对方用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnSide(Long userId, Long peerId) {
        Conversation conversation = mapper.findByUserIdAndPeerId(userId, peerId);
        if (conversation == null) {
            return;
        }
        messageMapper.deleteByConversationId(conversation.getId());
        mapper.delete(conversation.getId());
    }

    @Override
    public void preUserDelete(Long userId) {
        // 双向清理：先删除双方会话中的消息副本，再删除双方会话行。消息副本的发送用户
        // 若为被删用户，其所在会话必属本人所有或以本人为对方，均在此处删除，外键不会阻塞
        messageMapper.deleteByConversationUserId(userId);
        messageMapper.deleteByConversationPeerId(userId);
        mapper.deleteByUserId(userId);
        mapper.deleteByPeerId(userId);
    }
}
