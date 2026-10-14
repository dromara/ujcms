package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.DictService;
import com.ujcms.cms.core.web.directive.DictListDirective;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.query.QueryUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
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
    private final SiteResolver siteResolver;
    private final DictService dictService;

    public DictController(SiteResolver siteResolver, DictService dictService) {
        this.siteResolver = siteResolver;
        this.dictService = dictService;
    }

    @Operation(summary = "字典列表_DictList")
    @Parameter(in = ParameterIn.QUERY, name = "typeId", description = "类型ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "type", description = "类型别名",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @GetMapping
    public List<Dict> list(HttpServletRequest request) {
        // 公开接口无站点拦截器：按 Host 解析当前站点，可见范围 = 本站 ∪ 全局
        Site site = siteResolver.resolve(request);
        Map<String, String> params = new HashMap<>(QueryUtils.getParams(request.getQueryString()));
        QueryParser.validatePublicOrderBy(params.get(Directives.ORDER_BY));
        // 前台接口只允许获取启用的数据
        params.put("isEnabled", "true");
        return DictListDirective.query(params, site.getId(), dictService);
    }

}
