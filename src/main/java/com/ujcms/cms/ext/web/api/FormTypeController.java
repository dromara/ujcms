package com.ujcms.cms.ext.web.api;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.commons.query.QueryUtils.QUERY_PREFIX;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.service.FormTypeService;
import com.ujcms.cms.ext.service.args.FormTypeArgs;
import com.ujcms.cms.ext.web.directive.FormTypeListDirective;
import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.Views;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 表单类型接口 Controller
 *
 * @author PONY
 */
@Tag(name = "表单类型接口")
@RestController
@RequestMapping({API + "/form-type", FRONTEND_API + "/form-type"})
public class FormTypeController {
    private final FormTypeService service;
    private final SiteResolver siteResolver;

    public FormTypeController(FormTypeService service, SiteResolver siteResolver) {
        this.service = service;
        this.siteResolver = siteResolver;
    }

    private <T> T query(HttpServletRequest request, BiFunction<FormTypeArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        FormTypeArgs args = FormTypeArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        FormTypeListDirective.assemble(args, params, site.getId());
        return handle.apply(args, params);
    }

    /**
     * ## 标签使用示例
     * 
     * <pre>
     * ```
     * [@FormTypeList limit='5'; beans]
     *   [#list beans as bean]
     *     <p>${bean.name}</p>
     *   [/#list]
     * [/@FormTypeList]
     * ```
     * </pre>
     */
    @Operation(summary = "表单类型列表_FormTypeList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "mode", description = "模式。短整型(Short)。0:前台游客,1:前台登录用户,2:仅后台用户。多个用英文逗号分隔，如 '0,1,2'；全部使用空串 ''。默认：'0,1'",
            schema = @Schema(type = "string", format = "short"))
    @Parameter(in = ParameterIn.QUERY, name = "isViewable", description = "是否前台可查看。布尔型（Boolean）。true: 可查看, false: 不可查看。默认：全部",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isEnabled", description = "是否启用。布尔型（Boolean）。可选值：all(全部), false(禁用)。默认：启用",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<FormType> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return service.selectList(args, offset, limit);
        });
    }
}
