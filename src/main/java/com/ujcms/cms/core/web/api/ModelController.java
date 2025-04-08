package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.web.directive.ModelListDirective;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 模型前台 接口
 *
 * @author PONY
 */
@Tag(name = "模型接口")
@RestController
@RequestMapping({API + "/model", FRONTEND_API + "/model"})
public class ModelController {
    private final SiteResolver siteResolver;
    private final ModelService modelService;

    public ModelController(SiteResolver siteResolver, ModelService modelService) {
        this.siteResolver = siteResolver;
        this.modelService = modelService;
    }

    @Operation(summary = "模型列表_ModelList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "type", description = "类型",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @GetMapping
    public List<Model> list(HttpServletRequest request) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return ModelListDirective.query(params, site.getId(), modelService);
    }

    @Operation(summary = "模型对象_Model")
    @ApiResponses(value = {@ApiResponse(description = "模型对象")})
    @GetMapping("/{id:[\\d]+}")
    public Model show(@Parameter(description = "模型ID") @PathVariable Long id) {
        return Optional.ofNullable(modelService.select(id))
                .orElseThrow(() -> new Http404Exception("Model not found. ID: " + id));
    }

}
