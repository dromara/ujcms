package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.web.directive.DictListDirective;
import com.ujcms.commons.query.QueryUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 字典前台 接口
 *
 * @author PONY
 */
@Tag(name = "字典接口")
@RestController
@RequestMapping({API + "/dict", FRONTEND_API + "/dict"})
public class DictController {
    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @Operation(summary = "字典列表_DictList")
    @Parameter(in = ParameterIn.QUERY, name = "typeId", description = "类型ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "type", description = "类型别名",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "isEnabled", description = "是否启用。可选值：`all`(全部), `false`(禁用), `true`(启用)。默认值：启用",
            schema = @Schema(type = "string", allowableValues = {"all", "false", "true"}, defaultValue = "true"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @GetMapping
    public List<Dict> list(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return DictListDirective.query(params, dictService);
    }

}
