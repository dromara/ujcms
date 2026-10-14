package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.Link;
import com.ujcms.cms.ext.service.LinkGroupService;
import com.ujcms.cms.ext.service.LinkService;
import com.ujcms.cms.ext.service.args.LinkArgs;
import com.ujcms.cms.ext.web.directive.LinkListDirective;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 链接 Controller
 *
 * @author PONY
 */
@Tag(name = "链接接口")
@RestController
@RequestMapping({API + "/link", FRONTEND_API + "/link"})
public class LinkController {
    private final LinkService service;
    private final LinkGroupService groupService;
    private final SiteResolver siteResolver;

    public LinkController(LinkService service, LinkGroupService groupService,
                          SiteResolver siteResolver) {
        this.service = service;
        this.groupService = groupService;
        this.siteResolver = siteResolver;
    }

    private <T> T query(HttpServletRequest request, BiFunction<LinkArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        LinkArgs args = LinkArgs.of(QueryUtils.getPublicQueryMap(params));
        LinkListDirective.assemble(args, params, site.getId(), groupService);
        // 前台接口只允许获取启用的数据
        args.enabled(true);
        return handle.apply(args, params);
    }

    @Operation(summary = "链接列表_LinkList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "groupId", description = "链接组ID。默认获取全部链接组",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "groupAlias", description = "链接组别名。按别名获取指定链接组的链接",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Link> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return service.selectList(args, offset, limit);
        });
    }

}
