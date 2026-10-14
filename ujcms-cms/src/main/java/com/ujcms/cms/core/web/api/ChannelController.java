package com.ujcms.cms.core.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.component.ViewCountService;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.ChannelBuffer;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ChannelBufferService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.directive.ChannelListDirective;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http401Exception;
import com.ujcms.common.web.exception.Http403Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.cms.core.web.frontend.ChannelController.hasAccessPermission;

/**
 * 栏目前台 接口
 *
 * @author PONY
 */
@Tag(name = "栏目接口")
@RestController
@RequestMapping({API + "/channel", FRONTEND_API + "/channel"})
public class ChannelController {
    private final SiteResolver siteResolver;
    private final ChannelService channelService;
    private final ChannelBufferService bufferService;
    private final ViewCountService viewCountService;
    private final GroupService groupService;

    public ChannelController(SiteResolver siteResolver, ChannelService channelService,
                             ChannelBufferService bufferService, ViewCountService viewCountService,
                             GroupService groupService) {
        this.siteResolver = siteResolver;
        this.channelService = channelService;
        this.bufferService = bufferService;
        this.viewCountService = viewCountService;
        this.groupService = groupService;
    }

    /**
     * ## 标签使用示例
     * ### 获取一级栏目列表
     * <pre>
     * ```
     * [@ChannelList isNav='true'; channels]
     *   [#list channels as bean]
     *   <a href="${bean.url}">${bean.name}</a>
     *   [/#list]
     * [/@ChannelList]
     * ```
     * </pre>
     * ### 获取子栏目列表
     * <pre>
     * ```
     * [@ChannelList parentId='23' isNav='true'; channels]
     *   [#list channels as bean]
     *   <a href="${bean.url}">${bean.name}</a>
     *   [/#list]
     * [/@ChannelList]
     * ```
     * </pre>
     * ### 获取一级和二级栏目列表
     * <pre>
     * ```
     * [@ChannelList isNav='true'; channels]
     * <ul>
     *   [#list channels as parent]
     *   <li>
     *     <a href="${parent.url}">${parent.name}</a>
     *     [#if parent.hasChildren]
     *     [@ChannelList parentId=parent.id isNav='true'; subs]
     *     <ul>
     *       [#list subs as sub]
     *       <li><a href="${sub.url}">${sub.name}</a></li>
     *       [/#list]
     *     </ul>
     *     [/@ChannelList]
     *     [/#if]
     *   </li>
     *   [/#list]
     * </ul>
     * [/@ChannelList]
     * ```
     * </pre>
     */
    @Operation(summary = "栏目列表_ChannelList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "parent", description = "上级栏目别名",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "parentId", description = "上级栏目ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isNav", description = "是否导航。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isReal", description = "是否文章栏目。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllowSearch", description = "是否可搜索。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeChildren", description = "是否包含子栏目。设为 `true` 时包含所有后代栏目（子级、孙级等），设为 `false` 时仅包含直接子栏目。默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Channel> list(HttpServletRequest request) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        ChannelArgs args = ChannelArgs.of(QueryUtils.getPublicQueryMap(params));
        ChannelListDirective.assemble(args, params, site.getId(), channelService);
        return ChannelListDirective.selectList(channelService, args, params);
    }

    /**
     * ## 标签使用示例
     * <pre>
     * ```
     * [@Channel id='123'; bean]
     *   <a href="${bean.url}">${bean.name}</a>
     * [/@Channel]
     * ```
     * </pre>
     */
    @Operation(summary = "栏目对象_Channel")
    @ApiResponses(value = {@ApiResponse(description = "栏目对象")})
    @GetMapping("/{id:[\\d]+}")
    public Channel show(@Parameter(description = "栏目ID") @PathVariable Long id) {
        return validateAccess(channelService.select(id));
    }

    /**
     * ## 标签使用示例
     * <pre>
     * ```
     * [@Channel slug='news'; bean]
     *   <a href="${bean.url}">${bean.name}</a>
     * [/@Channel]
     * ```
     * </pre>
     */
    @Operation(summary = "栏目对象BySlug_Channel")
    @ApiResponses(value = {@ApiResponse(description = "栏目对象")})
    @GetMapping("/slug/{slug}")
    public Channel slug(@Parameter(description = "栏目别名") @PathVariable String slug,
                         @Parameter(description = "站点ID") Long siteId,
                         HttpServletRequest request) {
        if (siteId == null) {
            siteId = siteResolver.resolve(request).getId();
        }
        return validateAccess(channelService.findBySiteIdAndSlug(siteId, slug));
    }

    /**
     * 检查栏目浏览权限。与前台栏目页保持一致：未登录用户使用游客组权限
     */
    @Nullable
    private Channel validateAccess(@Nullable Channel channel) {
        if (channel == null) {
            return null;
        }
        User user = Contexts.findCurrentUser();
        if (user == null) {
            Group anonGroup = groupService.getAnonymous();
            if (!hasAccessPermission(anonGroup, channel.getSiteId(), channel.getId(), groupService)) {
                throw new Http401Exception();
            }
        } else if (!hasAccessPermission(user.getGroup(), channel.getSiteId(), channel.getId(), groupService)) {
            throw new Http403Exception("Channel access forbidden. ID: " + channel.getId());
        }
        return channel;
    }

    @Operation(summary = "获取栏目浏览次数")
    @GetMapping("/view/{id:[\\d]+}")
    public long view(@Parameter(description = "栏目ID") @PathVariable Long id) {
        return viewCountService.viewChannel(id);
    }

    @Operation(summary = "获取栏目统计数据")
    @GetMapping("/buffer/{id:[\\d]+}")
    public ChannelBuffer buffer(@Parameter(description = "栏目ID") @PathVariable Integer id) {
        return bufferService.select(id);
    }
}
