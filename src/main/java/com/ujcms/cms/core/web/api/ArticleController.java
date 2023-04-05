package com.ujcms.cms.core.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.service.ArticleBufferService;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.Utils;
import com.ujcms.cms.core.web.directive.ArticleListDirective;
import com.ujcms.cms.core.web.directive.ArticleNextDirective;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.util.query.QueryUtils;
import com.ujcms.util.web.Views;
import com.ujcms.util.web.exception.Http401Exception;
import com.ujcms.util.web.exception.Http403Exception;
import com.ujcms.util.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.util.db.MyBatis.springPage;
import static com.ujcms.util.query.QueryUtils.QUERY_PREFIX;

/**
 * 文章前台 接口
 *
 * @author PONY
 */
@Tag(name = "ArticleController", description = "文章接口")
@RestController
@RequestMapping({API + "/article", FRONTEND_API + "/article"})
public class ArticleController {
    private final SiteResolver siteResolver;
    private final GroupService groupService;
    private final ChannelService channelService;
    private final ArticleService articleService;
    private final ArticleBufferService bufferService;
    private final Props props;

    public ArticleController(SiteResolver siteResolver, GroupService groupService,
                             ChannelService channelService, ArticleService articleService,
                             ArticleBufferService bufferService, Props props) {
        this.siteResolver = siteResolver;
        this.groupService = groupService;
        this.channelService = channelService;
        this.articleService = articleService;
        this.bufferService = bufferService;
        this.props = props;
    }

    private <T> T query(HttpServletRequest request, BiFunction<ArticleArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        ArticleArgs args = ArticleArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        ArticleListDirective.assemble(args, params, site.getId(), channelService);
        args.customsQueryMap(QueryUtils.getCustomsQueryMap(params));
        return handle.apply(args, params);
    }

    @Operation(summary = "获取文章列表")
    @Parameters({
            @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "channel", description = "栏目别名",
                    schema = @Schema(type = "string")),
            @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "栏目ID",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "tagId", description = "TagID",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "beginPublishDate", description = "开始发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
                    schema = @Schema(type = "string", format = "date-time")),
            @Parameter(in = ParameterIn.QUERY, name = "endPublishDate", description = "结束发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
                    schema = @Schema(type = "string", format = "date-time")),
            @Parameter(in = ParameterIn.QUERY, name = "isWithImage", description = "是否有标题图。如：`true` `false`",
                    schema = @Schema(type = "boolean")),
            @Parameter(in = ParameterIn.QUERY, name = "title", description = "标题",
                    schema = @Schema(type = "string")),
            @Parameter(in = ParameterIn.QUERY, name = "text", description = "正文",
                    schema = @Schema(type = "string")),
            @Parameter(in = ParameterIn.QUERY, name = "excludeId", description = "不包含的文章ID。多个用英文逗号分隔，如`1,2,5`",
                    schema = @Schema(type = "string", format = "int32 array")),
            @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubChannel", description = "是否包含子栏目的文章。如：`true` `false`，默认`true`",
                    schema = @Schema(type = "boolean")),
            @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubSite", description = "是否包含子站点的文章。如：`true` `false`，默认`false`",
                    schema = @Schema(type = "boolean")),
            @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点文章。如：`true` `false`，默认`false`",
                    schema = @Schema(type = "boolean")),
            @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
                    schema = @Schema(type = "integer", format = "int32")),
    })
    @JsonView(Views.List.class)
    @GetMapping
    public List<Article> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            List<Article> list = articleService.selectList(args, offset, limit);
            list.forEach(article -> article.getChannel().getPaths().forEach(channelService::fetchFirstData));
            return list;
        });
    }

    @Operation(summary = "获取文章分页")
    @Parameters({
            @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "channel", description = "栏目别名",
                    schema = @Schema(type = "string")),
            @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "栏目ID",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "tagId", description = "TagID",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "beginPublishDate", description = "开始发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
                    schema = @Schema(type = "string", format = "date-time")),
            @Parameter(in = ParameterIn.QUERY, name = "endPublishDate", description = "接受发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
                    schema = @Schema(type = "string", format = "date-time")),
            @Parameter(in = ParameterIn.QUERY, name = "isWithImage", description = "是否有标题图。如：`true` `false`",
                    schema = @Schema(type = "boolean")),
            @Parameter(in = ParameterIn.QUERY, name = "title", description = "标题",
                    schema = @Schema(type = "string")),
            @Parameter(in = ParameterIn.QUERY, name = "text", description = "正文",
                    schema = @Schema(type = "string")),
            @Parameter(in = ParameterIn.QUERY, name = "excludeId", description = "不包含的文章ID。多个用英文逗号分隔，如`1,2,5`",
                    schema = @Schema(type = "string", format = "int32 array")),
            @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubChannel", description = "是否包含子栏目的文章。如：`true` `false`，默认`true`",
                    schema = @Schema(type = "boolean")),
            @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubSite", description = "是否包含子站点的文章。如：`true` `false`，默认`false`",
                    schema = @Schema(type = "boolean")),
            @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点文章。如：`true` `false`，默认`false`",
                    schema = @Schema(type = "boolean")),
            @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
                    schema = @Schema(type = "integer", format = "int32")),
    })
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Article> page(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int page = Directives.getPage(params);
            int pageSize = Directives.getPageSize(params);
            Page<Article> pagedList = springPage(articleService.selectPage(args, page, pageSize));
            pagedList.getContent().forEach(article ->
                    article.getChannel().getPaths().forEach(channelService::fetchFirstData));
            return pagedList;
        });
    }

    @Operation(summary = "获取文章对象")
    @ApiResponses(value = {@ApiResponse(description = "文章对象")})
    @GetMapping("/{id:[\\d]+}")
    public Article show(@Parameter(description = "文章ID") @PathVariable Integer id) {
        Article article = articleService.select(id);
        if (article == null) {
            throw new Http404Exception("Article not found. ID: " + id);
        }
        if (!article.isNormal()) {
            throw new Http403Exception("Article status forbidden. ID: " + id);
        }
        article.getChannel().getPaths().forEach(channelService::fetchFirstData);
        User user = Contexts.findCurrentUser();
        return checkAccessPermission(id, article, user, groupService);
    }

    public static Article checkAccessPermission(Integer id, Article article, User user, GroupService groupService) {
        if (user == null) {
            Group anonymous = groupService.getAnonymous();
            if (!anonymous.getAllAccessPermission()) {
                List<Integer> anonChannelIds = groupService.listAccessPermissions(Group.ANONYMOUS_ID, article.getSiteId());
                if (!anonChannelIds.contains(article.getChannelId())) {
                    throw new Http401Exception();
                }
            }
            return article;
        }
        if (user.hasAllAccessPermission()) {
            return article;
        }
        List<Integer> channelIds = groupService.listAccessPermissions(user.getGroupId(), article.getSiteId());
        if (!channelIds.contains(article.getChannelId())) {
            throw new Http403Exception("Article access forbidden. ID: " + id);
        }
        return article;
    }

    @Operation(summary = "获取下一篇文章")
    @Parameters({
            @Parameter(in = ParameterIn.QUERY, name = "id", description = "文章ID",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "文章栏目ID",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "publishDate", description = "文章发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
                    schema = @Schema(type = "string", format = "date-time")),
    })
    @GetMapping("/next")
    public Article next(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return ArticleNextDirective.query(params, articleService::findNext);
    }

    @Operation(summary = "获取上一篇文章")
    @Parameters({
            @Parameter(in = ParameterIn.QUERY, name = "id", description = "文章ID",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "文章栏目ID",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "publishDate", description = "文章发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
                    schema = @Schema(type = "string", format = "date-time")),
    })
    @GetMapping("/prev")
    public Article prev(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return ArticleNextDirective.query(params, articleService::findPrev);
    }

    @Operation(summary = "获取文章浏览次数")
    @GetMapping("/view/{id:[\\d]+}")
    public long view(@Parameter(description = "文章ID") @PathVariable Integer id) {
        return bufferService.updateViews(id, 1);
    }

    @Operation(summary = "顶文章")
    @ApiResponses(value = {@ApiResponse(description = "文章总顶次数")})
    @PostMapping("/up/{id:[\\d]+}")
    public int up(@Parameter(description = "文章ID") @PathVariable Integer id) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            return 0;
        }
        int ups = buffer.getUps() + 1;
        buffer.setUps(ups);
        bufferService.update(buffer);
        return ups;
    }

    @Operation(summary = "踩文章")
    @ApiResponses(value = {@ApiResponse(description = "文章总踩次数")})
    @PostMapping("/down/{id:[\\d]+}")
    public int down(@Parameter(description = "文章ID") @PathVariable Integer id) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            return 0;
        }
        int downs = buffer.getDowns() + 1;
        buffer.setDowns(downs);
        bufferService.update(buffer);
        return downs;
    }

    @Operation(summary = "获取下载参数")
    @ApiResponses(value = {@ApiResponse(description = "当前时间和KEY。如：`time=123&key=abc`")})
    @GetMapping("/download-params/{id:[\\d]+}")
    public String downloadParam(@Parameter(description = "文章ID") @PathVariable Integer id) {
        long time = System.currentTimeMillis();
        String secret = props.getDownloadSecret();
        String key = Utils.getDownloadKey(id, time, secret);
        return "time=" + time + "&key=" + key;
    }

    @Operation(summary = "记录下载次数")
    @ApiResponses(value = {@ApiResponse(description = "文章总下载次数")})
    @PostMapping("/download/{id:[\\d]+}")
    public int download(@Parameter(description = "文章ID") @PathVariable Integer id) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            return 0;
        }
        int downloads = buffer.getDownloads() + 1;
        buffer.setDownloads(downloads);
        bufferService.update(buffer);
        return downloads;
    }

    @Operation(summary = "获取文章统计数据")
    @GetMapping("/buffer/{id:[\\d]+}")
    public ArticleBuffer buffer(@Parameter(description = "文章ID") @PathVariable Integer id) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            throw new Http404Exception("ArticleBuffer not found. id=" + id);
        }
        return buffer;
    }
}
