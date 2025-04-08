package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.web.directive.EsArticleListDirective;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.commons.query.OffsetLimitRequest;
import com.ujcms.commons.query.QueryUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 文章前台(全文检索) 接口
 *
 * @author PONY
 */
@Tag(name = "文章全文检索接口")
@RestController
@RequestMapping({API + "/article/es", FRONTEND_API + "/article/es"})
public class EsArticleController {
    private final ArticleLucene articleLucene;
    private final SiteResolver siteResolver;

    public EsArticleController(ArticleLucene articleLucene, SiteResolver siteResolver) {
        this.articleLucene = articleLucene;
        this.siteResolver = siteResolver;
    }

    private Page<EsArticle> query(Map<String, String> params, Long defaultSiteId, Pageable pageable) {
        return EsArticleListDirective.query(params, defaultSiteId, pageable, articleLucene);
    }

    /**
     * 设置 `日期衰减半径 (dateExpScale)` 可将文章日期纳入评分。文章日期的评分在此半径内将快速衰减至 `日期衰减率 (dateExpDecay)`，
     * `日期衰减权重提升 (dateExpBoost)` 影响文章日期评分的分值大小。如：日期衰减半径=10，日期衰减率=0.5，日期衰减权重提升=3；
     * 则最新文章得分 3*1=3，10天前的文章得分为 3*0.5=1.5，20天前的文章得分为 3*0.5*0.5=0.75，30天前的文章得分为 3*0.5*0.5*0.5=0.375。
     */
    @Operation(summary = "全文检索文章列表_EsArticleList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    // 为保持全文检索的单纯性（即不需依赖其它 Service，不提供 channel 参数），以便将 ES 做成单独的服务
    @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "栏目ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "beginPublishDate", description = "开始发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(in = ParameterIn.QUERY, name = "endPublishDate", description = "结束发布日期。如：`2008-08-01` `2012-10-01 08:12:34`",
            schema = @Schema(type = "string", format = "date-time"))
    @Parameter(in = ParameterIn.QUERY, name = "isWithImage", description = "是否有标题图。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "q", description = "搜索内容",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeBody", description = "是否搜索正文。如：`true` `false`，默认`true`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeDisabled", description = "是否包含不允许搜索的栏目的文章。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "fragmentSize", description = "摘要长度。默认 `100`",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "excludeId", description = "不包含的文章ID。多个用英文逗号分隔，如`1,2,5`",
            schema = @Schema(type = "string", format = "int64 array"))
    @Parameter(in = ParameterIn.QUERY, name = "status", description = "状态。0:已发布,1:已归档,5:待发布,10:草稿,11:待审核,12:审核中,20:已删除,21:已下线,22:已退回。默认：0（已发布）",
            schema = @Schema(type = "string", format = "int16 array"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubChannel", description = "是否包含子栏目的文章。如：`true` `false`，默认`true`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeSubSite", description = "是否包含子站点的文章。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))

    @Parameter(in = ParameterIn.QUERY, name = "dateExpScale", description = "日期衰减半径。单位：天。为 `0` 时不启用日期衰减。默认 `0`",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "dateExpOffset", description = "日期衰减偏移量。单位：天。此天数内日期不衰减。默认 `0`",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "dateExpDecay", description = "日期衰减率。日期达到衰减半径时的衰减率。取值范围：0 - 1 之间（不含0，1）。默认为 0.5",
            schema = @Schema(type = "number", format = "double"))
    @Parameter(in = ParameterIn.QUERY, name = "dateExpBoost", description = "日期衰减权重提升。日期衰减评分将乘于提升值。默认为 3",
            schema = @Schema(type = "integer", format = "int32"))

    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点文章。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @GetMapping
    public List<EsArticle> list(HttpServletRequest request) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        Sort sort = Directives.getSort(params);
        Page<EsArticle> pagedList = query(params, site.getId(), OffsetLimitRequest.of(offset, limit, sort));
        return pagedList.getContent();
    }

    @Operation(summary = "全文检索文章分页_EsArticlePage")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "channel", description = "栏目别名",
            schema = @Schema(type = "string"))
    @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "栏目ID",
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
    @GetMapping("/page")
    public Page<EsArticle> page(HttpServletRequest request) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        // spring-data 的 page 从 0 开始
        int page = Directives.getPage(params) - 1;
        int pageSize = Directives.getPageSize(params);
        Sort sort = Directives.getSort(params);
        return query(params, site.getId(), PageRequest.of(page, pageSize, sort));
    }
}
