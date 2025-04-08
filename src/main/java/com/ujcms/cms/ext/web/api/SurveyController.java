package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.base.UserBase;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.Survey;
import com.ujcms.cms.ext.domain.SurveyItem;
import com.ujcms.cms.ext.domain.Vote;
import com.ujcms.cms.ext.domain.base.SurveyOptionBase;
import com.ujcms.cms.ext.service.SurveyFeedbackService;
import com.ujcms.cms.ext.service.SurveyService;
import com.ujcms.cms.ext.service.args.SurveyArgs;
import com.ujcms.cms.ext.web.directive.SurveyListDirective;
import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.Views;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
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
 * 调查问卷前台 接口
 *
 * @author PONY
 */
@Tag(name = "调查问卷接口")
@RestController
@RequestMapping({API + "/survey", FRONTEND_API + "/survey"})
public class SurveyController {
    private final SiteResolver siteResolver;
    private final SurveyService service;
    private final SurveyFeedbackService feedbackService;

    public SurveyController(SiteResolver siteResolver, SurveyService service, SurveyFeedbackService feedbackService) {
        this.siteResolver = siteResolver;
        this.service = service;
        this.feedbackService = feedbackService;
    }

    private <T> T query(HttpServletRequest request, BiFunction<SurveyArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        SurveyArgs args = SurveyArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        SurveyListDirective.assemble(args, params, site.getId());
        return handle.apply(args, params);
    }

    @Operation(summary = "调查问卷列表_SurveyList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isWithinDate", description = "是否在调查期限内。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点调查问卷。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Survey> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return service.selectList(args, offset, limit);
        });
    }

    @Operation(summary = "调查问卷分页_SurveyPage")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isWithinDate", description = "是否在调查期限内。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点调查问卷。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Survey> page(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int page = Directives.getPage(params);
            int pageSize = Directives.getPageSize(params);
            return springPage(service.selectPage(args, page, pageSize));
        });
    }

    @Operation(summary = "调查问卷对象_Survey")
    @ApiResponses(value = {@ApiResponse(description = "调查问卷对象")})
    @GetMapping("/{id:[\\d]+}")
    public Survey show(@Parameter(description = "调查问卷ID") @PathVariable Long id) {
        Survey bean = service.select(id);
        validateBean(id, bean);
        return bean;
    }

    public static void validateBean(Long id, Survey bean) {
        if (bean == null) {
            throw new Http404Exception("Survey not found. ID: " + id);
        }
        if (Boolean.FALSE.equals(bean.getEnabled())) {
            throw new Http404Exception("Survey not enabled. ID: " + id);
        }
    }

    @Operation(summary = "投票")
    @ApiResponse(description = "message: 提示信息。status: 状态码。0，成功；-1，失败；" +
            "1000，调查问卷未启用；" +
            "1001，调查问卷未开始；" +
            "1002，调查问卷已结束；" +
            "1003，本投票需先登录；" +
            "1004，已经投过票；")
    @PostMapping("/cast")
    public ResponseEntity<Responses.Body> cast(@RequestBody @Valid SurveyParams params,
                                               HttpServletRequest request, HttpServletResponse response) {
        Survey survey = service.select(params.id);
        if (survey == null) {
            throw new Http400Exception("Survey not found. id: " + params.id);
        }
        switch (survey.getStatus()) {
            case Vote.STATUS_DISABLED:
                return Responses.status(1000, request, "error.vote.disabled", params.id);
            case Vote.STATUS_NOT_STARTED:
                return Responses.status(1001, request, "error.vote.notStarted", params.id);
            case Vote.STATUS_HAS_ENDED:
                return Responses.status(1002, request, "error.vote.hasEnded", params.id);
            default:
        }
        Site site = siteResolver.resolve(request);
        long cookie = Constants.retrieveIdentityCookie(request, response);
        String ip = Servlets.getRemoteAddr(request);
        Long userId = Optional.ofNullable(Contexts.findCurrentUser()).map(UserBase::getId).orElse(null);
        OffsetDateTime date = survey.getInterval() > 0 ? OffsetDateTime.now().minusDays(survey.getInterval()) : null;
        // 已经投过票
        boolean voted;
        switch (survey.getMode()) {
            case Survey.MODE_COOKIE:
                voted = feedbackService.existsBy(survey.getId(), date, null, null, cookie);
                break;
            case Survey.MODE_IP:
                voted = feedbackService.existsBy(survey.getId(), date, null, ip, null);
                break;
            case Survey.MODE_USER:
                if (userId == null) {
                    return Responses.status(1003, request, "error.vote.notLogin");
                }
                voted = feedbackService.existsBy(survey.getId(), date, userId, null, null);
                break;
            default:
                throw new IllegalStateException("Vote mode not support: " + survey.getMode());
        }
        if (voted) {
            return Responses.status(1004, request, "error.vote.alreadyVoted");
        }
        Map<Long, List<Long>> optionMap = getOptionMap(survey, params.items);
        Map<Long, String> essayMap = getEssayMap(survey, params.items);
        service.cast(params.id, optionMap, essayMap, site.getId(), userId, ip, cookie);
        return Responses.ok();
    }

    private Map<Long, List<Long>> getOptionMap(Survey survey, Map<String, Object> items) {
        Map<Long, List<Long>> optionMap = new HashMap<>(16);
        for (SurveyItem item : survey.getItems()) {
            if (Boolean.TRUE.equals(item.getEssay())) {
                continue;
            }
            Object value = items.get(String.valueOf(item.getId()));
            List<Long> optionIds;
            if (value instanceof List) {
                if (Boolean.FALSE.equals(item.getMultiple())) {
                    throw new Http400Exception("Only one option can be selected.");
                }
                optionIds = ((List<?>) value).stream().map(Object::toString).map(Long::valueOf).collect(Collectors.toList());
            }else if (value instanceof String) {
                optionIds = Collections.singletonList(Long.valueOf((String) value));
            } else {
                throw new Http400Exception("Option is invalid: " + value);
            }
            List<Long> itemOptionsIds = item.getOptions().stream().map(SurveyOptionBase::getId)
                    .collect(Collectors.toList());
            for (Long optionId : optionIds) {
                if (!itemOptionsIds.contains(optionId)) {
                    throw new Http400Exception("'optionId' does not belong SurveyItem. optionId: " + optionId);
                }
            }
            optionMap.put(item.getId(), optionIds);
        }
        return optionMap;
    }

    private Map<Long, String> getEssayMap(Survey survey, Map<String, Object> items) {
        Map<Long, String> essayMap = new HashMap<>(16);
        for (SurveyItem item : survey.getItems()) {
            if (Boolean.FALSE.equals(item.getEssay())) {
                continue;
            }
            Object value = items.get(String.valueOf(item.getId()));
            if (!(value instanceof String)) {
                throw new Http400Exception("Answer must be a string.");
            }
            if (StringUtils.isBlank((String) value)) {
                throw new Http400Exception("Answer cannot be empty.");
            }
            essayMap.put(item.getId(), (String) value);
        }
        return essayMap;
    }

    public static class SurveyParams {
        @NotNull
        private Long id;
        @NotNull
        private Map<String, Object> items;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Map<String, Object> getItems() {
            return items;
        }

        public void setItems(Map<String, Object> items) {
            this.items = items;
        }
    }
}
