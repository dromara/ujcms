package com.ujcms.cms.core.web.backendapi;

import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.http.ResponseEntity;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.component.ArticleDataPermission;
import com.ujcms.cms.core.component.TemplateService;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.generator.LuceneGenerator;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.web.support.MoveParams;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.common.db.tree.TreeMoveType;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.Servlets;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import com.ujcms.common.web.exception.LogicException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 栏目 Controller（docs/spec/channel-management/plan.md）
 *
 * @author PONY
 */
@RestController("backendChannelController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/channel")
public class ChannelController {
    /**
     * 分页条数上限（与控制台表单一致）
     */
    private static final int PAGE_SIZE_MAX = ChannelService.PAGE_SIZE_MAX;

    private final HtmlGenerator generator;
    private final LuceneGenerator luceneGenerator;
    private final ChannelService service;
    private final SiteService siteService;
    private final OrgService orgService;
    private final TemplateService templateService;
    private final SlugGenerator slugGenerator;
    private final ArticleDataPermission articleDataPermission;

    public ChannelController(HtmlGenerator generator, LuceneGenerator luceneGenerator, ChannelService service,
                             SiteService siteService, OrgService orgService, TemplateService templateService,
                             SlugGenerator slugGenerator, ArticleDataPermission articleDataPermission) {
        this.articleDataPermission = articleDataPermission;
        this.generator = generator;
        this.luceneGenerator = luceneGenerator;
        this.service = service;
        this.siteService = siteService;
        this.orgService = orgService;
        this.templateService = templateService;
        this.slugGenerator = slugGenerator;
    }

    /**
     * 列表的查询条件、排序可以关联的字段（docs/spec/article-data-permission/plan.md D24）：栏目列表按栏目模型、文章模型的名称排序
     */
    private static final Set<String> QUERY_JOINS = Set.of("channelModel@model-name", "articleModel@model-name");

    @GetMapping
    @JsonView(Views.List.class)
    @PreAuthorize("hasAnyAuthority('channel:list','*')")
    public List<Channel> list(@RequestParam(defaultValue = "false") boolean isArticlePermission,
                              @RequestParam(defaultValue = "true") boolean isIncludeChildren,
                              @RequestParam(defaultValue = "false") boolean isIncludeSelf,
                              Long parentId, Long siteId, Boolean isReal,
                              HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        ChannelArgs args = ChannelArgs.of(getQueryMap(request.getQueryString(), QUERY_JOINS));
        args.isReal(isReal);
        if (isArticlePermission) {
            // 文章表单等处的栏目选择器：只列出文章权限之内的栏目。服务端据同一份数据拒绝越权的写入
            articleDataPermission.filter(args, user);
        }
        Long currentSiteId = Contexts.getCurrentSiteId();
        if (siteId == null) {
            siteId = currentSiteId;
        } else if (!siteId.equals(currentSiteId)) {
            // 跨站点读取（如多语言面板选择目标站点的栏目）：须对该站点有权限，规则同 BackendInterceptor
            requireSiteAccess(user, siteId);
        }
        // 各分支一律限定站点：parentId 来自请求，不能让其它站点的栏目经由上级、祖先条件被查出
        args.siteId(siteId);
        if (isIncludeChildren) {
            args.ancestorId(parentId);
        } else if (parentId == null) {
            args.parentIdIsNull();
        } else if (isIncludeSelf) {
            args.parentIdAndSelf(parentId);
        } else {
            args.parentId(parentId);
        }
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('channel:show','*')")
    public Channel show(@PathVariable Long id) {
        Channel bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception(Channel.NOT_FOUND + id);
        }
        ValidUtils.dataInSite(bean.getSiteId(), Contexts.getCurrentSiteId());
        bean.setContributeGroupIds(service.listContributeGroupIds(bean.getId()));
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('channel:create','*')")
    @OperationLog(module = "channel", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid Channel bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        bean.setSiteId(site.getId());
        Channel channel = new Channel();
        // 来源三元组由种子导入器专管：新建不得伪造主题来源。时间、浏览量、树形字段与静态页路径由服务端维护
        Entities.copy(bean, channel, "origin", "originId", "originHash",
                "created", "modified", "views", "selfViews", "depth", "order", "staticFile", "mobileStaticFile");
        channel.setCustoms(bean.getCustoms());
        Long parentId = channel.getParentId();
        if (parentId != null) {
            // 上级栏目须存在、属于本站，且在操作者的栏目权限之内
            service.selectInSite(parentId, site.getId());
            requireChannelPermission(user, site.getId(), List.of(parentId));
        }
        validateBean(channel, null);
        // 授权：有上级栏目照搬上级的，根栏目给所有未设“全部权限”的用户组、角色；投稿用户组表单有填则按表单
        ChannelService.Grants grants = service.defaultGrants(site.getId(), parentId);
        if (bean.getContributeGroupIds() != null) {
            grants = new ChannelService.Grants(grants.groupIds(), grants.articleRoleIds(), grants.channelRoleIds(),
                    bean.getContributeGroupIds());
        }
        service.insert(channel, grants);

        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_CHANNEL_RELATED);
            generator.updateChannelRelatedHtml(channel.getSiteId(), user.getId(), taskName, channel.getId());
        }
        return Responses.ok();
    }

    /**
     * 批量新增：按缩进文本解析出的一棵（或多棵）子树，一次建在所选上级栏目之下。全有或全无，校验见
     * {@link ChannelService#insertBatch}
     */
    @PostMapping("batch-create")
    @PreAuthorize("hasAnyAuthority('channel:create','*')")
    @OperationLog(module = "channel", operation = "batchCreate", type = OperationType.CREATE)
    public BatchCreateResult batchCreate(@RequestBody @Valid BatchCreateParams params, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Long parentId = params.parentId();
        if (parentId != null) {
            service.selectInSite(parentId, site.getId());
            requireChannelPermission(user, site.getId(), List.of(parentId));
        }
        List<Channel> created = service.insertBatch(site.getId(), parentId,
                params.channelModelId(), params.articleModelId(), params.items());
        if (site.getHtml().isEnabledAndAuto()) {
            // 新栏目各自的栏目页，以及其它栏目页上的导航：一次生成全部栏目页，不逐个起任务
            String taskName = Servlets.getMessage(request, "task.html.channel");
            generator.updateChannelHtml(site.getId(), user.getId(), taskName, site);
        }
        return new BatchCreateResult(created.size());
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('channel:update','*')")
    @OperationLog(module = "channel", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid Channel bean, HttpServletRequest request) {
        return update(bean, request, channel -> {
            Original original = new Original(channel.getSlug(), channel.getChannelModelId(),
                    channel.getArticleModelId(), channel.getType(), channel.getPageSize(), channel.getProcessKey());
            // 来源三元组由种子导入器专管：编辑不得改写或清除。创建时间、浏览量、树形字段不随表单回写；
            // 静态页路径不在响应里（@JsonIgnore），随表单回写即被清空，旧静态页就再也删不掉
            Entities.copy(bean, channel, "siteId", "parentId", "order", "origin", "originId", "originHash",
                    "created", "modified", "views", "selfViews", "depth", "staticFile", "mobileStaticFile");
            channel.setCustoms(bean.getCustoms());
            validateBean(channel, original);
            service.update(channel, null, null, null, bean.getContributeGroupIds());
        });
    }

    @PutMapping("nav")
    @PreAuthorize("hasAnyAuthority('channel:update','*')")
    @OperationLog(module = "channel", operation = "updateNav", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateNav(@RequestBody @Valid Channel bean, HttpServletRequest request) {
        return update(bean, request, channel ->
                service.updateNav(Collections.singletonList(bean.getId()), bean.getNav()));
    }

    @PutMapping("real")
    @PreAuthorize("hasAnyAuthority('channel:update','*')")
    @OperationLog(module = "channel", operation = "updateReal", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateReal(@RequestBody @Valid Channel bean, HttpServletRequest request) {
        return update(bean, request, channel ->
                service.updateReal(Collections.singletonList(bean.getId()), bean.getReal()));
    }

    private ResponseEntity<Body> update(Channel bean, HttpServletRequest request, Consumer<Channel> handleChannel) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Channel channel = service.selectInSite(bean.getId(), site.getId());
        requireChannelPermission(user, site.getId(), List.of(channel.getId()));
        handleChannel.accept(channel);
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_CHANNEL_RELATED);
            generator.updateChannelRelatedHtml(channel.getSiteId(), user.getId(), taskName, channel.getId());
        }
        return Responses.ok();
    }

    /**
     * 批量设置：把勾选的属性应用到所选栏目，可连同它们的全部下级栏目。全有或全无，校验见
     * {@link ChannelService#updateBatch}
     */
    @PutMapping("batch-update")
    @PreAuthorize("hasAnyAuthority('channel:update','*')")
    @OperationLog(module = "channel", operation = "batchUpdate", type = OperationType.UPDATE)
    public BatchUpdateResult batchUpdate(@RequestBody @Valid BatchUpdateParams params, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Set<Long> targetIds = service.listBatchTargetIds(site.getId(), params.ids(), params.includeChildren());
        // 栏目权限须覆盖每一个目标栏目，连带的下级栏目也不例外
        requireChannelPermission(user, site.getId(), targetIds);
        int updated = service.updateBatch(site.getId(), targetIds, params.toSettings());
        if (site.getHtml().isEnabledAndAuto()) {
            // 与单个编辑同样的范围（栏目页、各级上级栏目页、首页），合成一个任务，不逐个栏目起任务
            String taskName = Servlets.getMessage(request, TASK_HTML_CHANNEL_RELATED);
            generator.updateChannelsRelatedHtml(site.getId(), user.getId(), taskName, targetIds);
        }
        return new BatchUpdateResult(updated);
    }

    @PutMapping("move")
    @PreAuthorize("hasAnyAuthority('channel:update','*')")
    @OperationLog(module = "channel", operation = "move", type = OperationType.UPDATE)
    public ResponseEntity<Body> move(@RequestBody @Valid MoveParams params) {
        moveChannels(List.of(params.getFromId()), params.getToId(), params.getType());
        return Responses.ok();
    }

    @PutMapping("batch-move")
    @PreAuthorize("hasAnyAuthority('channel:update','*')")
    @OperationLog(module = "channel", operation = "batchMove", type = OperationType.UPDATE)
    public ResponseEntity<Body> batchMove(@RequestBody @Valid BatchMoveParams params) {
        moveChannels(params.getFromIds(), params.getToId(), params.getType());
        return Responses.ok();
    }

    private void moveChannels(List<Long> fromIds, Long toId, TreeMoveType type) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Channel to = service.selectInSite(toId, site.getId());
        // 栏目权限：被移动的栏目；移动后换了上级的，还须对新上级有权限（之内为目标栏目，之前、之后为目标栏目的上级；根下不限）
        Long newParentId = type == TreeMoveType.INNER ? to.getId() : to.getParentId();
        Set<Long> scoped = new LinkedHashSet<>(fromIds);
        if (newParentId != null) {
            for (Long fromId : fromIds) {
                if (!newParentId.equals(service.selectInSite(fromId, site.getId()).getParentId())) {
                    scoped.add(newParentId);
                    break;
                }
            }
        }
        requireChannelPermission(user, site.getId(), scoped);
        service.moveBatch(fromIds, toId, type, site.getId());
    }

    @PutMapping("batch-merge")
    @PreAuthorize("hasAnyAuthority('channel:update','*')")
    @OperationLog(module = "channel", operation = "batchMerge", type = OperationType.UPDATE)
    public ResponseEntity<Body> batchMerge(@RequestBody @Valid BatchMergeParams params, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        // 合并会删除原栏目（连同其子栏目）：除编辑权限外，还须有删除权限
        requireDeleteAuthority();
        Long toId = params.getToId();
        service.selectInSite(toId, site.getId());
        Set<Long> scoped = new LinkedHashSet<>();
        scoped.add(toId);
        for (Long fromId : params.getFromIds()) {
            scoped.addAll(service.listSubtreeIds(fromId));
        }
        requireChannelPermission(user, site.getId(), scoped);
        int moved = service.mergeBatch(params.getFromIds(), toId, site.getId());
        if (moved > 0) {
            // 文章改属目标栏目，全文索引里的栏目信息随之过期
            String taskName = Servlets.getMessage(request, "task.fulltext.channel");
            luceneGenerator.reindexChannel(site.getId(), user.getId(), taskName, toId);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.all");
            generator.updateAllHtml(site.getId(), user.getId(), taskName, site);
        }
        return Responses.ok();
    }

    /**
     * 整理树形结构
     */
    @PutMapping("tidy-tree")
    @PreAuthorize("hasAnyAuthority('channel:tidyTree','*')")
    @OperationLog(module = "channel", operation = "tidyTree", type = OperationType.UPDATE)
    public ResponseEntity<Body> tidyTree() {
        service.tidyTree(Contexts.getCurrentSiteId());
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('channel:delete','*')")
    @OperationLog(module = "channel", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        // 先整体校验、再动手删除：栏目权限须覆盖整棵子树；子树内仍有文章则整体拒绝，不留下删到一半的树
        List<Long> existingIds = new ArrayList<>(ids.size());
        Set<Long> scoped = new LinkedHashSet<>();
        for (Long id : ids) {
            Channel bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            existingIds.add(id);
            scoped.add(id);
            scoped.addAll(service.listSubtreeIds(id));
        }
        requireChannelPermission(user, site.getId(), scoped);
        for (Long id : existingIds) {
            if (service.existsArticleInSubtree(id)) {
                throw new LogicException("error.refer.article");
            }
        }
        for (Long id : existingIds) {
            deleteTree(id);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.all");
            generator.updateAllHtml(site.getId(), user.getId(), taskName, site);
        }
        return Responses.ok();
    }

    /**
     * 自下而上递归删除栏目。不能放到Service里面递归，这样会因事务太大，导致性能低下。
     * 每个栏目由 Service 按 ID 重新读取后删除：它的 order 已因之前的删除而变化，清理静态页也需要整行（含 ext）
     *
     * @param id 栏目ID
     * @return 删除条数
     */
    private int deleteTree(Long id) {
        int deleted = 0;
        for (Channel child : service.listChildren(id)) {
            deleted += deleteTree(child.getId());
        }
        return deleted + service.delete(id);
    }

    @GetMapping("channel-templates")
    @PreAuthorize("hasAnyAuthority('channel:list','*')")
    public List<String> channelTemplate() throws IOException {
        Site site = Contexts.getCurrentSite();
        String storagePath = site.getConfig().getTemplateStorage().getPath();
        return templateService.getTemplates(storagePath + "/" + site.getThemeKey(), Channel.TEMPLATE_PREFIX);
    }

    @GetMapping("article-templates")
    @PreAuthorize("hasAnyAuthority('channel:list','*')")
    public List<String> articleTemplate() throws IOException {
        Site site = Contexts.getCurrentSite();
        String storagePath = site.getConfig().getTemplateStorage().getPath();
        return templateService.getTemplates(storagePath + "/" + site.getThemeKey(), Article.TEMPLATE_PREFIX);
    }

    @GetMapping("channel-permissions")
    @PreAuthorize("hasAnyAuthority('channel:list','*')")
    public List<Long> channelPermissions(@Nullable Long siteId) {
        User user = Contexts.getCurrentUser();
        Collection<Long> roleIds = user.fetchRoleIds();
        Set<Long> orgIds = user.fetchAllOrgIds();
        return service.listChannelPermissions(roleIds, orgIds, siteId != null ? siteId : getCurrentSiteId());
    }

    @GetMapping("slug-exist")
    @PreAuthorize("hasAnyAuthority('channel:list','*')")
    public boolean slugExist(@NotBlank String slug) {
        return service.existsBySlug(slug, Contexts.getCurrentSiteId());
    }

    /**
     * 别名建议（名称失焦且别名留空时调用）。返回空串表示保存时将回退为 ID
     */
    @GetMapping("slug-suggest")
    @PreAuthorize("hasAnyAuthority('channel:list','*')")
    public SlugSuggestResult slugSuggest(@NotBlank String name, @Nullable Long id) {
        Long siteId = Contexts.getCurrentSiteId();
        String slug = slugGenerator.suggest(siteId, name, SlugGenerator.CHANNEL_MAX_LENGTH,
                candidate -> service.slugTakenByOther(siteId, candidate, id));
        return new SlugSuggestResult(slug);
    }

    public record SlugSuggestResult(String slug) {
    }

    /**
     * 栏目权限（角色、组织的“栏目权限”数据范围）校验。与 {@link #channelPermissions} 同一查询：控制台据该接口置灰，
     * 这里据它拒绝，两边不会各说各话。拥有全部栏目权限者不受限
     *
     * @param channelIds 须在权限范围内的栏目ID
     */
    private void requireChannelPermission(User user, Long siteId, Collection<Long> channelIds) {
        if (channelIds.isEmpty() || user.hasAllChannelPermission()) {
            return;
        }
        Collection<Long> roleIds = user.fetchRoleIds();
        Set<Long> orgIds = user.fetchAllOrgIds();
        // 既无角色也无组织时，查询不带任何条件、会返回全部栏目：此时应视为没有任何栏目权限
        Set<Long> permitted = roleIds.isEmpty() && orgIds.isEmpty() ? Set.of()
                : new HashSet<>(service.listChannelPermissions(roleIds, orgIds, siteId));
        for (Long id : channelIds) {
            if (!permitted.contains(id)) {
                throw new Http403Exception("error.channel.forbidden", String.valueOf(id));
            }
        }
    }

    /**
     * 当前用户须对该站点有权限（用户所属组织与站点所属组织有上下级关系），规则同 BackendInterceptor 对请求头站点的校验
     */
    private void requireSiteAccess(User user, Long siteId) {
        Site site = Optional.ofNullable(siteService.select(siteId))
                .orElseThrow(() -> new Http404Exception(Site.NOT_FOUND + siteId));
        if (!orgService.hasRelationship(user.getOrgId(), site.getOrgId())) {
            throw new Http403Exception("error.siteForbidden", site.getName(), String.valueOf(site.getId()));
        }
    }

    /**
     * 须同时具有 channel:delete。@PreAuthorize 只写单一权限码（权限树一致性脚本按此解析），第二个权限在方法内校验
     */
    private static void requireDeleteAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean granted = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "channel:delete".equals(authority) || "*".equals(authority));
        if (!granted) {
            throw new Http403Exception("error.channel.merge.deleteRequired");
        }
    }

    /**
     * 校验栏目的可写字段。编辑时只校验改动过的字段：历史数据里已不可用的模型、超限的分页条数，
     * 不应妨碍修改其它字段
     *
     * @param original 编辑前的取值。新建时为 {@code null}，全部校验
     */
    private void validateBean(Channel channel, @Nullable Original original) {
        Long siteId = channel.getSiteId();
        String slug = channel.getSlug();
        // 别名格式由 Channel#getSlug 上的 @Pattern 校验；这里只管站内唯一
        if ((original == null || !Objects.equals(original.slug(), slug)) && StringUtils.isNotBlank(slug)
                && service.slugTakenByOther(siteId, slug, original == null ? null : channel.getId())) {
            throw new Http400Exception("error.channel.slugExist", slug);
        }
        if (original == null || !Objects.equals(original.channelModelId(), channel.getChannelModelId())
                || !Objects.equals(original.articleModelId(), channel.getArticleModelId())) {
            service.validateModels(siteId, channel.getChannelModelId(), channel.getArticleModelId());
        }
        Short type = channel.getType();
        if ((original == null || !Objects.equals(original.type(), type))
                && (type == null || type < Channel.TYPE_NORMAL || type > Channel.TYPE_LINK_CHILD)) {
            throw new Http400Exception("error.channel.typeInvalid", String.valueOf(type));
        }
        Short pageSize = channel.getPageSize();
        if ((original == null || !Objects.equals(original.pageSize(), pageSize))
                && (pageSize == null || pageSize < 1 || pageSize > PAGE_SIZE_MAX)) {
            throw new Http400Exception("error.channel.pageSizeInvalid", String.valueOf(pageSize));
        }
        // 审核流程须是本站已部署的文章流程（docs/spec/article-data-permission/plan.md D23）；没改的照旧
        if (original == null || !Objects.equals(original.processKey(), channel.getProcessKey())) {
            service.validateProcessKey(siteId, channel.getProcessKey());
        }
    }

    /**
     * 编辑前的取值，用于判断哪些字段被改动
     */
    private record Original(@Nullable String slug, Long channelModelId, Long articleModelId,
                            Short type, Short pageSize, @Nullable String processKey) {
    }

    public static final String TASK_HTML_CHANNEL_RELATED = "task.html.channelRelated";

    /**
     * 批量新增参数
     *
     * @param parentId       上级栏目ID。为空则建在根下
     * @param channelModelId 栏目模型ID（本批全部栏目共用）
     * @param articleModelId 文章模型ID（本批全部栏目共用）
     * @param items          待建栏目，上级先于下级
     */
    public record BatchCreateParams(
            @Nullable Long parentId, @NotNull Long channelModelId, @NotNull Long articleModelId,
            @NotEmpty @Size(max = ChannelService.BATCH_MAX_SIZE) List<ChannelService.@NotNull BatchItem> items)
            implements Serializable {
    }

    /**
     * 批量新增结果
     *
     * @param created 新建的栏目数
     */
    public record BatchCreateResult(int created) {
    }

    /**
     * 批量设置参数。{@code fields} 列出要改写的属性，只有列出的才写入；模板、审核流程列出而不给值即清空
     *
     * @param ids             所选栏目ID
     * @param includeChildren 是否连同所选栏目的全部下级栏目
     * @param fields          要改写的属性名，见 {@link ChannelService#BATCH_UPDATE_FIELDS}
     */
    public record BatchUpdateParams(
            @NotEmpty @Size(max = ChannelService.BATCH_MAX_SIZE) List<@NotNull Long> ids,
            boolean includeChildren,
            @NotEmpty List<@NotBlank String> fields,
            @Nullable Long articleModelId, @Nullable String channelTemplate, @Nullable String articleTemplate,
            @Nullable String processKey, @Nullable Boolean nav, @Nullable Boolean real,
            @Nullable Boolean allowSearch, @Nullable Short pageSize) implements Serializable {
        ChannelService.BatchSettings toSettings() {
            return new ChannelService.BatchSettings(fields, articleModelId, channelTemplate, articleTemplate,
                    processKey, nav, real, allowSearch, pageSize);
        }
    }

    /**
     * 批量设置结果
     *
     * @param updated 更新的栏目数
     */
    public record BatchUpdateResult(int updated) {
    }

    public static class BatchMoveParams implements Serializable {
        private static final long serialVersionUID = 1;

        @NotEmpty
        private List<Long> fromIds;
        @NotNull
        private Long toId;
        @NotNull
        private TreeMoveType type = TreeMoveType.INNER;

        public List<Long> getFromIds() {
            return fromIds;
        }

        public void setFromIds(List<Long> fromIds) {
            this.fromIds = fromIds;
        }

        public Long getToId() {
            return toId;
        }

        public void setToId(Long toId) {
            this.toId = toId;
        }

        public TreeMoveType getType() {
            return type;
        }

        public void setType(TreeMoveType type) {
            this.type = type;
        }
    }

    public static class BatchMergeParams implements Serializable {
        private static final long serialVersionUID = 1;

        @NotEmpty
        private List<Long> fromIds;
        @NotNull
        private Long toId;

        public List<Long> getFromIds() {
            return fromIds;
        }

        public void setFromIds(List<Long> fromIds) {
            this.fromIds = fromIds;
        }

        public Long getToId() {
            return toId;
        }

        public void setToId(Long toId) {
            this.toId = toId;
        }
    }
}
