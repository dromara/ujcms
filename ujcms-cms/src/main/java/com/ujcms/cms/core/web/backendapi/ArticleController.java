package com.ujcms.cms.core.web.backendapi;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.component.ArticleDataPermission;
import com.ujcms.cms.core.component.WordScreeningService;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.service.ArticleExportService;
import com.ujcms.cms.core.service.ArticleRelatedService;
import com.ujcms.cms.core.service.ArticleRelatedService.CandidateView;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.common.db.order.MoveOrderParams;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.Servlets;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import jakarta.validation.constraints.NotBlank;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import static com.ujcms.cms.core.domain.Article.STATUS_DRAFT;
import static com.ujcms.cms.core.domain.Article.STATUS_PUBLISHED;
import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.common.db.MyBatis.springPage;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

/**
 * 文章 Controller
 *
 * @author PONY
 */
@RestController("backendArticleController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/article")
public class ArticleController {
    private final HtmlGenerator generator;
    private final ArticleService service;
    private final WordScreeningService wordScreeningService;

    public ArticleController(HtmlGenerator generator, ArticleService service,
                             WordScreeningService wordScreeningService, SlugGenerator slugGenerator,
                             ArticleRelatedService relatedService, TagService tagService,
                             ChannelService channelService, ArticleDataPermission dataPermission,
                             ArticleExportService exportService) {
        this.generator = generator;
        this.service = service;
        this.exportService = exportService;
        this.channelService = channelService;
        this.dataPermission = dataPermission;
        this.wordScreeningService = wordScreeningService;
        this.slugGenerator = slugGenerator;
        this.relatedService = relatedService;
        this.tagService = tagService;
    }

    private final SlugGenerator slugGenerator;
    private final ArticleRelatedService relatedService;
    private final TagService tagService;
    private final ChannelService channelService;
    private final ArticleDataPermission dataPermission;
    private final ArticleExportService exportService;

    /**
     * 列表的查询条件、排序可以关联的字段（docs/spec/article-data-permission/plan.md D24）：文章列表、文章选择器展示、
     * 筛选、排序用到的——文章扩展表是文章自身的字段，另有所在栏目、专题、标签、区块、组织、创建人、修改人的名称
     */
    private static final Set<String> QUERY_JOINS = Set.of("@articleExt-*", "channel-name", "@ArticleTopic-topicId",
            "@ArticleTag-tag-name", "@BlockItem-blockId", "org-name", "user-username", "modifiedUser@user-username");

    @GetMapping
    @JsonView(Views.List.class)
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public Page<Article> list(Long subChannelId, Integer page, Integer pageSize, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        ArticleArgs args = ArticleArgs.of(getQueryMap(request.getQueryString(), QUERY_JOINS));
        args.siteId(site.getId());
        args.channelAncestorId(subChannelId);
        // 数据权限（数据范围、文章权限）。须在其它条件之后，见 ArticleDataPermission#filter
        dataPermission.filter(args, user);
        com.github.pagehelper.Page<Article> pagedList = service.selectPage(args, validPage(page), validPageSize(pageSize));
        // TAG、区块项整页各取一次，不逐篇懒加载（docs/spec/article-management/plan.md Phase 6）
        service.attachListRelations(pagedList);
        return springPage(pagedList);
    }

    @GetMapping("reject-count")
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public long rejectCount() {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        ArticleArgs args = ArticleArgs.of();
        args.userId(user.getId());
        args.siteId(site.getId());
        args.status(Collections.singleton(Article.STATUS_REJECTED));
        // 计的是“我的被退回文章”；仍须在文章列表看得到——文章权限或数据范围调整后看不到的，不再计入，
        // 否则首页写着 2 篇，点进去的列表里却找不到
        dataPermission.filter(args, user);
        return service.count(args);
    }

    /**
     * 各状态的文章数，供列表的状态页签显示（docs/spec/article-management/plan.md Phase 3）。条件与列表相同——查询键、
     * 选中的栏目、数据权限——只是不按状态筛选：页签要的正是“换到别的状态有几篇”。没有文章的状态不返回
     */
    @GetMapping("status-counts")
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public List<StatusCount> statusCounts(Long subChannelId, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Map<String, Object> queryMap = getQueryMap(request.getQueryString(), QUERY_JOINS);
        queryMap.keySet().removeIf(key -> STATUS_KEY.matcher(key).matches());
        ArticleArgs args = ArticleArgs.of(queryMap);
        args.siteId(site.getId());
        args.channelAncestorId(subChannelId);
        dataPermission.filter(args, user);
        List<StatusCount> counts = new ArrayList<>();
        service.countByStatus(args).forEach((status, total) -> counts.add(new StatusCount(status, total)));
        return counts;
    }

    public record StatusCount(short status, int total) {
    }

    /**
     * 针对文章状态一列的查询键：{@code EQ_status_Short}、{@code In_status_Short}，以及带分组的 {@code EQ_1_status_Short}。
     * 关联表的列（{@code channel-status}）不在此列
     */
    private static final Pattern STATUS_KEY = Pattern.compile("^[A-Za-z]+_(?:\\d[\\w-]*_)?status(?:_[A-Za-z]+)?$");

    public static class ExportColumn implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @NotBlank
        @Length(max = 80)
        private String key = "";
        @NotBlank
        @Length(max = 100)
        private String label = "";

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    public static class ArticleExportParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 只导出这些文章（勾选的行）。不给则导出当前筛选的全部
         */
        @Nullable
        @Size(max = 1000)
        private List<Long> ids;
        @NotEmpty
        @Size(max = 80)
        @Valid
        private List<ExportColumn> columns = Collections.emptyList();
        /**
         * 枚举值的文字：{@code status.0}、{@code type.1}、{@code true}、{@code false}
         */
        @Size(max = 60)
        private Map<@Length(max = 40) String, @Length(max = 60) String> labels = Collections.emptyMap();

        @Nullable
        public List<Long> getIds() {
            return ids;
        }

        public void setIds(@Nullable List<Long> ids) {
            this.ids = ids;
        }

        public List<ExportColumn> getColumns() {
            return columns;
        }

        public void setColumns(List<ExportColumn> columns) {
            this.columns = columns;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }
    }

    /**
     * 导出文章列表为 .xlsx（docs/spec/article-management/plan.md D6）。范围与列表相同：查询条件在查询串里（与列表同一组
     * {@code Q_} 键），加上所选栏目与操作员的数据权限；请求体给出要导出的列，以及可选的一批文章ID（勾选的行）。
     * <p>
     * 在请求线程上同步写出：数据权限、当前站点都取自当前请求，也不受异步超时限制。行数在写出响应头之前核对，
     * 超过上限直接拒绝，不给出半个文件
     */
    @PostMapping("export")
    @PreAuthorize("hasAnyAuthority('article:export','*')")
    @OperationLog(module = "article", operation = "export", type = OperationType.OTHER)
    public void export(Long subChannelId, @RequestBody @Valid ArticleExportParams params,
                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<ArticleExportService.Column> columns = new ArrayList<>(params.columns.size());
        for (ExportColumn column : params.columns) {
            if (!ArticleExportService.isValidColumn(column.key)) {
                throw new Http400Exception("error.article.export.columnInvalid", column.key);
            }
            columns.add(new ArticleExportService.Column(column.key, column.label));
        }
        ArticleArgs args = ArticleArgs.of(getQueryMap(request.getQueryString(), QUERY_JOINS));
        args.siteId(site.getId());
        args.channelAncestorId(subChannelId);
        if (params.ids != null && !params.ids.isEmpty()) {
            args.inIds(params.ids);
        }
        dataPermission.filter(args, user);
        long total = service.count(args);
        if (total > ArticleExportService.MAX_ROWS) {
            throw new Http400Exception("error.article.export.tooMany",
                    String.valueOf(total), String.valueOf(ArticleExportService.MAX_ROWS));
        }
        String filename = "articles-" + EXPORT_STAMP.format(LocalDateTime.now()) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString());
        exportService.write(args, columns, params.labels, site, response.getOutputStream());
    }

    private static final DateTimeFormatter EXPORT_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * 读取一批待写入的文章并整体校验（docs/spec/article-data-permission/plan.md D2）：须属于本站，且都在操作员的文章权限
     * 与数据范围之内。先校验、再写入——有一篇不合格，整批都不执行。不存在的 ID 略过
     */
    private List<Article> selectForWrite(List<Long> ids, Site site, User user) {
        List<Article> articles = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Article bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            articles.add(bean);
        }
        dataPermission.scopeOf(user, site.getId()).requireArticles(articles);
        return articles;
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('article:show','*')")
    public Article show(@PathVariable Long id) {
        Article bean = service.selectWithChannelListAndTopicList(id);
        if (bean == null) {
            throw new Http404Exception(Article.NOT_FOUND + id);
        }
        ValidUtils.dataInSite(bean.getSiteId(), Contexts.getCurrentSiteId());
        // 不在文章列表里的文章，也不能凭 ID 读取
        dataPermission.requireArticle(Contexts.getCurrentUser(), bean);
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('article:create','*')")
    @OperationLog(module = "article", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid ArticleParams bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Article article = new Article();
        Entities.copy(bean, article, EXCLUDE_FIELDS);
        article.setCustoms(bean.getCustoms());
        article.setSiteId(site.getId());
        if (article.getOrgId() == 0) {
            article.setOrgId(user.getOrgId());
        }
        // 栏目须存在且属于本站；栏目、扩展栏目、归属组织都须在操作员的数据权限之内
        channelService.selectInSite(article.getChannelId(), site.getId());
        ArticleDataPermission.Scope scope = dataPermission.scopeOf(user, site.getId());
        Set<Long> channelIds = new LinkedHashSet<>();
        channelIds.add(article.getChannelId());
        channelIds.addAll(service.listAddedExtendedChannelIds(null, site.getId(),
                bean.getRefersChannelIds(), bean.getCopiesChannelIds()));
        scope.requireChannels(channelIds);
        scope.requireOrg(article.getOrgId());
        // 如果不为草稿，就设置为已发布。不能为其它状态。
        article.adjustStatus(bean.getStatus() == STATUS_DRAFT ? STATUS_DRAFT : STATUS_PUBLISHED);
        validateBean(article.getExt());
        validateSlug(article.getSlug(), site.getId(), null);
        List<String> tagNames = bean.getTagNames() != null ? bean.getTagNames() : Collections.emptyList();
        // 文章、扩展栏目、专题、相关文章在一个事务里：后几样被拒绝时不留下一篇缺了它们的文章
        List<Article> regenerate = new ArrayList<>();
        regenerate.add(article);
        // 互相关联会改写对方文章的列表，对方静态页一并重生成
        regenerate.addAll(service.insert(article, user.getId(), tagNames, bean.getRefersChannelIds(),
                bean.getCopiesChannelIds(), bean.getTopicIds(), bean.getRelated(), user));
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(article.getSiteId(), user.getId(), taskName, regenerate, null);
        }
        // 返回新文章的ID：控制台保存后留在对话框里继续编辑（Ctrl+S、存为草稿）要靠它切到编辑状态
        return Responses.ok(new CreateResult(article.getId()));
    }

    public record CreateResult(Long id) {
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('article:update','*')")
    @OperationLog(module = "article", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid ArticleParams bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Article article = Optional.ofNullable(service.select(bean.getId()))
                .orElseThrow(() -> new Http404Exception(Article.NOT_FOUND + bean.getId()));
        ValidUtils.dataInSite(article.getSiteId(), site.getId());
        // 库中现有的文章须在操作员的文章权限与数据范围之内；不可编辑状态的文章另需“全部状态权限”，
        // 或由持有其审核任务的审核人保存（审核对话框沿用本接口）
        ArticleDataPermission.Scope scope = dataPermission.scopeOf(user, site.getId());
        scope.requireArticle(article);
        dataPermission.requireEditable(user, article, () -> service.isReviewer(article, user));
        Long origChannelId = article.getChannelId();
        // 只校验改动的部分：改换的栏目、新加的扩展栏目、改换的组织。原样提交回来的不再判定——
        // 权限更宽的同事加的扩展栏目，不应让别人改个标题也保存不了
        Set<Long> channelIds = new LinkedHashSet<>(service.listAddedExtendedChannelIds(article.getId(),
                site.getId(), bean.getRefersChannelIds(), bean.getCopiesChannelIds()));
        if (!Objects.equals(origChannelId, bean.getChannelId())) {
            Channel target = channelService.selectInSite(bean.getChannelId(), site.getId());
            // 与“移动栏目”同一规则：已发布、已归档、待发布的文章改到有审核流程的栏目，就绕过了那个栏目的审核，
            // 需要“全部状态权限”。审核中的文章不在此列：它正在走流程，由审核人保存
            short status = article.getStatus();
            if (!user.hasAllStatusPermission() && status != Article.STATUS_REVIEWING
                    && !ArticleService.editableIn(target, status)) {
                throw new Http403Exception("error.article.channel.reviewRequired", target.getName());
            }
            channelIds.add(bean.getChannelId());
        }
        scope.requireChannels(channelIds);
        // 新增并留在对话框里之后再保存，表单里没有组织（新增时由服务端取默认值）：沿用库里的，不当作“改成了 0”
        if (bean.getOrgId() == 0) {
            bean.setOrgId(article.getOrgId());
        }
        if (!Objects.equals(article.getOrgId(), bean.getOrgId())) {
            scope.requireOrg(bean.getOrgId());
        }
        Entities.copy(bean, article, EXCLUDE_FIELDS);
        article.setCustoms(bean.getCustoms());
        validateBean(article.getExt());
        validateSlug(article.getSlug(), site.getId(), article.getId());
        article.adjustStatus();
        // 改换栏目的落位判定与扩展栏目同步在同一事务里（docs/spec/article-data-permission/plan.md D20）
        List<Article> regenerate = new ArrayList<>();
        regenerate.add(article);
        // 标签、专题、相关文章没有提交（null）表示不改动，不是清空
        regenerate.addAll(service.update(article, user.getId(), bean.getTagNames(), bean.getRefersChannelIds(),
                bean.getCopiesChannelIds(), bean.getTopicIds(), bean.getRelated(), user));
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(article.getSiteId(), user.getId(), taskName, regenerate, origChannelId);
        }
        return Responses.ok();
    }

    @PostMapping("update-order")
    @PreAuthorize("hasAnyAuthority('article:updateOrder','*')")
    @OperationLog(module = "article", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> moveOrder(@RequestBody @Valid MoveOrderParams params, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Article fromBean = Optional.ofNullable(service.select(params.getFromId()))
                .orElseThrow(() -> new Http404Exception(Article.NOT_FOUND + params.getFromId()));
        Article toBean = Optional.ofNullable(service.select(params.getToId()))
                .orElseThrow(() -> new Http404Exception(Article.NOT_FOUND + params.getToId()));
        ValidUtils.dataInSite(fromBean.getSiteId(), site.getId());
        ValidUtils.dataInSite(toBean.getSiteId(), site.getId());
        dataPermission.scopeOf(user, site.getId()).requireArticles(List.of(fromBean, toBean));
        service.moveOrder(fromBean.getId(), toBean.getId());
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName,
                    Arrays.asList(fromBean, toBean), null);
        }
        return Responses.ok();
    }

    @SuppressWarnings("java:S2160")
    public static class ArticleParams extends Article {
        private static final long serialVersionUID = 1L;

        /**
         * 标签。没有提交（{@code null}）：新增时为无标签，修改时为不改动
         */
        @Nullable
        private List<String> tagNames;

        @Nullable
        public List<String> getTagNames() {
            return tagNames;
        }

        public void setTagNames(@Nullable List<String> tagNames) {
            this.tagNames = tagNames;
        }

        /**
         * 标题的约束声明在扩展数据上，{@code @Valid} 不级联到那里：在此补上，空标题、超长标题答 400 而不是落到数据库报 500。
         * 扩展数据的整体级联没有开启——采集、数据迁移写入的历史文章未必都合乎那些约束，开启后它们将无法保存
         */
        @Override
        @NotBlank
        @Length(max = 150)
        public String getTitle() {
            return super.getTitle();
        }
    }

    public static class ArticleStickyParams implements Serializable {
        private static final long serialVersionUID = 1;
        @NotEmpty
        private List<Long> ids;
        @Min(0)
        @Max(999)
        private short sticky;
        private OffsetDateTime stickyDate;

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }

        public short getSticky() {
            return sticky;
        }

        public void setSticky(short sticky) {
            this.sticky = sticky;
        }

        public OffsetDateTime getStickyDate() {
            return stickyDate;
        }

        public void setStickyDate(OffsetDateTime stickyDate) {
            this.stickyDate = stickyDate;
        }
    }

    @PutMapping("/sticky")
    @PreAuthorize("hasAnyAuthority('article:sticky','*')")
    @OperationLog(module = "article", operation = "sticky", type = OperationType.UPDATE)
    public ResponseEntity<Body> sticky(@RequestBody @Valid ArticleStickyParams params, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = selectForWrite(params.ids, site, user);
        for (Article bean : articles) {
            service.updateSticky(bean, params.sticky, params.stickyDate);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    public static class ArticleMoveParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @NotEmpty
        private List<Long> ids = Collections.emptyList();
        @NotNull
        private Long channelId = 0L;

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }

        public Long getChannelId() {
            return channelId;
        }

        public void setChannelId(Long channelId) {
            this.channelId = channelId;
        }
    }

    public record MoveResult(int moved, int skipped) {
    }

    /**
     * 批量移动栏目（docs/spec/article-management/plan.md Phase 3）。目标栏目须属于本站、在操作员的文章权限之内，且是
     * 末级的文章栏目——与文章表单的栏目选择器同一规则。不合条件的文章（审核中的、不可编辑的、文章模型不同的、
     * 目标栏目里已有同一篇的）略过，结果里给出移动与略过的篇数
     */
    @PutMapping("/move")
    @PreAuthorize("hasAnyAuthority('article:update','*')")
    @OperationLog(module = "article", operation = "move", type = OperationType.UPDATE)
    public ResponseEntity<Body> move(@RequestBody @Valid ArticleMoveParams params, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = selectForWrite(params.ids, site, user);
        Channel target = channelService.selectInSite(params.channelId, site.getId());
        dataPermission.scopeOf(user, site.getId()).requireChannels(Collections.singleton(target.getId()));
        if (!Boolean.TRUE.equals(target.getReal()) || target.isLink()
                || !channelService.listChildren(target.getId()).isEmpty()) {
            throw new Http400Exception("error.article.move.targetInvalid", target.getName());
        }
        ArticleService.MoveResult result = service.move(articles, target, user);
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            // 按原栏目分批：新栏目与原栏目的列表页、两边上一篇下一篇的文章页都要重新生成
            result.movedByOldChannel().forEach((oldChannelId, moved) ->
                    generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, moved, oldChannelId));
        }
        return Responses.ok(new MoveResult(result.moved(), result.skipped()));
    }

    @PutMapping("/submit")
    @PreAuthorize("hasAnyAuthority('article:submit','*')")
    @OperationLog(module = "article", operation = "submit", type = OperationType.UPDATE)
    public ResponseEntity<Body> submit(@RequestBody List<Long> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = selectForWrite(ids, site, user);
        // 先整体校验、再逐篇提交：下线时间已过的文章提交了也只会是已下线，有一篇如此整批都不执行（D4）
        articles.forEach(ArticleService::requireOfflineDateAhead);
        for (Article bean : articles) {
            service.submit(bean, user.getId());
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    /**
     * 退回待审核的文章（docs/spec/member-contribute/plan.md D6）：会员投稿到没有审批流程的栏目时停在待审核、无流程实例，
     * “通过”就是提交，“退回”走这里。进入了审批流程的文章由“文章审核”退回，这里拒绝——两处各管各的流程实例
     */
    @PutMapping("/reject")
    @PreAuthorize("hasAnyAuthority('article:reject','*')")
    @OperationLog(module = "article", operation = "reject", type = OperationType.UPDATE)
    public ResponseEntity<Body> reject(@RequestBody @Valid RejectParams params) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = selectForWrite(params.ids(), site, user);
        for (Article bean : articles) {
            if (bean.getStatus() != Article.STATUS_PENDING || bean.getProcessInstanceId() != null) {
                throw new Http400Exception("error.article.rejectInProcess", bean.getTitle());
            }
        }
        for (Article bean : articles) {
            service.reject(bean, params.reason());
        }
        return Responses.ok();
    }

    public record RejectParams(@NotEmpty List<Long> ids, @NotBlank @Length(max = 300) String reason) {
    }

    /**
     * 待审投稿数：待审核、录入类型为投稿、尚未进入审批流程（进入了的由“文章审核”计数），在操作员的文章权限与数据范围之内。
     * 供首页待办胶囊显示
     */
    @GetMapping("contribute-pending-count")
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public long contributePendingCount() {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        ArticleArgs args = ArticleArgs.of();
        args.siteId(site.getId());
        args.status(Collections.singleton(Article.STATUS_PENDING));
        args.inputType(Article.INPUT_TYPE_CONTRIBUTE);
        args.withoutProcessInstance();
        dataPermission.filter(args, user);
        return service.count(args);
    }

    @PutMapping("/archive")
    @PreAuthorize("hasAnyAuthority('article:archive','*')")
    @OperationLog(module = "article", operation = "archive", type = OperationType.UPDATE)
    public ResponseEntity<Body> archive(@RequestBody List<Long> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = selectForWrite(ids, site, user);
        for (Article bean : articles) {
            service.archive(bean);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    @PutMapping("/offline")
    @PreAuthorize("hasAnyAuthority('article:offline','*')")
    @OperationLog(module = "article", operation = "offline", type = OperationType.UPDATE)
    public ResponseEntity<Body> offline(@RequestBody List<Long> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = selectForWrite(ids, site, user);
        for (Article bean : articles) {
            service.offline(bean, user);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    @PutMapping("/delete")
    @PreAuthorize("hasAnyAuthority('article:delete','*')")
    @OperationLog(module = "article", operation = "delete", type = OperationType.UPDATE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = selectForWrite(ids, site, user);
        for (Article bean : articles) {
            service.delete(bean, user);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    /**
     * 从回收站恢复为草稿（docs/spec/article-management/plan.md D2）。与删除同一权限：能放进回收站的人就能取回来。
     * 已删除与草稿都不对外展示，不必重新生成静态页
     */
    @PutMapping("/restore")
    @PreAuthorize("hasAnyAuthority('article:delete','*')")
    @OperationLog(module = "article", operation = "restore", type = OperationType.UPDATE)
    public ResponseEntity<Body> restore(@RequestBody List<Long> ids) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = selectForWrite(ids, site, user);
        for (Article bean : articles) {
            service.restore(bean);
        }
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('article:completelyDelete','*')")
    @OperationLog(module = "article", operation = "completelyDelete", type = OperationType.DELETE)
    public ResponseEntity<Body> completelyDelete(@RequestBody List<Long> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = selectForWrite(ids, site, user);
        for (Article bean : articles) {
            service.completelyDelete(bean, user);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    /**
     * 相关文章启发式候选（docs/spec/article-related/plan.md §3）。已保存文章传 {@code id}；未保存的新文章传
     * {@code title / channelId / tagNames} 组装种子。{@code excludeIds} 为已在列表中的文章（逗号分隔）
     */
    @GetMapping("related-candidates")
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public List<CandidateView> relatedCandidates(Long id, String title, Long channelId, String tagNames,
                                                 String excludeIds, Integer limit) {
        Site site = Contexts.getCurrentSite();
        ArticleRelatedService.Seed seed;
        if (id != null) {
            Article article = Optional.ofNullable(service.select(id))
                    .orElseThrow(() -> new Http404Exception(Article.NOT_FOUND + id));
            ValidUtils.dataInSite(article.getSiteId(), site.getId());
            seed = ArticleRelatedService.seedOf(article);
            if (StringUtils.isNotBlank(title)) {
                // 表单里的当前标题（可能尚未保存）优先于库中标题
                seed = new ArticleRelatedService.Seed(seed.siteId(), channelId != null ? channelId : seed.channelId(),
                        title, seed.seoKeywords(), seed.tags(), seed.excludeId());
            }
        } else {
            List<Tag> tags = new ArrayList<>();
            for (String name : StringUtils.isBlank(tagNames) ? new String[0] : tagNames.split(",")) {
                Optional.ofNullable(tagService.selectByName(site.getId(), name.trim())).ifPresent(tags::add);
            }
            seed = new ArticleRelatedService.Seed(site.getId(), channelId, StringUtils.defaultString(title),
                    null, tags, null);
        }
        Set<Long> exclude = new HashSet<>();
        if (StringUtils.isNotBlank(excludeIds)) {
            for (String s : excludeIds.split(",")) {
                if (StringUtils.isNumeric(s.trim())) {
                    exclude.add(Long.valueOf(s.trim()));
                }
            }
        }
        int size = limit == null || limit <= 0 ? ArticleRelatedService.CANDIDATE_LIMIT
                : Math.min(limit, ArticleRelatedService.CANDIDATE_MAX);
        return relatedService.candidates(seed, exclude, size, false).stream()
                .map(candidate -> CandidateView.of(candidate, null)).toList();
    }

    @GetMapping("title-similarity")
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public List<EsArticle> titleSimilarity(double similarity, String title, Long excludeId) {
        Site site = Contexts.getCurrentSite();
        return service.titleSimilarity(similarity, title, site.getId(), excludeId);
    }

    @GetMapping("slug-exist")
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public boolean slugExist(@NotBlank String slug, @Nullable Long id) {
        return service.existsBySlug(slug, Contexts.getCurrentSiteId(), id);
    }

    /**
     * 别名建议（控制台标题失焦且别名留空时调用；slugify 只在服务端实现一份）。返回空串表示保存时将回退为 ID
     */
    @GetMapping("slug-suggest")
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public SlugSuggestResult slugSuggest(@NotBlank String title, @Nullable Long id) {
        Long siteId = Contexts.getCurrentSiteId();
        String slug = slugGenerator.suggest(siteId, title, SlugGenerator.ARTICLE_MAX_LENGTH,
                candidate -> service.existsBySlug(candidate, siteId, id));
        return new SlugSuggestResult(slug);
    }

    public record SlugSuggestResult(String slug) {
    }

    private void validateSlug(String slug, Long siteId, @Nullable Long excludeId) {
        if (StringUtils.isNotBlank(slug) && service.existsBySlug(slug, siteId, excludeId)) {
            throw new Http400Exception("slug exist: " + slug);
        }
    }

    private void validateBean(ArticleExt ext) {
        // 只拦敏感词；易错词多与语境有关（登陆/登录、截止/截至），只在编辑器里提示，由编辑决定是否修正
        // （docs/spec/word-dictionaries/plan.md D1）。按纯文本筛查（screening.md §3）：
        // 原始HTML里被行内标签拆开的词会漏拦，而藏在属性/URL里的词会误拦
        String word = wordScreeningService.firstSensitiveWord(ext);
        if (word != null) {
            throw new Http400Exception("error.sensitiveWordNotAllowed", word);
        }
    }

    // 来源三元组（origin/originId/originHash）由种子导入器专管：新建不得伪造主题来源，编辑不得改写或清除
    private static final String[] EXCLUDE_FIELDS = {"siteId", "srcId", "userId", "modifiedUserId",
            "withImage", "sticky", "inputType", "type", "status", "created", "modified", "stickyDate",
            "processInstanceId", "rejectReason", "staticFile", "mobileStaticFile", "views",
            "origin", "originId", "originHash"};

    private static final String TASK_HTML_ARTICLE_RELATED = "task.html.articleRelated";
}
