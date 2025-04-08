package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Form;
import com.ujcms.cms.ext.service.FormService;
import com.ujcms.cms.ext.service.FormTypeService;
import com.ujcms.cms.ext.service.args.FormArgs;
import com.ujcms.cms.ext.web.directive.FormListDirective;
import com.ujcms.commons.query.QueryUtils;
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
 * 表单 Controller
 *
 * @author PONY
 */
@Tag(name = "表单接口")
@RestController
@RequestMapping({API + "/form", FRONTEND_API + "/form"})
public class FormController {
    private final FormTypeService typeService;
    private final FormService service;

    public FormController(FormTypeService typeService, FormService service) {
        this.typeService = typeService;
        this.service = service;
    }

    private <T> T query(HttpServletRequest request, BiFunction<FormArgs, Map<String, String>, T> handle) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        FormArgs args = FormArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        FormListDirective.assemble(args, params, typeService);
        return handle.apply(args, params);
    }

    @Operation(summary = "表单列表_FormList")
    @Parameter(in = ParameterIn.QUERY, name = "typeId", description = "类型ID。必选项",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "orgId", description = "组织ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "userId", description = "用户ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Form> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return service.selectList(args, offset, limit);
        });
    }

    @Operation(summary = "表单分页_FormPage")
    @Parameter(in = ParameterIn.QUERY, name = "typeId", description = "类型ID。必选项",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "orgId", description = "组织ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "userId", description = "用户ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Form> page(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int page = Directives.getPage(params);
            int pageSize = Directives.getPageSize(params);
            return springPage(service.selectPage(args, page, pageSize));
        });
    }

    @Operation(summary = "获取表单对象")
    @ApiResponses(value = {@ApiResponse(description = "表单对象")})
    @GetMapping("/{id:[\\d]+}")
    public Form show(@Parameter(description = "表单ID") @PathVariable Long id) {
        Form bean = Optional.ofNullable(service.select(id))
                .orElseThrow(() -> new Http404Exception("Form not found. ID: " + id));
        if (Boolean.FALSE.equals(bean.getType().getViewable())) {
            throw new Http400Exception("FormType is not viewable. ID: " + bean.getType().getId());
        }
        return bean;
    }
}
