package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Conversation;
import com.ujcms.cms.ext.domain.Message;
import com.ujcms.cms.ext.service.ConversationService;
import com.ujcms.cms.ext.service.MessageService;
import com.ujcms.cms.ext.service.args.MessageArgs;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.common.db.MyBatis.springPage;

/**
 * 私信 Controller
 *
 * @author PONY
 */
@Tag(name = "私信接口")
@RestController
@RequestMapping({API + "/message", FRONTEND_API + "/message"})
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
     * 校验私信接收用户，返回接收用户对象
     */
    private User validateReceiver(User currentUser, Long receiverId) {
        if (User.ANONYMOUS_ID.equals(receiverId)) {
            throw new Http400Exception("Cannot message anonymous user");
        }
        if (currentUser.getId().equals(receiverId)) {
            throw new Http400Exception("Cannot message yourself");
        }
        User receiver = userService.select(receiverId);
        if (receiver == null) {
            throw new Http404Exception(User.NOT_FOUND + receiverId);
        }
        if (receiver.isCancelled()) {
            throw new Http400Exception("User is cancelled. ID: " + receiverId);
        }
        return receiver;
    }

    @Operation(summary = "私信记录分页_MessagePage。获取当前登录用户与对方的私信记录，按ID倒序（即时间倒序）。"
            + "无会话时返回空分页，不返回 404。未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "peerId", description = "对方用户ID", required = true,
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "beforeId", description = "只获取ID小于该值的私信。向前翻阅历史，游标分页不受并发新消息影响",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "afterId", description = "只获取ID大于该值的私信。打开会话时轮询新消息",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public Page<Message> page(@RequestParam Long peerId, @RequestParam(required = false) Long beforeId,
                              @RequestParam(required = false) Long afterId, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        // 私信记录只能通过本人的会话行获取，所有权在SQL层保证
        Conversation conversation = conversationService.findByUserIdAndPeerId(user.getId(), peerId);
        if (conversation == null) {
            return Page.empty();
        }
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        MessageArgs args = MessageArgs.of().conversationId(conversation.getId()).beforeId(beforeId).afterId(afterId);
        int page = Directives.getPage(params);
        int pageSize = Directives.getPageSize(params);
        return springPage(service.selectPage(args, page, pageSize));
    }

    @Operation(summary = "发送私信。门禁：互为好友，或任一方为管理员，否则返回 403。"
            + "接收用户不存在返回 404，本人、匿名用户、已注销用户、内容为空或超过1000字返回 400。未登录返回 401。"
            + "返回发送方的消息副本")
    @PostMapping
    public Message send(@RequestBody @Valid MessageParams params) {
        User user = Contexts.getCurrentUser();
        User receiver = validateReceiver(user, params.getUserId());
        try {
            return service.send(user, receiver, params.getText());
        } catch (DuplicateKeyException e) {
            // 并发首次互发触发会话唯一键兜底，事务已回滚，会话行已存在，重试一次即可
            return service.send(user, receiver, params.getText());
        }
    }

    @Schema(name = "MessageController.MessageParams", description = "私信发送参数")
    public static class MessageParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 接收用户ID
         */
        @NotNull
        @Schema(description = "接收用户ID")
        private Long userId;
        /**
         * 内容
         */
        @NotNull
        @Schema(description = "内容。去除首尾空白后不能为空，最长1000字")
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
}
