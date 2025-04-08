package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.service.args.TagArgs;
import com.ujcms.cms.core.web.directive.TagListDirective;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.commons.db.MyBatis.springPage;
import static com.ujcms.commons.query.QueryUtils.QUERY_PREFIX;

/**
 * Tag前台 接口
 *
 * @author PONY
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tag接口")
@RestController
@RequestMapping({API + "/tag", FRONTEND_API + "/tag"})
public class TagController {
    private final SiteResolver siteResolver;
    private final TagService service;

    public TagController(SiteResolver siteResolver, TagService service) {
        this.siteResolver = siteResolver;
        this.service = service;
    }

    private <T> T query(HttpServletRequest request, BiFunction<TagArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        TagArgs args = TagArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        TagListDirective.assemble(args, params, site.getId());
        return handle.apply(args, params);
    }

    @Operation(summary = "Tag列表_TagList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "name", description = "标签名称",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "isReferred", description = "是否被引用。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点的Tag。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @GetMapping
    public List<Tag> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return service.selectList(args, offset, limit);
        });
    }

    @Operation(summary = "Tag分页_TagPage")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "name", description = "标签名称",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "isReferred", description = "是否被引用。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点的Tag。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @GetMapping("/page")
    public Page<Tag> page(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int page = Directives.getPage(params);
            int pageSize = Directives.getPageSize(params);
            return springPage(service.selectPage(args, page, pageSize));
        });
    }

    @Operation(summary = "获取Tag对象")
    @ApiResponses(value = {@ApiResponse(description = "Tag对象")})
    @GetMapping("/{id:[\\d]+}")
    public Tag show(@Parameter(description = "TagID") @PathVariable Long id) {
        return Optional.ofNullable(service.select(id)).orElseThrow(() ->
                new Http404Exception(Tag.NOT_FOUND + id));
    }
}
