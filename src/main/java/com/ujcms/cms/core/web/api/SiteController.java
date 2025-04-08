package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.component.ViewCountService;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.SiteBuffer;
import com.ujcms.cms.core.service.SiteBufferService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.cms.core.web.directive.SiteListDirective;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
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
import static com.ujcms.commons.query.QueryUtils.QUERY_PREFIX;

/**
 * 站点前台 接口
 *
 * @author PONY
 */
@Tag(name = "站点接口")
@RestController
@RequestMapping({API + "/site", FRONTEND_API + "/site"})
public class SiteController {
    private final SiteResolver siteResolver;
    private final SiteBufferService bufferService;
    private final ViewCountService viewCountService;
    private final SiteService service;

    public SiteController(SiteResolver siteResolver, SiteBufferService bufferService,
                          ViewCountService viewCountService, SiteService service) {
        this.siteResolver = siteResolver;
        this.bufferService = bufferService;
        this.viewCountService = viewCountService;
        this.service = service;
    }

    @Operation(summary = "站点列表_SiteList")
    @Parameter(in = ParameterIn.QUERY, name = "parentId", description = "上级站点ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeChildren", description = "是否包含子站点。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "status", description = "状态。0:正常, 1:关闭。默认：0（正常）",
            schema = @Schema(type = "string", format = "int16 array"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @GetMapping
    public List<Site> list(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        SiteArgs args = SiteArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        SiteListDirective.assemble(args, params);
        args.customsQueryMap(QueryUtils.getCustomsQueryMap(params));
        return SiteListDirective.selectList(service, args, params);
    }

    @Operation(summary = "获取当前站点对象。如果传递subDir参数，则通过子目录查询当前站点；否则通过当前域名获取对应站点对象，无法获取则返回默认站点")
    @GetMapping("/current")
    public Site current(@Parameter(description = "站点子目录") String subDir,
                        HttpServletRequest request) {
        if (StringUtils.isNotBlank(subDir)) {
            return Optional.ofNullable(service.findBySubDir(subDir))
                    .orElseThrow(() -> new Http404Exception("error.siteSubDirNotExist", subDir));
        }
        return siteResolver.resolve(request);
    }

    @Operation(summary = "站点对象_Site")
    @GetMapping("/{id:[\\d]+}")
    public Site show(@Parameter(description = "站点ID") @PathVariable Long id) {
        return service.select(id);
    }

    @Operation(summary = "获取站点缓冲对象，并记录浏览量")
    @GetMapping("/view/{id:[\\d]+}")
    public SiteBuffer view(@Parameter(description = "站点ID") @PathVariable Long id) {
        return viewCountService.viewSite(id);
    }

    @Operation(summary = "获取站点缓冲对象")
    @GetMapping("/buffer/{id:[\\d]+}")
    public SiteBuffer buffer(@Parameter(description = "站点ID") @PathVariable Long id) {
        SiteBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            throw new Http404Exception("SiteBuffer not found. id=" + id);
        }
        return buffer;
    }
}
