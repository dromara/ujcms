package com.ujcms.cms.ext.web.api;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.base.UserBase;
import com.ujcms.cms.core.service.ActionService;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.domain.base.VoteOptionBase;
import com.ujcms.cms.ext.service.VoteService;
import com.ujcms.cms.ext.service.args.VoteArgs;
import com.ujcms.cms.ext.web.directive.VoteListDirective;
import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.exception.Http400Exception;
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
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.commons.db.MyBatis.springPage;
import static com.ujcms.commons.query.QueryUtils.QUERY_PREFIX;

/**
 * 投票前台 接口
 *
 * @author PONY
 */
@Tag(name = "投票接口")
@RestController
@RequestMapping({API + "/vote", FRONTEND_API + "/vote"})
public class VoteController {
    private final SiteResolver siteResolver;
    private final ActionService actionService;
    private final VoteService service;

    public VoteController(SiteResolver siteResolver, ActionService actionService, VoteService service) {
        this.siteResolver = siteResolver;
        this.actionService = actionService;
        this.service = service;
    }

    private <T> T query(HttpServletRequest request, BiFunction<VoteArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        VoteArgs args = VoteArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        VoteListDirective.assemble(args, params, site.getId());
        return handle.apply(args, params);
    }

    @Operation(summary = "投票列表_VoteList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isWithinDate", description = "是否在投票期限内。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点投票。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @GetMapping
    public List<Vote> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return service.selectList(args, offset, limit);
        });
    }

    @Operation(summary = "投票分页_VotePage")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isWithinDate", description = "是否在投票期限内。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点投票。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @GetMapping("/page")
    public Page<Vote> page(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int page = Directives.getPage(params);
            int pageSize = Directives.getPageSize(params);
            return springPage(service.selectPage(args, page, pageSize));
        });
    }

    @Operation(summary = "投票对象_Vote")
    @ApiResponses(value = {@ApiResponse(description = "投票对象")})
    @GetMapping("/{id:[\\d]+}")
    public Vote show(@Parameter(description = "投票ID") @PathVariable Long id) {
        Vote vote = service.select(id);
        validateVote(id, vote);
        return vote;
    }

    public static void validateVote(Long id, Vote vote) {
        if (vote == null) {
            throw new Http404Exception("Vote not found. ID: " + id);
        }
        if (Boolean.FALSE.equals(vote.getEnabled())) {
            throw new Http404Exception("Vote not enabled. ID: " + id);
        }
    }

    @Operation(summary = "投票")
    @ApiResponse(description = "message: 提示信息。status: 状态码。0，成功；-1，失败；" +
            "1000，投票未启用；" +
            "1001，投票未开始；" +
            "1002，投票已结束；" +
            "1003，本投票需先登录；" +
            "1004，已经投过票；")
    @PostMapping("/cast")
    public ResponseEntity<Responses.Body> cast(@RequestBody @Valid VoteParams params,
                                               HttpServletRequest request, HttpServletResponse response) {
        List<Long> optionIds = params.getOptionIds();
        Vote vote = service.select(params.id);
        if (vote == null) {
            throw new Http400Exception("Vote not found. id: " + params.id);
        }
        validateCast(vote, optionIds);
        switch (vote.getStatus()) {
            case Vote.STATUS_DISABLED:
                return Responses.status(1000, request, "error.vote.disabled", params.id);
            case Vote.STATUS_NOT_STARTED:
                return Responses.status(1001, request, "error.vote.notStarted", params.id);
            case Vote.STATUS_HAS_ENDED:
                return Responses.status(1002, request, "error.vote.hasEnded", params.id);
            default:
        }
        // 只能选择一个选项
        if (Boolean.FALSE.equals(vote.getMultiple()) && optionIds.size() > 1) {
            throw new Http400Exception("Only one option can be selected.");
        }
        Site site = siteResolver.resolve(request);
        long cookie = Constants.retrieveIdentityCookie(request, response);
        String ip = Servlets.getRemoteAddr(request);
        Long userId = Optional.ofNullable(Contexts.findCurrentUser()).map(UserBase::getId).orElse(null);
        OffsetDateTime date = vote.getInterval() > 0 ? OffsetDateTime.now().minusDays(vote.getInterval()) : null;
        // 已经投过票
        boolean voted;
        switch (vote.getMode()) {
            case Vote.MODE_COOKIE:
                voted = actionService.existsBy(Vote.ACTION_TYPE, vote.getId(), null, date,
                        null, null, cookie);
                break;
            case Vote.MODE_IP:
                voted = actionService.existsBy(Vote.ACTION_TYPE, vote.getId(), null, date,
                        null, ip, null);
                break;
            case Vote.MODE_USER:
                if (userId == null) {
                    return Responses.status(1003, request, "error.vote.notLogin");
                }
                voted = actionService.existsBy(Vote.ACTION_TYPE, vote.getId(), null, date,
                        userId, null, null);
                break;
            default:
                throw new IllegalStateException("Vote mode not support: " + vote.getMode());
        }
        if (voted) {
            return Responses.status(1004, request, "error.vote.alreadyVoted");
        }
        service.cast(params.id, optionIds, site.getId(), userId, ip, cookie);
        return Responses.ok();
    }

    private void validateCast(Vote vote, List<Long> optionIds) {
        if (optionIds == null) {
            throw new Http400Exception("'optionIds' can not be empty");
        }
        List<Long> voteOptionIds = vote.getOptions().stream().map(VoteOptionBase::getId)
                .collect(Collectors.toList());
        for (Long optionId : optionIds) {
            if (!voteOptionIds.contains(optionId)) {
                throw new Http400Exception("'optionId' does not belong vote. optionId: " + optionId);
            }
        }
    }

    public static class VoteParams {
        @NotNull
        private Long id;
        @NotNull
        private Object options;

        public List<Long> getOptionIds() {
            if (options instanceof String[]) {
                return Arrays.stream((String[]) options).map(Long::valueOf).collect(Collectors.toList());
            } else if (options instanceof String) {
                return Collections.singletonList(Long.valueOf((String) options));
            } else {
                throw new Http400Exception("options type must be String[] or String: " + options.getClass().getName());
            }
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Object getOptions() {
            return options;
        }

        public void setOptions(Object options) {
            this.options = options;
        }
    }
}
