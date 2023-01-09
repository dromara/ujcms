package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.web.directive.EsArticleListDirective;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.util.query.OffsetLimitRequest;
import com.ujcms.util.query.QueryUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
@Tag(name = "EsArticleController", description = "文章全文检索接口")
@RestController
@RequestMapping({API + "/article/es", FRONTEND_API + "/article/es"})
public class EsArticleController {
    private final ArticleLucene articleLucene;
    private final SiteResolver siteResolver;

    public EsArticleController(ArticleLucene articleLucene, SiteResolver siteResolver) {
        this.articleLucene = articleLucene;
        this.siteResolver = siteResolver;
    }

    private Page<EsArticle> query(Map<String, String> params, Integer defaultSiteId, Pageable pageable) {
        return EsArticleListDirective.query(params, defaultSiteId, pageable, articleLucene);
    }

    @Operation(summary = "获取文章列表")
    @Parameters({
            @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "channel", description = "栏目别名",
                    schema = @Schema(type = "string")),
            @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "栏目ID",
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
    @GetMapping
    public List<EsArticle> list(HttpServletRequest request) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        Page<EsArticle> pagedList = query(params, site.getId(), OffsetLimitRequest.of(offset, limit));
        return pagedList.getContent();
    }

    @Operation(summary = "获取文章分页")
    @Parameters({
            @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
                    schema = @Schema(type = "integer", format = "int32")),
            @Parameter(in = ParameterIn.QUERY, name = "channel", description = "栏目别名",
                    schema = @Schema(type = "string")),
            @Parameter(in = ParameterIn.QUERY, name = "channelId", description = "栏目ID",
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
    @GetMapping("/page")
    public Page<EsArticle> page(HttpServletRequest request) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        // spring-data 的 page 从 0 开始
        int page = Directives.getPage(params) - 1;
        int pageSize = Directives.getPageSize(params);
        return query(params, site.getId(), PageRequest.of(page, pageSize));
    }
}
