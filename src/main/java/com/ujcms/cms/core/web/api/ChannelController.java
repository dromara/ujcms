package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.ChannelBuffer;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ChannelBufferService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.core.web.directive.ChannelListDirective;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.util.query.QueryUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
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

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.util.query.QueryUtils.QUERY_PREFIX;

/**
 * 栏目前台 接口
 *
 * @author PONY
 */
@Tag(name = "ChannelController", description = "栏目接口")
@RestController
@RequestMapping({API + "/channel", FRONTEND_API + "/channel"})
public class ChannelController {
    private final SiteResolver siteResolver;
    private final ChannelService channelService;
    private final ChannelBufferService bufferService;

    public ChannelController(SiteResolver siteResolver, ChannelService channelService,
                             ChannelBufferService bufferService) {
        this.siteResolver = siteResolver;
        this.channelService = channelService;
        this.bufferService = bufferService;
    }

    @Operation(summary = "获取栏目列表")
    @Parameters({
            @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "parent", description = "上级栏目别名",
                    schema = @Schema(type = "string")),
            @Parameter(in = ParameterIn.QUERY, name = "parentId", description = "上级栏目ID",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "isNav", description = "是否导航。如：`true` `false`",
                    schema = @Schema(type = "boolean")),
            @Parameter(in = ParameterIn.QUERY, name = "isIncludeChildren", description = "是否包含子栏目。如：`true` `false`，默认`false`",
                    schema = @Schema(type = "boolean")),
            @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
                    schema = @Schema(type = "integer", format = "int32")),
    })
    @GetMapping
    public List<Channel> list(HttpServletRequest request) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        ChannelArgs args = ChannelArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        ChannelListDirective.assemble(args, params, site.getId(), channelService);
        args.customsQueryMap(QueryUtils.getCustomsQueryMap(params));
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        List<Channel> list = channelService.selectList(args, offset, limit);
        list.forEach(channel -> {
            channel.getPaths().forEach(channelService::fetchFirstData);
            channel.getChildren().forEach(channelService::fetchFirstData);
        });
        return list;
    }

    @Operation(summary = "获取栏目对象")
    @ApiResponses(value = {@ApiResponse(description = "栏目对象")})
    @GetMapping("/{id:[\\d]+}")
    public Channel show(@Parameter(description = "栏目ID") @PathVariable Integer id) {
        Channel channel = channelService.select(id);
        if (channel != null) {
            channel.getPaths().forEach(channelService::fetchFirstData);
            channel.getChildren().forEach(channelService::fetchFirstData);
        }
        return channel;
    }

    @Operation(summary = "获取栏目对象")
    @ApiResponses(value = {@ApiResponse(description = "栏目对象")})
    @GetMapping("/alias/{alias}")
    public Channel alias(@Parameter(description = "栏目别名") @PathVariable String alias,
                         @Parameter(description = "站点ID") Integer siteId,
                         HttpServletRequest request) {
        if (siteId == null) {
            siteId = siteResolver.resolve(request).getId();
        }
        Channel channel = channelService.findBySiteIdAndAlias(siteId, alias);
        if (channel != null) {
            channel.getPaths().forEach(channelService::fetchFirstData);
            channel.getChildren().forEach(channelService::fetchFirstData);
        }
        return channel;
    }

    @Operation(summary = "获取栏目浏览次数")
    @GetMapping("/view/{id:[\\d]+}")
    public long view(@Parameter(description = "栏目ID") @PathVariable Integer id) {
        return bufferService.updateViews(id, 1);
    }

    @Operation(summary = "获取栏目统计数据")
    @GetMapping("/buffer/{id:[\\d]+}")
    public ChannelBuffer buffer(@Parameter(description = "栏目ID") @PathVariable Integer id) {
        return bufferService.select(id);
    }
}
