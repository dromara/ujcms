package com.ujcms.cms.core.web.api;

import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.common.db.MyBatis.springPage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.component.ArticleAccessGuard;
import com.ujcms.cms.core.component.IpInteractionCounterService;
import com.ujcms.cms.core.component.ViewCountService;
import com.ujcms.cms.core.domain.Action;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleBuffer;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.generated.GeneratedUser;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.ArticleLuceneArgs;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.service.ActionService;
import com.ujcms.cms.core.service.ArticleBufferService;
import com.ujcms.cms.core.service.ArticleRelatedService;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.Utils;
import com.ujcms.cms.core.web.directive.ArticleListDirective;
import com.ujcms.cms.core.web.directive.ArticleNextDirective;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.common.query.OffsetLimitRequest;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Servlets;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http404Exception;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
    private final ChannelService channelService;
    private final ArticleService articleService;
    private final ArticleBufferService bufferService;
    private final ViewCountService viewCountService;
    private final Props props;
    private final IpInteractionCounterService interactionCounter;
    private final ArticleRelatedService relatedService;
    private final ArticleAccessGuard accessGuard;
    private final ArticleLucene articleLucene;

    public ArticleController(SiteResolver siteResolver, ActionService actionService,
                             ChannelService channelService, ArticleService articleService,
                             ArticleBufferService bufferService, ViewCountService viewCountService, Props props,
                             IpInteractionCounterService interactionCounter,
                             ArticleRelatedService relatedService, ArticleAccessGuard accessGuard,
                             ArticleLucene articleLucene) {
        this.siteResolver = siteResolver;
        this.actionService = actionService;
        this.channelService = channelService;
        this.articleService = articleService;
        this.bufferService = bufferService;
        this.viewCountService = viewCountService;
        this.props = props;
        this.interactionCounter = interactionCounter;
        this.relatedService = relatedService;
        this.accessGuard = accessGuard;
        this.articleLucene = articleLucene;
    }

    /**
     * 列表接口没有传 {@code limit} 时返回的条数。以前是 1000：匿名的一次请求就能让服务端读出一千篇文章
     */
    static final int DEFAULT_LIMIT = 20;
    /**
     * 关键词（{@code q} / {@code text}）经全文检索命中的文章数上限，其余条件再在这些文章里筛选
     */
    static final int SEARCH_MAX = 500;

    private <T> T query(HttpServletRequest request, BiFunction<ArticleArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = new HashMap<>(QueryUtils.getParams(request.getQueryString()));
        // 关键词不交给数据库做 LIKE '%…%'：正文是大字段、没有索引，每个匿名请求都是对全站正文的一次全表扫描，
        // 十来个并发就能占满连接池。改由全文索引取出命中的文章，其余条件在这些文章里筛选
        // （docs/spec/article-management/plan.md Phase 2.5）。模板标签不受影响
        String q = StringUtils.trimToNull(params.remove(ArticleListDirective.Q));
        String text = StringUtils.trimToNull(params.remove(ArticleListDirective.TEXT));
        ArticleArgs args = ArticleArgs.of(QueryUtils.getPublicQueryMap(params));
        ArticleListDirective.assemble(args, params, site.getId(), channelService);
        // 前台接口只允许获取已发布、已归档的数据
        List<Short> status = ArticleListDirective.publicStatus(params);
        args.status(status);
        if (q != null || text != null) {
            args.inIds(searchIds(q, text, params, site.getId(), status));
        }
        return handle.apply(args, params);
    }

    /**
     * 全文检索命中的文章ID，至多 {@link #SEARCH_MAX} 篇。{@code q} 查标题与正文，{@code text} 只查正文。
     * 站点范围与列表接口一致：{@code siteId}，没有则当前站点；{@code isAllSite} 时不限站点
     */
    private List<Long> searchIds(@Nullable String q, @Nullable String text, Map<String, String> params,
                                 Long defaultSiteId, List<Short> status) {
        ArticleLuceneArgs searchArgs = new ArticleLuceneArgs();
        if (q != null) {
            searchArgs.setTitle(q);
            searchArgs.setBody(q);
        } else {
            searchArgs.setBody(text);
        }
        Long siteId = Directives.getLong(params, ArticleListDirective.SITE_ID);
        if (siteId == null && !Directives.getBoolean(params, ArticleListDirective.IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        searchArgs.setSiteId(siteId);
        searchArgs.setIncludeSubSite(Directives.getBoolean(params, ArticleListDirective.IS_INCLUDE_SUB_SITE, false));
        searchArgs.setStatus(status.stream().map(Short::intValue).toList());
        List<Long> ids = articleLucene.findAll(searchArgs, null, new OffsetLimitRequest(0, SEARCH_MAX))
                .map(EsArticle::getId).getContent();
        // 没有命中：给一个不存在的ID，结果为空（空集合会被当作“没有这个条件”）
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    /**
     * ## 标签使用示例
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
    @Parameter(in = ParameterIn.QUERY, name = "status", description = "状态。0:已发布,1:已归档。其它状态不可获取。默认：0（已发布）",
            schema = @Schema(type = "string", format = "int16 array"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubChannel", description = "是否包含子栏目的文章。如：`true` `false`，默认`true`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubSite", description = "是否包含子站点的文章。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点文章。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。默认 20，最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Article> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = params.containsKey(Directives.LIMIT) ? Directives.getLimit(params) : DEFAULT_LIMIT;
            List<Article> list = articleService.selectList(args, offset, limit);
            // TAG、区块项整页各取一次，不逐篇懒加载
            articleService.attachListRelations(list);
            list.forEach(ArticleController::withPublicSource);
            return list;
        });
    }

    /**
     * ## 标签使用示例
     * <pre>
     * ```
     * [@ArticlePage channel='news' limit='8'; pagedList]
     *   [#list pagedList.content as bean]
     *   <a href="${bean.url}">${bean.title}</a>
     *   [/#list]
     * [/@ArticlePage]
     * ```
     * </pre>
     */
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
            com.github.pagehelper.Page<Article> pagedList = articleService.selectPage(args, page, pageSize);
            articleService.attachListRelations(pagedList);
            pagedList.forEach(ArticleController::withPublicSource);
            return springPage(pagedList);
        });
    }

    @Operation(summary = "专题文章分页_TopicArticlePage")
    @Parameter(in = ParameterIn.QUERY, name = "topicId", description = "专题ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/by-topic")
    public Page<Article> byTopic(@RequestParam Long topicId,
                                 @RequestParam(defaultValue = "1") Integer page,
                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        com.github.pagehelper.Page<Article> pagedList =
                articleService.selectPageByTopicId(topicId, validPage(page), validPageSize(pageSize));
        articleService.attachListRelations(pagedList);
        pagedList.forEach(ArticleController::withPublicSource);
        return springPage(pagedList);
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
        accessGuard.check(article, Contexts.findCurrentUser(), preview);
        return publicView(article);
    }

    /**
     * 文章详情里的“推送 / 扩展栏目”子行只给出已公开的：待审核、已下线的子行不属于前台
     */
    private static Article publicView(Article article) {
        article.setDestList(article.getDestList().stream().filter(Article::isNormal).toList());
        return withPublicSource(article);
    }

    /**
     * 源文章不是公开状态（草稿、待审核、已下线、已删除……）时不给出：子行公开不等于源文章公开
     */
    private static Article withPublicSource(Article article) {
        Article src = article.getSrc();
        if (src != null && !src.isNormal()) {
            article.setSrc(null);
        }
        return article;
    }

    /**
     * 相关文章：手选优先、不足自动补齐，与模板 {@code [@ArticleRelatedList]} 同一读取路径
     * （docs/spec/article-related/plan.md §6）。仅对已发布/已归档文章开放
     */
    @Operation(summary = "相关文章_ArticleRelatedList")
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "条数，默认 5",
            schema = @Schema(type = "integer"))
    @Parameter(in = ParameterIn.QUERY, name = "fill", description = "不足时是否自动补齐，默认 true",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "自动补齐时限定的栏目ID（含子栏目）",
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses(value = {@ApiResponse(description = "文章列表")})
    @JsonView(Views.List.class)
    @GetMapping("/{id:[\\d]+}/related")
    public List<Article> related(@Parameter(description = "文章ID") @PathVariable Long id,
                                 @RequestParam(defaultValue = "5") int limit,
                                 @RequestParam(defaultValue = "true") boolean fill,
                                 Long channelId) {
        Article article = articleService.select(id);
        if (article == null) {
            throw new Http404Exception("Article not found. ID: " + id);
        }
        accessGuard.check(article, Contexts.findCurrentUser(), false);
        int size = Math.min(Math.max(limit, 1), Constants.MAX_PAGE_SIZE);
        return relatedService.listForRender(article, size, fill, channelId).list();
    }

    /**
     * 按别名取文章（无头客户端解析 {@code /article/{slug}} 形态；混合/数字形态先按 ID 取，未命中再调本接口）
     */
    @Operation(summary = "文章对象BySlug_Article")
    @ApiResponses(value = {@ApiResponse(description = "文章对象")})
    @GetMapping("/slug/{slug:[\\w-]+}")
    public Article slug(@Parameter(description = "文章别名") @PathVariable String slug,
                        @Parameter(description = "站点ID") Long siteId,
                        @Parameter(description = "是否后台预览") @RequestParam(defaultValue = "false") boolean preview,
                        HttpServletRequest request) {
        if (siteId == null) {
            siteId = siteResolver.resolve(request).getId();
        }
        Article article = articleService.findBySiteIdAndSlug(siteId, slug);
        if (article == null) {
            throw new Http404Exception("Article not found. slug: " + slug);
        }
        accessGuard.check(article, Contexts.findCurrentUser(), preview);
        return publicView(article);
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
    @JsonView(Views.List.class)
    @GetMapping("/next")
    @Nullable
    public Article next(@RequestParam(defaultValue = "true") boolean isDesc, HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return readable(ArticleNextDirective.query(params, isDesc ? articleService::findNext : articleService::findPrev));
    }

    /**
     * 上一篇、下一篇只给访问者读得到的文章，且只给列表字段。以前这两个接口不查浏览权限、又返回完整对象：
     * 匿名请求拿栏目ID就能读出会员栏目里文章的全文（docs/spec/article-management/plan.md H6）
     */
    @Nullable
    private Article readable(@Nullable Article article) {
        return article != null && accessGuard.isReadable(article, Contexts.findCurrentUser())
                ? withPublicSource(article) : null;
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
    @JsonView(Views.List.class)
    @GetMapping("/prev")
    @Nullable
    public Article prev(@RequestParam(defaultValue = "true") boolean isDesc, HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return readable(ArticleNextDirective.query(params, isDesc ? articleService::findPrev : articleService::findNext));
    }

    @Operation(summary = "获取文章浏览次数")
    @GetMapping("/view/{id:[\\d]+}")
    public long view(@Parameter(description = "文章ID") @PathVariable Long id) {
        // 登录会员的浏览会触发浏览监听器（如浏览历史记录），匿名浏览仅计数
        // 只对访问者读得到的文章计数、记浏览历史：否则拿任意ID调用，草稿、别的站点的文章也会进到自己的浏览历史里
        User user = Contexts.findCurrentUser();
        Long userId = Optional.ofNullable(user).map(GeneratedUser::getId).orElse(null);
        return viewCountService.viewArticle(id, userId, article -> accessGuard.isReadable(article, user));
    }

    /**
     * 顶、踩、统计数据只对访问者读得到的文章开放
     */
    private boolean isReadable(Long id) {
        Article article = articleService.select(id);
        return article != null && accessGuard.isReadable(article, Contexts.findCurrentUser());
    }

    @Operation(summary = "顶文章")
    @ApiResponses(value = {@ApiResponse(description = "文章总顶次数")})
    @PostMapping("/up/{id:[\\d]+}")
    public int up(@Parameter(description = "文章ID") @PathVariable Long id,
                  HttpServletRequest request, HttpServletResponse response) {
        if (!isReadable(id)) {
            return 0;
        }
        if (actionExist(id, Article.ACTION_OPTION_UP, request, response)) {
            return -1;
        }
        return bufferService.incrementUps(id);
    }

    @Operation(summary = "踩文章")
    @ApiResponses(value = {@ApiResponse(description = "文章总踩次数")})
    @PostMapping("/down/{id:[\\d]+}")
    public int down(@Parameter(description = "文章ID") @PathVariable Long id,
                    HttpServletRequest request, HttpServletResponse response) {
        if (!isReadable(id)) {
            return 0;
        }
        if (actionExist(id, Article.ACTION_OPTION_DOWN, request, response)) {
            return -1;
        }
        return bufferService.incrementDowns(id);
    }

    private boolean actionExist(Long id, String option, HttpServletRequest request, HttpServletResponse response) {
        Site site = siteResolver.resolve(request);
        long cookie = Constants.retrieveIdentityCookie(request, response);
        String ip = Servlets.getRemoteAddr(request, props.getIpProxyDepth());
        // 匿名互动 IP 限流（默认关闭）：超限时视为不可再操作（与“已操作”同样返回 true）
        if (interactionCounter.isExcessive(ip, props.getInteractionMaxPerMinute())) {
            return true;
        }
        Long userId = Optional.ofNullable(Contexts.findCurrentUser()).map(GeneratedUser::getId).orElse(null);
        if (actionService.existsBy(Article.ACTION_TYPE_UP_DOWN, id, null, null, null, ip, cookie)) {
            return true;
        }
        actionService.insert(new Action(Article.ACTION_TYPE_UP_DOWN, id,
                option, site.getId(), userId, ip, cookie));
        interactionCounter.updateCount(ip);
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

    @Operation(summary = "获取文章统计数据")
    @GetMapping("/buffer/{id:[\\d]+}")
    public ArticleBuffer buffer(@Parameter(description = "文章ID") @PathVariable Long id) {
        ArticleBuffer buffer = isReadable(id) ? bufferService.select(id) : null;
        if (buffer == null) {
            throw new Http404Exception("ArticleBuffer not found. id=" + id);
        }
        return buffer;
    }
}
