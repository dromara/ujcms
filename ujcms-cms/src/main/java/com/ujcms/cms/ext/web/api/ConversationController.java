package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Conversation;
import com.ujcms.cms.ext.service.ConversationService;
import com.ujcms.cms.ext.service.args.ConversationArgs;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.common.db.MyBatis.springPage;

/**
 * 私信会话 Controller
 *
 * @author PONY
 */
@Tag(name = "私信会话接口")
@RestController
@RequestMapping({API + "/conversation", FRONTEND_API + "/conversation"})
public class ConversationController {
    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @Operation(summary = "会话列表_ConversationList。获取当前登录用户的私信会话，按最后消息时间倒序，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Conversation> list(HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        // userId 强制为当前用户，不接受任意查询参数
        ConversationArgs args = ConversationArgs.of().userId(user.getId());
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        return service.selectList(args, offset, limit);
    }

    @Operation(summary = "会话分页_ConversationPage。获取当前登录用户的私信会话，按最后消息时间倒序，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Conversation> page(HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        ConversationArgs args = ConversationArgs.of().userId(user.getId());
        int page = Directives.getPage(params);
        int pageSize = Directives.getPageSize(params);
        return springPage(service.selectPage(args, page, pageSize));
    }

    @Operation(summary = "未读私信数量。轮询接口，未登录用户返回 0，不返回 401")
    @GetMapping("/unread-count")
    public long unreadCount() {
        User user = Contexts.findCurrentUser();
        if (user == null) {
            return 0;
        }
        return service.unreadTotal(user.getId());
    }

    @Operation(summary = "标记会话已读。将与对方的会话未读数清零，无会话时不报错。未登录返回 401")
    @PutMapping("/read")
    public ResponseEntity<Responses.Body> read(@RequestBody @Valid PeerParams params) {
        User user = Contexts.getCurrentUser();
        service.markRead(user.getId(), params.getPeerId());
        return Responses.ok();
    }

    @Operation(summary = "删除会话。仅删除己方会话及己方消息副本，对方不受影响，收到新消息时会话重建。"
            + "无会话时不报错。未登录返回 401")
    @DeleteMapping
    public ResponseEntity<Responses.Body> delete(@Parameter(description = "对方用户ID") @RequestParam Long peerId) {
        User user = Contexts.getCurrentUser();
        service.deleteOwnSide(user.getId(), peerId);
        return Responses.ok();
    }

    @Schema(name = "ConversationController.PeerParams", description = "会话对方参数")
    public static class PeerParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 对方用户ID
         */
        @NotNull
        @Schema(description = "对方用户ID")
        private Long peerId;

        public Long getPeerId() {
            return peerId;
        }

        public void setPeerId(Long peerId) {
            this.peerId = peerId;
        }
    }
}
