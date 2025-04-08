package com.ujcms.cms.core.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.component.ViewCountService;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.domain.base.UserBase;
import com.ujcms.cms.core.service.*;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.Utils;
import com.ujcms.cms.core.web.directive.ArticleListDirective;
import com.ujcms.cms.core.web.directive.ArticleNextDirective;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.Views;
import com.ujcms.commons.web.exception.Http401Exception;
import com.ujcms.commons.web.exception.Http403Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.cms.core.web.frontend.ChannelController.hasAccessPermission;
import static com.ujcms.commons.db.MyBatis.springPage;
import static com.ujcms.commons.query.QueryUtils.QUERY_PREFIX;

/**
 * 文章前台 接口
 *
 * @author PONY
 */
@Tag(name = "文章接口")
@RestController
@RequestMapping({API + "/article", FRONTEND_API + "/article"})
public class ArticleController {
    private final SiteResolver siteResolver;
    private final ActionService actionService;
    private final GroupService groupService;
    private final ChannelService channelService;
    private final ArticleService articleService;
    private final ArticleBufferService bufferService;
    private final ViewCountService viewCountService;
    private final Props props;
    private final OrgService orgService;

    @Autowired
    public ArticleController(SiteResolver siteResolver, ActionService actionService, GroupService groupService,
                             ChannelService channelService, ArticleService articleService,
                             ArticleBufferService bufferService, ViewCountService viewCountService, Props props, OrgService orgService) {
        this.siteResolver = siteResolver;
        this.actionService = actionService;
        this.groupService = groupService;
        this.channelService = channelService;
        this.articleService = articleService;
        this.bufferService = bufferService;
        this.viewCountService = viewCountService;
        this.props = props;
        this.orgService = orgService;
    }

    private <T> T query(HttpServletRequest request, BiFunction<ArticleArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        ArticleArgs args = ArticleArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        ArticleListDirective.assemble(args, params, site.getId(), channelService);
        args.customsQueryMap(QueryUtils.getCustomsQueryMap(params));
        return handle.apply(args, params);
    }

    /**
     * 用于获取文章列表
     * <pre>
     * ```
     * [@ArticleList channel='news' limit='8'; beans]
     *   [#list beans as bean]
     *   <a href="${bean.url}">${bean.title}</a>
     *   [/#list]
     * [/@ArticleList]
     * ```
     * </pre>
     */
    @Operation(summary = "文章列表_ArticleList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "channel", description = "栏目别名。多个栏目别名可以用逗号分开，如`news,sports`",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "栏目ID。多个栏目ID可以用逗号分开，如`23,5,89`",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "tagId", description = "TagID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "beginPublishDate", description = "开始发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(in = ParameterIn.QUERY, name = "endPublishDate", description = "结束发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(in = ParameterIn.QUERY, name = "isWithImage", description = "是否有标题图。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "title", description = "标题",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "text", description = "正文",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "excludeId", description = "不包含的文章ID。多个用英文逗号分隔，如`1,2,5`",
            schema = @Schema(type = "string", format = "int64 array"))
    @Parameter(in = ParameterIn.QUERY, name = "status", description = "状态。0:已发布,1:已归档,5:待发布,10:草稿,11:待审核,12:审核中,20:已删除,21:已下线,22:已退回。默认：0（已发布）",
            schema = @Schema(type = "string", format = "int16 array"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubChannel", description = "是否包含子栏目的文章。如：`true` `false`，默认`true`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubSite", description = "是否包含子站点的文章。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点文章。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Article> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return articleService.selectList(args, offset, limit);
        });
    }

    @Operation(summary = "文章分页_ArticlePage")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "channel", description = "栏目别名",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "栏目ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "tagId", description = "TagID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "beginPublishDate", description = "开始发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(in = ParameterIn.QUERY, name = "endPublishDate", description = "接受发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(in = ParameterIn.QUERY, name = "isWithImage", description = "是否有标题图。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "title", description = "标题",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "text", description = "正文",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "excludeId", description = "不包含的文章ID。多个用英文逗号分隔，如`1,2,5`",
            schema = @Schema(type = "string", format = "int64 array"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubChannel", description = "是否包含子栏目的文章。如：`true` `false`，默认`true`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubSite", description = "是否包含子站点的文章。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点文章。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Article> page(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int page = Directives.getPage(params);
            int pageSize = Directives.getPageSize(params);
            return springPage(articleService.selectPage(args, page, pageSize));
        });
    }

    @Operation(summary = "文章对象_Article")
    @ApiResponses(value = {@ApiResponse(description = "文章对象")})
    @GetMapping("/{id:[\\d]+}")
    public Article show(@Parameter(description = "文章ID") @PathVariable Long id,
                        @Parameter(description = "是否后台预览") @RequestParam(defaultValue = "false") boolean preview) {
        Article article = articleService.select(id);
        if (article == null) {
            throw new Http404Exception("Article not found. ID: " + id);
        }
        User user = Contexts.findCurrentUser();
        checkAccessPermission(article, user, groupService, channelService, orgService, preview);
        return article;
    }

    public static void checkAccessPermission(Article article, User user, GroupService groupService,
                                             ChannelService channelService, OrgService orgService, boolean preview) {
        if (preview) {
            if (user == null) {
                throw new Http401Exception();
            }
            if (user.hasAllArticlePermission()) {
                return;
            }
            Long channelId = article.getChannelId();
            Collection<Long> roleIds = user.fetchRoleIds();
            Collection<Long> orgIds = user.fetchAllOrgIds();
            boolean hasRolePermission = !roleIds.isEmpty() && channelService.existsByArticleRoleId(channelId, roleIds);
            boolean hasOrgPermission = !orgIds.isEmpty() && orgService.existsByArticleOrgId(channelId, orgIds);
            if (!hasRolePermission && !hasOrgPermission) {
                throw new Http403Exception("No preview permission. ID: " + article.getId());
            }
            return;
        }
        if (!article.isNormal()) {
            throw new Http403Exception("Article status forbidden. ID: " + article.getId());
        }
        // 检查前台权限
        if (user == null) {
            Group anonGroup = groupService.getAnonymous();
            if (!hasAccessPermission(anonGroup, article.getSiteId(), article.getChannelId(), groupService)) {
                throw new Http401Exception();
            }
        } else {
            Group userGroup = user.getGroup();
            if (!hasAccessPermission(userGroup, article.getSiteId(), article.getChannelId(), groupService)) {
                throw new Http403Exception("Article access forbidden. ID: " + article.getId());
            }
        }
    }

    @Operation(summary = "下一篇文章_ArticleNext")
    @Parameter(in = ParameterIn.QUERY, name = "id", description = "文章ID",
            schema = @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED))
    @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "文章栏目ID",
            schema = @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED))
    @Parameter(in = ParameterIn.QUERY, name = "order", description = "文章排序值",
            schema = @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED))
    @Parameter(in = ParameterIn.QUERY, name = "isDesc", description = "是否倒序。默认 true",
            schema = @Schema(type = "boolean"))
    @GetMapping("/next")
    public Article next(@RequestParam(defaultValue = "true") boolean isDesc, HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return ArticleNextDirective.query(params, isDesc ? articleService::findNext : articleService::findPrev);
    }

    @Operation(summary = "上一篇文章_ArticlePrev")
    @Parameter(in = ParameterIn.QUERY, name = "id", description = "文章ID",
            schema = @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED))
    @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "文章栏目ID",
            schema = @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED))
    @Parameter(in = ParameterIn.QUERY, name = "order", description = "文章排序值",
            schema = @Schema(type = "integer", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED))
    @Parameter(in = ParameterIn.QUERY, name = "isDesc", description = "是否倒序。默认 true",
            schema = @Schema(type = "boolean"))
    @GetMapping("/prev")
    public Article prev(@RequestParam(defaultValue = "true") boolean isDesc, HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return ArticleNextDirective.query(params, isDesc ? articleService::findPrev : articleService::findNext);
    }

    @Operation(summary = "获取文章浏览次数")
    @GetMapping("/view/{id:[\\d]+}")
    public long view(@Parameter(description = "文章ID") @PathVariable Long id) {
        return viewCountService.viewArticle(id);
    }

    @Operation(summary = "顶文章")
    @ApiResponses(value = {@ApiResponse(description = "文章总顶次数")})
    @PostMapping("/up/{id:[\\d]+}")
    public int up(@Parameter(description = "文章ID") @PathVariable Long id,
                  HttpServletRequest request, HttpServletResponse response) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            return 0;
        }
        if (actionExist(id, Article.ACTION_OPTION_UP, request, response)) {
            return -1;
        }
        int ups = buffer.getUps() + 1;
        buffer.setUps(ups);
        bufferService.update(buffer);
        return ups;
    }

    @Operation(summary = "踩文章")
    @ApiResponses(value = {@ApiResponse(description = "文章总踩次数")})
    @PostMapping("/down/{id:[\\d]+}")
    public int down(@Parameter(description = "文章ID") @PathVariable Long id,
                    HttpServletRequest request, HttpServletResponse response) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            return 0;
        }
        if (actionExist(id, Article.ACTION_OPTION_DOWN, request, response)) {
            return -1;
        }
        int downs = buffer.getDowns() + 1;
        buffer.setDowns(downs);
        bufferService.update(buffer);
        return downs;
    }

    private boolean actionExist(Long id, String option, HttpServletRequest request, HttpServletResponse response) {
        Site site = siteResolver.resolve(request);
        long cookie = Constants.retrieveIdentityCookie(request, response);
        String ip = Servlets.getRemoteAddr(request);
        Long userId = Optional.ofNullable(Contexts.findCurrentUser()).map(UserBase::getId).orElse(null);
        if (actionService.existsBy(Article.ACTION_TYPE_UP_DOWN, id, null, null, null, ip, cookie)) {
            return true;
        }
        actionService.insert(new Action(Article.ACTION_TYPE_UP_DOWN, id,
                option, site.getId(), userId, ip, cookie));
        return false;
    }

    @Operation(summary = "获取下载参数")
    @ApiResponses(value = {@ApiResponse(description = "当前时间和KEY。如：`time=123&key=abc`")})
    @GetMapping("/download-params/{id:[\\d]+}")
    public String downloadParam(@Parameter(description = "文章ID") @PathVariable Long id) {
        long time = System.currentTimeMillis();
        String secret = props.getDownloadSecret();
        String key = Utils.getDownloadKey(id, time, secret);
        return "time=" + time + "&key=" + key;
    }

    @Operation(summary = "记录下载次数")
    @ApiResponses(value = {@ApiResponse(description = "文章总下载次数")})
    @PostMapping("/download/{id:\\d+}")
    public int download(@Parameter(description = "文章ID") @PathVariable Long id) {
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
    public ArticleBuffer buffer(@Parameter(description = "文章ID") @PathVariable Long id) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            throw new Http404Exception("ArticleBuffer not found. id=" + id);
        }
        return buffer;
    }
}
