package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.MessageBoard;
import com.ujcms.cms.ext.service.MessageBoardService;
import com.ujcms.cms.ext.service.args.MessageBoardArgs;
import com.ujcms.cms.ext.web.directive.MessageBoardListDirective;
import com.ujcms.commons.captcha.CaptchaTokenService;
import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.Views;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http401Exception;
import com.ujcms.commons.web.exception.Http403Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.commons.db.MyBatis.springPage;
import static com.ujcms.commons.query.QueryUtils.QUERY_PREFIX;

/**
 * 留言 Controller
 *
 * @author PONY
 */
@Tag(name = "留言接口")
@RestController
@RequestMapping({API + "/message-board", FRONTEND_API + "/message-board"})
public class MessageBoardController {
    private final CaptchaTokenService captchaTokenService;
    private final MessageBoardService service;
    private final SiteResolver siteResolver;

    public MessageBoardController(CaptchaTokenService captchaTokenService, MessageBoardService service,
                                  SiteResolver siteResolver) {
        this.captchaTokenService = captchaTokenService;
        this.service = service;
        this.siteResolver = siteResolver;
    }

    private <T> T query(HttpServletRequest request, BiFunction<MessageBoardArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        MessageBoardArgs args = MessageBoardArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        MessageBoardListDirective.assemble(args, params, site.getId());
        return handle.apply(args, params);
    }

    @Operation(summary = "留言列表_MessageBoardList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "typeId", description = "类型ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "typeId", description = "类型ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isRecommended", description = "是否推荐。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isReplied", description = "是否回复。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点调查问卷。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<MessageBoard> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return service.selectList(args, offset, limit);
        });
    }

    @Operation(summary = "留言分页_MessageBoardPage")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "typeId", description = "类型ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isRecommended", description = "是否推荐。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isReplied", description = "是否回复。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点调查问卷。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<MessageBoard> page(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int page = Directives.getPage(params);
            int pageSize = Directives.getPageSize(params);
            return springPage(service.selectPage(args, page, pageSize));
        });
    }

    @Operation(summary = "获取留言对象")
    @ApiResponses(value = {@ApiResponse(description = "留言对象")})
    @GetMapping("/{id:[\\d]+}")
    public MessageBoard show(@Parameter(description = "留言ID") @PathVariable Long id) {
        MessageBoard bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception("MessageBoard not found. ID: " + id);
        }
        if (!bean.getSite().getMessageBoard().isEnabled()) {
            throw new Http403Exception("MessageBoard is not enabled.");
        }
        return bean;
    }

    @Operation(summary = "提交留言")
    @PostMapping
    public ResponseEntity<Responses.Body> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "留言对象。" +
                    "不可用属性：\"userId\", \"replyUserId\", \"replyText\", \"created\", \"replyDate\", \"ip\", \"replied\", \"recommended\", \"status\"")
            @RequestBody @Valid MessageBoardParams params,
            HttpServletRequest request) {
        Site site = siteResolver.resolve(request, params.getSiteId());
        if (!site.getMessageBoard().isEnabled()) {
            throw new Http403Exception("MessageBoard is not enabled.");
        }
        User user = Contexts.findCurrentUser();
        Long userId;
        if (user == null) {
            if (site.getMessageBoard().isLoginRequired()) {
                throw new Http401Exception("MessageBoard login required.");
            }
            userId = User.ANONYMOUS_ID;
        } else {
            userId = user.getId();
        }
        if (!captchaTokenService.validateCaptcha(params.captchaToken, params.captcha)) {
            throw new Http400Exception("Captcha is incorrect.");
        }
        MessageBoard messageBoard = new MessageBoard();
        Entities.copy(params, messageBoard,
                "userId", "replyUserId", "replyText", "created", "replyDate",
                "ip", "replied", "recommended", "status");
        messageBoard.setSiteId(site.getId());
        String ip = Servlets.getRemoteAddr(request);
        service.insert(messageBoard, userId, ip);
        return Responses.ok();
    }

    @Schema(name = "MessageBoardController.MessageBoardParams", description = "留言参数")
    public static class MessageBoardParams extends MessageBoard {
        private static final long serialVersionUID = 1L;
        @Schema(description = "验证码")
        public String captcha;
        @Schema(description = "验证码TOKEN")
        public String captchaToken;
    }
}
