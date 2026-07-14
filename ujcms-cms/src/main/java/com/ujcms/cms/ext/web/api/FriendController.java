package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Friend;
import com.ujcms.cms.ext.service.FriendService;
import com.ujcms.cms.ext.service.args.FriendArgs;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Responses;
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
import org.springframework.http.ResponseEntity;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.common.db.MyBatis.springPage;

/**
 * 好友 Controller
 *
 * @author PONY
 */
@Tag(name = "好友接口")
@RestController
@RequestMapping({API + "/friend", FRONTEND_API + "/friend"})
public class FriendController {
    private final UserService userService;
    private final FriendService service;

    public FriendController(UserService userService, FriendService service) {
        this.userService = userService;
        this.service = service;
    }

    private static String toValue(Friend.Relation relation) {
        return relation.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 校验申请、通过、解除好友的目标用户，返回目标用户对象
     */
    private User validateTarget(User currentUser, Long targetId) {
        if (User.ANONYMOUS_ID.equals(targetId)) {
            throw new Http400Exception("Cannot befriend anonymous user");
        }
        if (currentUser.getId().equals(targetId)) {
            throw new Http400Exception("Cannot befriend yourself");
        }
        User target = userService.select(targetId);
        if (target == null) {
            throw new Http404Exception(User.NOT_FOUND + targetId);
        }
        if (target.isCancelled()) {
            throw new Http400Exception("User is cancelled. ID: " + targetId);
        }
        return target;
    }

    @Operation(summary = "好友列表_FriendList。获取当前登录用户的好友（已通过），未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Friend> list(HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        // userId 强制为当前用户，不接受任意查询参数
        FriendArgs args = FriendArgs.of().userId(user.getId()).status(Friend.STATUS_ACCEPTED);
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        return service.selectList(args, offset, limit);
    }

    @Operation(summary = "好友分页_FriendPage。获取当前登录用户的好友（已通过），未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Friend> page(HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        FriendArgs args = FriendArgs.of().userId(user.getId()).status(Friend.STATUS_ACCEPTED);
        int page = Directives.getPage(params);
        int pageSize = Directives.getPageSize(params);
        return springPage(service.selectPage(args, page, pageSize));
    }

    @Operation(summary = "收到的好友申请分页。获取待当前登录用户处理的申请，申请人为 user 属性，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/pending")
    public Page<Friend> pending(HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        // 待我处理：好友用户ID为我的待通过行，行所有者即申请人
        FriendArgs args = FriendArgs.of().friendId(user.getId()).status(Friend.STATUS_PENDING);
        int page = Directives.getPage(params);
        int pageSize = Directives.getPageSize(params);
        return springPage(service.selectPage(args, page, pageSize));
    }

    @Operation(summary = "收到的好友申请数量。角标用，未登录返回 401")
    @GetMapping("/pending-count")
    public long pendingCount() {
        User user = Contexts.getCurrentUser();
        return service.pendingCount(user.getId());
    }

    @Operation(summary = "发出的好友申请分页。获取当前登录用户发出且待通过的申请，对方为 friend 属性，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/sent")
    public Page<Friend> sent(HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        FriendArgs args = FriendArgs.of().userId(user.getId()).status(Friend.STATUS_PENDING);
        int page = Directives.getPage(params);
        int pageSize = Directives.getPageSize(params);
        return springPage(service.selectPage(args, page, pageSize));
    }

    @Operation(summary = "与指定用户的好友关系。返回 none/pending_sent/pending_received/accepted/self。"
            + "未登录用户返回 none，不返回 401")
    @GetMapping("/relation")
    public String relation(@Parameter(description = "对方用户ID") @RequestParam Long userId) {
        User user = Contexts.findCurrentUser();
        if (user == null) {
            return toValue(Friend.Relation.NONE);
        }
        return toValue(service.relation(user.getId(), userId));
    }

    @Operation(summary = "发起好友申请。返回申请后的好友关系字符串。已申请或已是好友时不报错，返回当前关系；"
            + "对方已向我申请时直接通过。目标用户不存在返回 404，本人、匿名用户、已注销用户返回 400，超过每日申请上限返回 400。未登录返回 401")
    @PostMapping
    public String create(@RequestBody @Valid FriendParams params) {
        User user = Contexts.getCurrentUser();
        User target = validateTarget(user, params.getUserId());
        try {
            return toValue(service.request(user, target));
        } catch (DuplicateKeyException e) {
            // 并发重复申请。唯一索引兜底，事务已回滚，返回当前关系即可
            return toValue(service.relation(user.getId(), target.getId()));
        }
    }

    @Operation(summary = "通过好友申请。userId 为申请人用户ID，无申请记录返回 404，已是好友不报错。未登录返回 401")
    @PutMapping("/accept")
    public ResponseEntity<Responses.Body> accept(@RequestBody @Valid FriendParams params) {
        User user = Contexts.getCurrentUser();
        boolean found;
        try {
            found = service.accept(user.getId(), params.getUserId());
        } catch (DuplicateKeyException e) {
            // 并发重复通过。唯一索引兜底，事务已回滚，视为已通过即可
            found = true;
        }
        if (!found) {
            throw new Http404Exception("Friend request not found. userId: " + params.getUserId());
        }
        return Responses.ok();
    }

    @Operation(summary = "解除好友关系。涵盖拒绝申请、取消申请、删除好友。无好友关系时不报错。未登录返回 401")
    @DeleteMapping
    public ResponseEntity<Responses.Body> remove(@Parameter(description = "对方用户ID") @RequestParam Long userId) {
        User user = Contexts.getCurrentUser();
        service.removeRelation(user.getId(), userId);
        return Responses.ok();
    }

    @Schema(name = "FriendController.FriendParams", description = "好友参数")
    public static class FriendParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 对方用户ID
         */
        @NotNull
        @Schema(description = "对方用户ID")
        private Long userId;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }
}
