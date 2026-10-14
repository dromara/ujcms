package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.service.args.UserArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.ext.domain.Conversation;
import com.ujcms.cms.ext.domain.Message;
import com.ujcms.cms.ext.service.ConversationService;
import com.ujcms.cms.ext.service.MessageService;
import com.ujcms.cms.ext.service.args.ConversationArgs;
import com.ujcms.cms.ext.service.args.MessageArgs;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.common.db.MyBatis.springPage;

/**
 * 私信 Controller。所有接口只操作当前登录用户本人的会话与私信，后台不提供浏览其他用户私信的接口（隐私约束）
 *
 * @author PONY
 */
@RestController("backendMessageController")
@RequestMapping(BACKEND_API + "/ext/message")
public class MessageController {
    private final UserService userService;
    private final ConversationService conversationService;
    private final MessageService service;

    public MessageController(UserService userService, ConversationService conversationService,
                             MessageService service) {
        this.userService = userService;
        this.conversationService = conversationService;
        this.service = service;
    }

    /**
     * 接收用户搜索最大返回条数
     */
    private static final int USER_SEARCH_MAX_LIMIT = 100;

    @GetMapping("conversation")
    @PreAuthorize("hasAnyAuthority('message:list','*')")
    public Page<Conversation> conversationPage(@RequestParam("page") Integer page,
                                               @RequestParam("pageSize") Integer pageSize) {
        User user = Contexts.getCurrentUser();
        // userId 强制为当前用户，不接受任意查询参数
        ConversationArgs args = ConversationArgs.of().userId(user.getId());
        return springPage(conversationService.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("conversation/unread-count")
    @PreAuthorize("hasAnyAuthority('message:list','*')")
    public long unreadCount() {
        User user = Contexts.getCurrentUser();
        return conversationService.unreadTotal(user.getId());
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('message:list','*')")
    public Page<Message> page(@RequestParam Long peerId, @RequestParam(required = false) Long beforeId,
                              @RequestParam(required = false) Long afterId,
                              @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        User user = Contexts.getCurrentUser();
        // 私信记录只能通过本人的会话行获取，所有权在SQL层保证。无会话时返回空分页
        Conversation conversation = conversationService.findByUserIdAndPeerId(user.getId(), peerId);
        if (conversation == null) {
            return Page.empty();
        }
        MessageArgs args = MessageArgs.of().conversationId(conversation.getId()).beforeId(beforeId).afterId(afterId);
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    /**
     * 标记会话已读。个人状态操作，非针对他人数据的管理动作，刻意不记操作日志
     */
    @PutMapping("read")
    @PreAuthorize("hasAnyAuthority('message:list','*')")
    public ResponseEntity<Body> read(@RequestBody @Valid PeerParams params) {
        User user = Contexts.getCurrentUser();
        conversationService.markRead(user.getId(), params.getPeerId());
        return Responses.ok();
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('message:send','*')")
    @OperationLog(module = "message", operation = "send", type = OperationType.CREATE)
    public Message send(@RequestBody @Valid SendParams params) {
        User sender = Contexts.getCurrentUser();
        User receiver = validateReceiver(sender, params.getUserId());
        try {
            return service.send(sender, receiver, params.getText());
        } catch (DuplicateKeyException e) {
            // 并发首次互发触发会话唯一键兜底，事务已回滚，会话行已存在，重试一次即可
            return service.send(sender, receiver, params.getText());
        }
    }

    @PostMapping("bulk")
    @PreAuthorize("hasAnyAuthority('message:bulkSend','*')")
    @OperationLog(module = "message", operation = "bulkSend", type = OperationType.CREATE)
    public ResponseEntity<Body> bulkSend(@RequestBody @Valid BulkSendParams params) {
        User sender = Contexts.getCurrentUser();
        List<Long> userIds = params.getUserIds().stream().filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            throw new Http400Exception("userIds is required");
        }
        if (userIds.size() > MessageService.BULK_MAX) {
            throw new Http400Exception("Recipients exceed max: " + MessageService.BULK_MAX);
        }
        // 全量校验接收用户，任一无效则整体失败，不发送任何私信
        List<User> receivers = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            receivers.add(validateReceiver(sender, userId));
        }
        try {
            service.bulkSend(sender, receivers, params.getText());
        } catch (DuplicateKeyException e) {
            // 并发创建会话触发唯一键兜底，事务已回滚（未送达任何私信），重试一次即可
            service.bulkSend(sender, receivers, params.getText());
        }
        return Responses.ok();
    }

    @DeleteMapping("conversation")
    @PreAuthorize("hasAnyAuthority('message:delete','*')")
    @OperationLog(module = "message", operation = "deleteConversation", type = OperationType.DELETE)
    public ResponseEntity<Body> deleteConversation(@RequestParam Long peerId) {
        User user = Contexts.getCurrentUser();
        conversationService.deleteOwnSide(user.getId(), peerId);
        return Responses.ok();
    }

    /**
     * 接收用户搜索。刻意不复用 {@code user:list} 权限（其暴露完整用户数据），仅返回展示所需字段
     */
    @GetMapping("user")
    @PreAuthorize("hasAnyAuthority('message:send','*')")
    public List<UserRefer> userList(@RequestParam String q,
                                    @RequestParam(required = false, defaultValue = "20") int limit) {
        if (StringUtils.isBlank(q)) {
            return Collections.emptyList();
        }
        Map<String, Object> queryMap = new HashMap<>(16);
        // 同组条件为 OR：用户名或昵称包含关键字
        queryMap.put("Contains_0q_username", q);
        queryMap.put("Contains_0q_nickname", q);
        queryMap.put("NE_status_Short", User.STATUS_CANCELLED);
        queryMap.put("NE_id_Long", User.ANONYMOUS_ID);
        List<User> users = userService.selectList(UserArgs.of(queryMap), 0,
                Math.min(Math.max(limit, 1), USER_SEARCH_MAX_LIMIT));
        return users.stream().map(user ->
                new UserRefer(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar())).toList();
    }

    /**
     * 校验私信接收用户，返回接收用户对象
     */
    private User validateReceiver(User sender, @Nullable Long receiverId) {
        if (receiverId == null || User.ANONYMOUS_ID.equals(receiverId)) {
            throw new Http400Exception("Invalid recipient. ID: " + receiverId);
        }
        if (sender.getId().equals(receiverId)) {
            throw new Http400Exception("Cannot message yourself");
        }
        User receiver = userService.select(receiverId);
        if (receiver == null || receiver.isCancelled()) {
            throw new Http400Exception("Invalid recipient. ID: " + receiverId);
        }
        return receiver;
    }

    /**
     * 用户展示字段
     */
    public record UserRefer(Long id, String username, @Nullable String nickname, @Nullable String avatar) {
    }

    public static class PeerParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 对方用户ID
         */
        @NotNull
        private Long peerId;

        public Long getPeerId() {
            return peerId;
        }

        public void setPeerId(Long peerId) {
            this.peerId = peerId;
        }
    }

    public static class SendParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 接收用户ID
         */
        @NotNull
        private Long userId;
        /**
         * 内容
         */
        @NotNull
        private String text;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static class BulkSendParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 接收用户ID列表
         */
        @NotEmpty
        private List<Long> userIds;
        /**
         * 内容
         */
        @NotNull
        private String text;

        public List<Long> getUserIds() {
            return userIds;
        }

        public void setUserIds(List<Long> userIds) {
            this.userIds = userIds;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
