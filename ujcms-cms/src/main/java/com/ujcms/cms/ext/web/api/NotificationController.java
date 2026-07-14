package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Notification;
import com.ujcms.cms.ext.service.NotificationService;
import com.ujcms.cms.ext.service.args.NotificationArgs;
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
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
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
 * 通知 Controller
 *
 * @author PONY
 */
@Tag(name = "通知接口")
@RestController
@RequestMapping({API + "/notification", FRONTEND_API + "/notification"})
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /**
     * 单次标记已读/删除的最大通知ID数量。与每页最大条数一致，防止超大 IN 列表
     */
    private static final int MAX_IDS = 1000;

    /**
     * 组装当前用户的通知查询条件。{@code userId} 强制为当前用户，不接受任意查询参数
     */
    private static NotificationArgs assembleArgs(Long userId, @Nullable Short type, @Nullable Boolean isRead) {
        return NotificationArgs.of().userId(userId).type(type).isRead(isRead);
    }

    @Operation(summary = "通知列表_NotificationList。获取当前登录用户的通知，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "type", description = "类型(1:系统公告,2:评论回复,3:@提及,4:关注文章新评论,5:关注栏目新文章,6:好友申请,7:好友通过,8:新增粉丝,9:关注用户新文章)。不传获取全部",
            schema = @Schema(type = "integer", format = "int16"))
    @Parameter(in = ParameterIn.QUERY, name = "isRead", description = "是否已读。如：`true` `false`，不传获取全部",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Notification> list(@RequestParam(required = false) Short type,
                                   @RequestParam(required = false) Boolean isRead, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        NotificationArgs args = assembleArgs(user.getId(), type, isRead);
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        return service.selectList(args, offset, limit);
    }

    @Operation(summary = "通知分页_NotificationPage。获取当前登录用户的通知，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "type", description = "类型(1:系统公告,2:评论回复,3:@提及,4:关注文章新评论,5:关注栏目新文章,6:好友申请,7:好友通过,8:新增粉丝,9:关注用户新文章)。不传获取全部",
            schema = @Schema(type = "integer", format = "int16"))
    @Parameter(in = ParameterIn.QUERY, name = "isRead", description = "是否已读。如：`true` `false`，不传获取全部",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Notification> page(@RequestParam(required = false) Short type,
                                   @RequestParam(required = false) Boolean isRead, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        NotificationArgs args = assembleArgs(user.getId(), type, isRead);
        int page = Directives.getPage(params);
        int pageSize = Directives.getPageSize(params);
        return springPage(service.selectPage(args, page, pageSize));
    }

    @Operation(summary = "未读通知数量。轮询接口，未登录用户返回 0，不返回 401")
    @GetMapping("/unread-count")
    public long unreadCount() {
        User user = Contexts.findCurrentUser();
        if (user == null) {
            return 0;
        }
        return service.countUnread(user.getId());
    }

    @Operation(summary = "标记通知已读。未登录返回 401。只能标记当前登录用户自己的通知，其他用户的通知ID不生效")
    @PutMapping("/read")
    public ResponseEntity<Responses.Body> read(@RequestBody @Valid IdsParams params) {
        User user = Contexts.getCurrentUser();
        service.markRead(params.getIds(), user.getId());
        return Responses.ok();
    }

    @Operation(summary = "标记全部通知已读。未登录返回 401")
    @PutMapping("/read-all")
    public ResponseEntity<Responses.Body> readAll() {
        User user = Contexts.getCurrentUser();
        service.markAllRead(user.getId());
        return Responses.ok();
    }

    @Operation(summary = "删除通知。未登录返回 401。只能删除当前登录用户自己的通知，其他用户的通知ID不生效")
    @DeleteMapping
    public ResponseEntity<Responses.Body> delete(@RequestBody @Valid IdsParams params) {
        User user = Contexts.getCurrentUser();
        service.delete(params.getIds(), user.getId());
        return Responses.ok();
    }

    @Operation(summary = "清空已读通知。未登录返回 401")
    @DeleteMapping("/read")
    public ResponseEntity<Responses.Body> deleteRead() {
        User user = Contexts.getCurrentUser();
        service.deleteRead(user.getId());
        return Responses.ok();
    }

    @Schema(name = "NotificationController.IdsParams", description = "通知ID列表参数")
    public static class IdsParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 通知ID列表
         */
        @NotNull
        @Size(max = MAX_IDS)
        @Schema(description = "通知ID列表，单次最多 1000 个")
        private List<Long> ids;

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }
    }
}
