package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.FriendLinkType;
import com.ujcms.cms.ext.service.FriendLinkTypeService;
import com.ujcms.cms.ext.service.args.FriendLinkTypeArgs;
import com.ujcms.cms.ext.web.directive.FriendLinkTypeListDirective;
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
import static com.ujcms.common.query.QueryUtils.QUERY_PREFIX;

/**
 * 友情链接类型 Controller
 *
 * @author PONY
 */
@Tag(name = "友情链接类型接口")
@RestController
@RequestMapping({API + "/friend-link-type", FRONTEND_API + "/friend-link-type"})
public class FriendLinkTypeController {
    private final FriendLinkTypeService service;
    private final SiteResolver siteResolver;

    public FriendLinkTypeController(FriendLinkTypeService service, SiteResolver siteResolver) {
        this.service = service;
        this.siteResolver = siteResolver;
    }

    private <T> T query(HttpServletRequest request, BiFunction<FriendLinkTypeArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        FriendLinkTypeArgs args = FriendLinkTypeArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        FriendLinkTypeListDirective.assemble(args, params, site.getId());
        return handle.apply(args, params);
    }

    @Operation(summary = "友情链接类型列表_FriendLinkTypeList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<FriendLinkType> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return service.selectList(args, offset, limit);
        });
    }

}
