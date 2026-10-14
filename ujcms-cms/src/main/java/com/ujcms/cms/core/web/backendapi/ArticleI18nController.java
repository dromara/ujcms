package com.ujcms.cms.core.web.backendapi;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.component.ArticleDataPermission;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleI18n;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ArticleI18nService;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.web.support.SiteForbiddenException;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章翻译等价集 Controller（multilingual P5，translation-links.md §5.3；
 * 译文草稿与过期状态见 translation-workflow.md）。
 * 复用 article:show / article:update 权限（链接是文章元数据）；与站点语言组一样，
 * 事实上依赖多站点（T2），不另设商业闸门。
 * 例外：新增译文（跨站点创建内容）使用独立权限 article:createTranslation，经
 * PERMISSION_TIERS 登记为 T2（控制器留在核心，社区版因无第二站点而天然不可用——
 * 与站点语言组同一姿态）。
 *
 * @author PONY
 */
@RestController("backendArticleI18nController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/article-i18n")
public class ArticleI18nController {
    private final ArticleI18nService service;
    private final ArticleService articleService;
    private final ChannelService channelService;
    private final SiteService siteService;
    private final OrgService orgService;
    private final ArticleDataPermission dataPermission;

    public ArticleI18nController(ArticleI18nService service, ArticleService articleService,
                                 ChannelService channelService, SiteService siteService, OrgService orgService,
                                 ArticleDataPermission dataPermission) {
        this.dataPermission = dataPermission;
        this.service = service;
        this.articleService = articleService;
        this.channelService = channelService;
        this.siteService = siteService;
        this.orgService = orgService;
    }

    /**
     * 文章的翻译行：语言组内除本文所在站点外的每个站点一行（站点、语言、已链接文章或空、
     * 该译文是否可能过期），附本文自身的可能过期标志。成员查询状态无关（译文草稿必须可见，
     * 面板据状态标签展示）。当前站点未分组时 rows 为空列表（前端面板据此自隐藏）
     */
    @GetMapping("{articleId}")
    @PreAuthorize("hasAnyAuthority('article:show','*')")
    public ShowResult show(@PathVariable Long articleId) {
        Article article = Optional.ofNullable(articleService.select(articleId))
                .orElseThrow(() -> new Http404Exception(Article.NOT_FOUND + articleId));
        ValidUtils.dataInSite(article.getSiteId(), Contexts.getCurrentSiteId());
        // 翻译面板嵌在文章表单里：能读到这篇文章的人才能读它的翻译行——文章在数据权限之内，或是持有其审核任务的审核人
        User user = Contexts.getCurrentUser();
        if (!dataPermission.permits(user, article) && !articleService.isReviewer(article, user)) {
            throw new Http403Exception("error.article.forbidden", String.valueOf(articleId));
        }
        Site site = Contexts.getCurrentSite();
        List<ArticleI18n> groupRows = service.listGroupRows(articleId);
        Map<Long, ArticleI18n> rowByArticleId = new HashMap<>(16);
        for (ArticleI18n row : groupRows) {
            rowByArticleId.put(row.getArticleId(), row);
        }
        ArticleI18n selfRow = rowByArticleId.get(articleId);
        boolean selfStale = selfRow != null && ArticleI18n.isStale(selfRow, groupRows);
        List<TranslationResult> rows = new ArrayList<>();
        for (Site member : siteService.listByLangGroup(site)) {
            if (Objects.equals(member.getId(), article.getSiteId())) {
                continue;
            }
            Article linked = service.findMemberByGroupAndSiteId(articleId, member.getId());
            Boolean stale = null;
            if (linked != null) {
                ArticleI18n linkedRow = rowByArticleId.get(linked.getId());
                stale = linkedRow != null && ArticleI18n.isStale(linkedRow, groupRows);
            }
            rows.add(new TranslationResult(member.getId(), member.getName(), member.getLangTag(),
                    linked == null ? null : ArticleRefResult.of(linked), stale));
        }
        return new ShowResult(selfStale, rows);
    }

    /**
     * 集合式写入：body 为 站点ID → 目标文章ID（null=解除该站点链接）；未出现的站点不变。
     * 服务端校验同语言组/异站点/一站一员，不做隐式并组（translation-links.md §3.2）
     */
    @PutMapping("{articleId}")
    @PreAuthorize("hasAnyAuthority('article:update','*')")
    @OperationLog(module = "article", operation = "updateI18n", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@PathVariable Long articleId,
                                       @RequestBody Map<Long, @Nullable Long> siteIdToArticleId) {
        Article article = Optional.ofNullable(articleService.select(articleId))
                .orElseThrow(() -> new Http404Exception(Article.NOT_FOUND + articleId));
        ValidUtils.dataInSite(article.getSiteId(), Contexts.getCurrentSiteId());
        // 改写的是本文的翻译链接：本文须在操作员的数据权限之内。目标站点一侧沿用语言组规则（D7）
        dataPermission.requireArticle(Contexts.getCurrentUser(), article);
        service.relink(article, siteIdToArticleId);
        return Responses.ok();
    }

    /**
     * 新增译文（translation-workflow.md）：在同语言组的目标站点创建当前文章的草稿副本并链入翻译组。
     * 目标站点须为当前站点语言组内的其它正常站点，且操作用户的组织须对目标站点有权限
     * （复制 BackendInterceptor 的站点权限口径——跨站点写入不能只依赖当前站点头）。
     * 目标栏目缺省按源栏目别名解析；无同别名栏目时须显式指定
     */
    @PostMapping("{articleId}/translation")
    @PreAuthorize("hasAnyAuthority('article:createTranslation','*')")
    @OperationLog(module = "article", operation = "createTranslation", type = OperationType.CREATE)
    public CreateTranslationResult createTranslation(@PathVariable Long articleId,
                                                     @RequestBody @Valid CreateTranslationParams params) {
        Article source = Optional.ofNullable(articleService.select(articleId))
                .orElseThrow(() -> new Http404Exception(Article.NOT_FOUND + articleId));
        ValidUtils.dataInSite(source.getSiteId(), Contexts.getCurrentSiteId());
        User user = Contexts.getCurrentUser();
        // 源文章须在操作员的数据权限之内。目标站点一侧沿用语言组、站点权限规则（D7）
        dataPermission.requireArticle(user, source);
        Site site = Contexts.getCurrentSite();
        Site targetSite = siteService.listByLangGroup(site).stream()
                .filter(member -> Objects.equals(member.getId(), params.targetSiteId()))
                .findFirst().orElseThrow(() -> new Http400Exception(String.format(
                        "Site %s is not in the current site's language group", params.targetSiteId())));
        if (Objects.equals(targetSite.getId(), source.getSiteId())) {
            throw new Http400Exception(String.format(
                    "Cannot create a translation in the article's own site. site: %s", targetSite.getId()));
        }
        if (!orgService.hasRelationship(user.getOrgId(), targetSite.getOrgId())) {
            throw new SiteForbiddenException("error.siteForbidden", targetSite.getName(),
                    String.valueOf(targetSite.getId()));
        }
        Channel targetChannel;
        if (params.targetChannelId() != null) {
            targetChannel = Optional.ofNullable(channelService.select(params.targetChannelId()))
                    .orElseThrow(() -> new Http400Exception(Channel.NOT_FOUND + params.targetChannelId()));
        } else {
            targetChannel = channelService.findBySiteIdAndSlug(targetSite.getId(),
                    source.getChannel().getSlug());
            if (targetChannel == null) {
                throw new Http400Exception(String.format(
                        "Site %s has no channel with slug '%s'. Choose a target channel explicitly.",
                        targetSite.getId(), source.getChannel().getSlug()));
            }
        }
        Article created = service.createTranslation(source, targetSite, targetChannel, user);
        return new CreateTranslationResult(created.getId(), created.getTitle(), created.getSiteId());
    }

    /**
     * 批量翻译状态（文章列表徽标用）：每个文章ID返回是否已入组、是否可能过期。
     * 仅查询当前站点文章（他站ID返回未链接）；当前站点未分组时全部返回未链接
     */
    @GetMapping("status")
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public List<StatusResult> status(@RequestParam("ids") List<Long> ids) {
        if (ids.size() > MAX_STATUS_IDS) {
            throw new Http400Exception(String.format("Too many ids: %s (max %s)", ids.size(), MAX_STATUS_IDS));
        }
        Site site = Contexts.getCurrentSite();
        Map<Long, Boolean> staleById = siteService.listByLangGroup(site).size() < 2
                ? Map.of()
                : service.listStaleByArticleIds(ids, site.getId());
        return ids.stream()
                .map(id -> new StatusResult(id, staleById.containsKey(id), Boolean.TRUE.equals(staleById.get(id))))
                .toList();
    }

    /**
     * 备选文章查询（文章表单的翻译选择器）。siteId 必须属于当前站点的语言组（防跨组数据泄露）；
     * channelSlug 经别名解析为该站点栏目（未命中返回空列表）；结果标记已入组文章
     */
    @GetMapping("candidates")
    @PreAuthorize("hasAnyAuthority('article:show','*')")
    public List<CandidateResult> candidates(Long siteId, @Nullable String channelSlug, @Nullable String title,
                                            @Nullable Integer limit) {
        Site site = Contexts.getCurrentSite();
        boolean inGroup = siteService.listByLangGroup(site).stream()
                .anyMatch(member -> Objects.equals(member.getId(), siteId));
        if (!inGroup) {
            throw new Http400Exception(String.format(
                    "Site %s is not in the current site's language group", siteId));
        }
        Long channelId = null;
        if (StringUtils.isNotBlank(channelSlug)) {
            Channel channel = channelService.findBySiteIdAndSlug(siteId, channelSlug);
            if (channel == null) {
                return List.of();
            }
            channelId = channel.getId();
        }
        int size = limit == null ? DEFAULT_CANDIDATE_LIMIT : Math.min(limit, MAX_CANDIDATE_LIMIT);
        Long finalChannelId = channelId;
        List<Article> articles = PageMethod.offsetPage(0, size, false)
                .doSelectPage(() -> service.listCandidates(siteId, finalChannelId, title));
        Set<Long> linkedIds = service.listByArticleIds(articles.stream().map(Article::getId).toList())
                .stream().map(ArticleI18n::getArticleId).collect(Collectors.toSet());
        return articles.stream()
                .map(article -> new CandidateResult(article.getId(), article.getTitle(), article.getStatus(),
                        article.hasCustomSlug() ? article.getSlug() : null, linkedIds.contains(article.getId())))
                .toList();
    }

    private static final int DEFAULT_CANDIDATE_LIMIT = 20;
    private static final int MAX_CANDIDATE_LIMIT = 200;
    private static final int MAX_STATUS_IDS = 200;

    /**
     * 翻译面板响应：本文自身是否可能过期 + 语言组各站点的翻译行
     */
    @Schema(name = "ArticleI18nController.ShowResult", description = "文章翻译面板")
    public record ShowResult(boolean selfStale, List<TranslationResult> rows) {
    }

    /**
     * 翻译行响应：语言组内一个站点的链接状态。{@code stale} 为该已链接译文是否可能过期
     * （未链接时为 {@code null}）
     */
    @Schema(name = "ArticleI18nController.TranslationResult", description = "文章翻译行")
    public record TranslationResult(Long siteId, String siteName, String lang, @Nullable ArticleRefResult article,
                                    @Nullable Boolean stale) {
    }

    /**
     * 新增译文请求
     */
    @Schema(name = "ArticleI18nController.CreateTranslationParams", description = "新增译文请求")
    public record CreateTranslationParams(@NotNull Long targetSiteId, @Nullable Long targetChannelId) {
    }

    /**
     * 新增译文响应
     */
    @Schema(name = "ArticleI18nController.CreateTranslationResult", description = "新增译文结果")
    public record CreateTranslationResult(Long id, String title, Long siteId) {
    }

    /**
     * 批量翻译状态响应
     */
    @Schema(name = "ArticleI18nController.StatusResult", description = "文章翻译状态")
    public record StatusResult(Long id, boolean linked, boolean stale) {
    }

    /**
     * 已链接文章引用
     */
    @Schema(name = "ArticleI18nController.ArticleRefResult", description = "已链接文章")
    public record ArticleRefResult(Long id, String title, Short status) {
        static ArticleRefResult of(Article article) {
            return new ArticleRefResult(article.getId(), article.getTitle(), article.getStatus());
        }
    }

    /**
     * 备选文章响应
     */
    @Schema(name = "ArticleI18nController.CandidateResult", description = "备选文章")
    public record CandidateResult(Long id, String title, Short status, @Nullable String slug, boolean linked) {
    }
}
