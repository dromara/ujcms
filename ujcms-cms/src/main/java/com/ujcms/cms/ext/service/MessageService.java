package com.ujcms.cms.ext.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.domain.Conversation;
import com.ujcms.cms.ext.domain.Message;
import com.ujcms.cms.ext.domain.generated.GeneratedMessage;
import com.ujcms.cms.ext.mapper.ConversationMapper;
import com.ujcms.cms.ext.mapper.MessageMapper;
import com.ujcms.cms.ext.service.args.MessageArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 私信 Service
 * <p>
 * 发送门禁：互为好友，或任一方为管理员类型（会员始终拥有与管理员的回复通道）。
 * 写入纪律：一次发送涉及双方两行会话，按行所有者用户ID升序处理，防止 A→B 与 B→A 并发互发死锁。
 *
 * @author PONY
 */
@Service
public class MessageService {
    private final FriendService friendService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public MessageService(FriendService friendService, ConversationMapper conversationMapper,
                          MessageMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.friendService = friendService;
        this.conversationMapper = conversationMapper;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    /**
     * 群发单次最大接收用户数量
     */
    public static final int BULK_MAX = 100;

    /**
     * 发送私信。接收用户的存在性、非本人、非匿名、非注销校验由调用方完成；
     * 并发首次互发可能触发会话唯一键冲突（事务已回滚），由调用方重试一次
     *
     * @param sender   发送用户
     * @param receiver 接收用户
     * @param text     内容
     * @return 发送方的消息副本（含发送用户引用）
     */
    @Transactional(rollbackFor = Exception.class)
    public Message send(User sender, User receiver, String text) {
        checkGate(sender, receiver);
        String validated = validateText(text);
        Message sent = doSend(sender.getId(), receiver.getId(), validated, OffsetDateTime.now());
        sent.setSender(sender);
        return sent;
    }

    /**
     * 群发私信。同步执行、单事务、全量成功或全量失败。接收用户列表的去重、数量上限、
     * 逐一有效性校验由调用方完成；消息落入发送人与每个接收用户的会话，回复自然回流
     *
     * @param sender    发送用户
     * @param receivers 接收用户列表（已校验）
     * @param text      内容
     */
    @Transactional(rollbackFor = Exception.class)
    public void bulkSend(User sender, List<User> receivers, String text) {
        String validated = validateText(text);
        OffsetDateTime now = OffsetDateTime.now();
        // 死锁纪律：按接收用户ID升序依次执行常规发送写入
        List<User> sorted = receivers.stream().sorted(Comparator.comparing(User::getId)).toList();
        for (User receiver : sorted) {
            checkGate(sender, receiver);
            doSend(sender.getId(), receiver.getId(), validated, now);
        }
    }

    /**
     * 发送门禁：互为好友，或任一方为管理员类型。接收方为管理员时豁免，保证会员
     * 始终可以回复后台来信、联系工作人员；未互加好友的会员之间即使有历史记录也不能发送
     */
    private void checkGate(User sender, User receiver) {
        if (sender.isAdminType() || receiver.isAdminType()
                || friendService.isMutual(sender.getId(), receiver.getId())) {
            return;
        }
        throw new Http403Exception("Only mutual friends can send messages. userId: " + receiver.getId());
    }

    private String validateText(@Nullable String text) {
        String trimmed = StringUtils.trimToEmpty(text);
        if (trimmed.isEmpty()) {
            throw new Http400Exception("Message text is required");
        }
        if (trimmed.length() > Message.TEXT_MAX_LENGTH) {
            throw new Http400Exception("Message text exceeds max length: " + Message.TEXT_MAX_LENGTH);
        }
        return trimmed;
    }

    /**
     * 常规发送写入：先按所有者用户ID升序落实双方会话行，再插入两份消息副本。
     * 会话行不存在时直接以本条消息的摘要、时间、未读数新建，已存在时更新摘要、时间并为接收方原子自增未读数。
     * 新建会话行绝不携带空摘要：Oracle 系数据库（达梦、openGauss、Vastbase 等）会把空串存为 NULL，与非空约束冲突。
     * 会话行一律先于消息副本写入，两行会话锁按所有者用户ID升序获取，消息副本的外键共享锁落在本事务已持有的行上
     */
    private Message doSend(Long senderId, Long receiverId, String text, OffsetDateTime now) {
        String excerpt = StringUtils.substring(text, 0, Message.EXCERPT_MAX_LENGTH);
        Long senderConvId;
        Long receiverConvId;
        if (senderId.compareTo(receiverId) < 0) {
            senderConvId = touch(senderId, receiverId, excerpt, now, false);
            receiverConvId = touch(receiverId, senderId, excerpt, now, true);
        } else {
            receiverConvId = touch(receiverId, senderId, excerpt, now, true);
            senderConvId = touch(senderId, receiverId, excerpt, now, false);
        }
        Message sent = insertCopy(senderConvId, senderId, text, now);
        insertCopy(receiverConvId, senderId, text, now);
        return sent;
    }

    /**
     * 落实一方的会话行并返回其ID。不存在则以本条消息的摘要、时间、未读数直接新建；
     * 已存在则更新摘要与时间，接收方一侧原子自增未读数
     *
     * @param userId   会话行所有者用户ID
     * @param peerId   对方用户ID
     * @param excerpt  最后消息摘要（非空）
     * @param now      最后消息时间
     * @param received 行所有者是否为本条消息的接收方（是则计入未读）
     * @return 会话ID
     */
    private Long touch(Long userId, Long peerId, String excerpt, OffsetDateTime now, boolean received) {
        Conversation conversation = conversationMapper.findByUserIdAndPeerId(userId, peerId);
        if (conversation != null) {
            if (received) {
                conversationMapper.updateLastAndIncUnread(conversation.getId(), excerpt, now);
            } else {
                conversationMapper.updateLast(conversation.getId(), excerpt, now);
            }
            return conversation.getId();
        }
        conversation = new Conversation();
        conversation.setId(snowflakeSequence.nextId());
        conversation.setUserId(userId);
        conversation.setPeerId(peerId);
        conversation.setUnread(received ? 1 : 0);
        conversation.setLastTime(now);
        conversation.setLastText(excerpt);
        conversation.setCreated(now);
        conversationMapper.insert(conversation);
        return conversation.getId();
    }

    private Message insertCopy(Long conversationId, Long senderId, String text, OffsetDateTime now) {
        Message bean = new Message();
        bean.setId(snowflakeSequence.nextId());
        bean.setConversationId(conversationId);
        bean.setSenderId(senderId);
        bean.setText(text);
        bean.setCreated(now);
        mapper.insert(bean);
        return bean;
    }

    @Nullable
    public Message select(Long id) {
        return mapper.select(id);
    }

    public List<Message> selectList(MessageArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedMessage.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Message> selectList(MessageArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Message> selectPage(MessageArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }
}
