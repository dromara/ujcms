package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.LeaderBoard;
import com.ujcms.cms.ext.service.LeaderBoardService;
import com.ujcms.cms.ext.service.args.LeaderBoardArgs;
import com.ujcms.cms.ext.web.directive.LeaderBoardListDirective;
import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 文章排行榜 Controller
 *
 * @author PONY
 */
@Tag(name = "文章排行榜接口")
@RestController
@RequestMapping({API + "/leader-board", FRONTEND_API + "/leader-board"})
public class LeaderBoardController {
    private final LeaderBoardService service;
    private final SiteService siteService;
    private final SiteResolver siteResolver;

    public LeaderBoardController(LeaderBoardService service, SiteService siteService, SiteResolver siteResolver) {
        this.service = service;
        this.siteService = siteService;
        this.siteResolver = siteResolver;
    }

    private <T> T query(HttpServletRequest request, BiFunction<LeaderBoardArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        LeaderBoardArgs args = LeaderBoardArgs.of();
        LeaderBoardListDirective.assemble(args, params, site.getId());
        return handle.apply(args, params);
    }

    @Operation(summary = "文章排行榜列表_LeaderBoardList")
    @Parameter(in = ParameterIn.QUERY, name = "type", description = "类型。channel:栏目排行榜, org:组织排行榜, user:用户排行榜。默认：栏目。",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isPublished", description = "是否发布。`true`:已发布文章, `false`:全部文章。默认：`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "period", description = "期间。none:无期间，即所有, today:当天, week:本周, month:本月, quarter:本季, year:本年。默认: none",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "begin", description = "开始日期。如：`2008-08-01` `2012-10-01 08:12:34`",
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(in = ParameterIn.QUERY, name = "end", description = "结束日期。如：`2008-08-01` `2012-10-01 08:12:34`",
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点文章。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<LeaderBoard> list(String type, HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return LeaderBoardListDirective.fetchList(service, siteService, type, args, offset, limit);
        });
    }

    @Operation(summary = "文章排行榜分页_LeaderBoardPage")
    @Parameter(in = ParameterIn.QUERY, name = "type", description = "类型。channel:栏目排行榜, org:组织排行榜, user:用户排行榜。默认：栏目。",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isPublished", description = "是否发布。`true`:已发布文章, `false`:全部文章。默认：`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "period", description = "期间。none:无期间，即所有, today:当天, week:本周, month:本月, quarter:本季, year:本年。默认: none",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "begin", description = "开始日期。如：`2008-08-01` `2012-10-01 08:12:34`",
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(in = ParameterIn.QUERY, name = "end", description = "结束日期。如：`2008-08-01` `2012-10-01 08:12:34`",
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点文章。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<LeaderBoard> page(String type, HttpServletRequest request) {
        return query(request, (args, params) -> {
            int page = Directives.getPage(params);
            int pageSize = Directives.getPageSize(params);
            return LeaderBoardListDirective.fetchPage(service, siteService, type, args, page, pageSize);
        });
    }
}
