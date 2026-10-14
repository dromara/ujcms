package com.ujcms.cms.core.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.component.LangAlternateService;
import com.ujcms.cms.core.component.ViewCountService;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.SiteBuffer;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.SiteBufferService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.cms.core.web.directive.SiteListDirective;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

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
    private final LangAlternateService langAlternateService;
    private final ChannelService channelService;
    private final ArticleService articleService;
    private final SiteService service;

    public SiteController(SiteResolver siteResolver, SiteBufferService bufferService,
                          ViewCountService viewCountService, LangAlternateService langAlternateService,
                          ChannelService channelService, ArticleService articleService, SiteService service) {
        this.siteResolver = siteResolver;
        this.bufferService = bufferService;
        this.viewCountService = viewCountService;
        this.langAlternateService = langAlternateService;
        this.channelService = channelService;
        this.articleService = articleService;
        this.service = service;
    }

    @Operation(summary = "站点列表_SiteList")
    @Parameter(in = ParameterIn.QUERY, name = "parentId", description = "上级站点ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isIncludeChildren", description = "是否包含子站点。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @GetMapping
    @JsonView(Views.List.class)
    public List<Site> list(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        SiteArgs args = SiteArgs.of(QueryUtils.getPublicQueryMap(params));
        SiteListDirective.assemble(args, params);
        // 前台接口只允许获取正常状态的站点
        args.status(Collections.singletonList(Site.STATUS_NORMAL));
        return SiteListDirective.selectList(service, args, params);
    }

    @Operation(summary = "获取当前站点对象。如果传递subDir参数，则按 (当前域名, 子目录) 组合查询，未命中回退全局子目录查询；" +
            "否则通过当前域名获取对应站点对象，无法获取则返回默认站点")
    @GetMapping("/current")
    public Site current(@Parameter(description = "站点子目录") String subDir,
                        HttpServletRequest request) {
        // multilingual P2：统一走解析器，API 客户端获得组合寻址行为，请求/响应契约不变
        return siteResolver.resolve(request, subDir);
    }

    @Operation(summary = "当前页面的语言备选（multilingual P3）。站点解析与 current 一致（subDir → Host → 默认站点）；"
            + "channel=栏目别名、articleId=文章ID 提供可选的页面上下文；hreflang 仅可使用 exact=true 条目")
    @GetMapping("/langs")
    public List<LangAlternateResult> langs(@Parameter(description = "站点子目录") @Nullable String subDir,
                                           @Parameter(description = "栏目别名") @Nullable String channel,
                                           @Parameter(description = "文章ID") @Nullable Long articleId,
                                           HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        Article articleBean = null;
        Channel channelBean = null;
        if (articleId != null) {
            articleBean = articleService.select(articleId);
            if (articleBean == null || !site.getId().equals(articleBean.getSiteId())) {
                throw new Http404Exception(Article.NOT_FOUND + articleId);
            }
            articleBean.setSite(site);
        } else if (StringUtils.isNotBlank(channel)) {
            channelBean = channelService.findBySiteIdAndSlug(site.getId(), channel);
            if (channelBean == null) {
                throw new Http404Exception(Channel.NOT_FOUND + channel);
            }
            channelBean.setSite(site);
        }
        boolean isHome = articleBean == null && channelBean == null;
        // 瘦 DTO：本端点匿名高频，完整 Site JSON 过大
        return langAlternateService.listAlternates(site, channelBean, articleBean, isHome).stream()
                .map(alternate -> new LangAlternateResult(alternate.getSite().getId(), alternate.getSite().getName(),
                        alternate.getLang(), alternate.getUrl(), alternate.getFullUrl(),
                        alternate.isExact(), alternate.isCurrent()))
                .toList();
    }

    /**
     * 语言备选项响应
     */
    @Schema(name = "SiteController.LangAlternateResult", description = "语言备选项")
    public record LangAlternateResult(Long siteId, String name, String lang, String url, String fullUrl,
                                      boolean exact, boolean current) {
    }

    @Operation(summary = "站点对象_Site")
    @GetMapping("/{id:[\\d]+}")
    public Site show(@Parameter(description = "站点ID") @PathVariable Long id) {
        Site site = service.select(id);
        // 前台接口只允许获取正常状态的站点
        if (site == null || !site.isEnabled()) {
            throw new Http404Exception("Site not found. ID: " + id);
        }
        return site;
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
