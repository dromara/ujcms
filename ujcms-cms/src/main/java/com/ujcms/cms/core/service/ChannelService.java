package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.component.ContentStatEvictor;
import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.domain.generated.GeneratedChannel;
import com.ujcms.cms.core.domain.generated.GeneratedGroup;
import com.ujcms.cms.core.domain.generated.GeneratedRole;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.listener.ChannelDeleteListener;
import com.ujcms.cms.core.listener.ModelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.db.tree.TreeMoveType;
import com.ujcms.common.db.tree.TreeService;
import com.ujcms.common.db.tree.TreeSortEntity;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.text.Slugs;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import com.ujcms.common.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.security.PolicyTier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 栏目 Service
 *
 * @author PONY
 */
@Service
public class ChannelService implements ModelDeleteListener, SiteDeleteListener {
    private final HtmlService htmlService;
    private final HtmlSanitizeService sanitizeService;
    private final AttachmentService attachmentService;
    private final ModelService modelService;
    private final ChannelMapper mapper;
    private final ChannelExtMapper extMapper;
    private final ChannelTreeMapper treeMapper;
    private final ArticleMapper articleMapper;
    private final GroupAccessMapper groupAccessMapper;
    private final GroupContributeMapper groupContributeMapper;
    private final RoleArticleMapper roleArticleMapper;
    private final RoleChannelMapper roleChannelMapper;
    private final OrgArticleMapper orgArticleMapper;
    private final OrgChannelMapper orgChannelMapper;
    private final SnowflakeSequence snowflakeSequence;
    private final SlugGenerator slugGenerator;
    private final ContentStatEvictor contentStatEvictor;
    private final RoleService roleService;
    private final GroupService groupService;
    private final ObjectProvider<ProcessDefinitionPort> processDefinitionPort;
    private final SiteMapper siteMapper;
    private final TreeService<Channel> treeService;

    public ChannelService(
            HtmlService htmlService, HtmlSanitizeService sanitizeService, AttachmentService attachmentService,
            ModelService modelService, ChannelMapper mapper, ChannelExtMapper extMapper, ChannelTreeMapper treeMapper,
            ArticleMapper articleMapper, GroupAccessMapper groupAccessMapper,
            GroupContributeMapper groupContributeMapper, RoleArticleMapper roleArticleMapper,
            RoleChannelMapper roleChannelMapper, OrgArticleMapper orgArticleMapper, OrgChannelMapper orgChannelMapper,
            SnowflakeSequence snowflakeSequence, SlugGenerator slugGenerator, ContentStatEvictor contentStatEvictor,
            RoleService roleService, GroupService groupService,
            ObjectProvider<ProcessDefinitionPort> processDefinitionPort, SiteMapper siteMapper) {
        this.htmlService = htmlService;
        this.sanitizeService = sanitizeService;
        this.attachmentService = attachmentService;
        this.modelService = modelService;
        this.articleMapper = articleMapper;
        this.groupAccessMapper = groupAccessMapper;
        this.groupContributeMapper = groupContributeMapper;
        this.mapper = mapper;
        this.extMapper = extMapper;
        this.treeMapper = treeMapper;
        this.roleArticleMapper = roleArticleMapper;
        this.roleChannelMapper = roleChannelMapper;
        this.orgArticleMapper = orgArticleMapper;
        this.orgChannelMapper = orgChannelMapper;
        this.snowflakeSequence = snowflakeSequence;
        this.slugGenerator = slugGenerator;
        this.contentStatEvictor = contentStatEvictor;
        this.roleService = roleService;
        this.groupService = groupService;
        this.processDefinitionPort = processDefinitionPort;
        this.siteMapper = siteMapper;
        this.treeService = new TreeService<>(mapper, treeMapper);
    }

    /**
     * 别名留空时由服务端生成（docs/spec/permalinks/plan.md D4）：名称 slugify → 拼音（站点开关）→ ID，站内唯一。
     * 名称变更不重生成；清空别名即触发重生成
     */
    private void fillSlug(Channel bean, Predicate<String> reserved) {
        if (StringUtils.isBlank(bean.getSlug())) {
            // 站点行锁之下、以锁定读查重（docs/spec/article-data-permission/plan.md D22）
            bean.setSlug(slugGenerator.generate(bean.getSiteId(), bean.getName(), bean.getId(),
                    SlugGenerator.CHANNEL_MAX_LENGTH, slug -> reserved.test(slug) || slugTakenByOtherForUpdate(bean, slug)));
        }
    }

    private boolean slugTakenByOtherForUpdate(Channel bean, String slug) {
        Long id = mapper.selectIdBySlugForUpdate(bean.getSiteId(), slug);
        return id != null && !id.equals(bean.getId());
    }

    /**
     * 别名是否已被站内其他栏目占用。只查 ID：{@link #findBySiteIdAndSlug} 会连同上级、子栏目一并装配，不宜用于校验
     *
     * @param excludeId 排除的栏目ID（编辑时为自身）。可以为 {@code null}
     */
    public boolean slugTakenByOther(Long siteId, String slug, @Nullable Long excludeId) {
        Long id = mapper.selectIdBySlug(siteId, slug);
        return id != null && !id.equals(excludeId);
    }

    /**
     * 校验别名格式（字母、数字、下划线、连字符，不超过 {@link SlugGenerator#CHANNEL_MAX_LENGTH}）。空串合法：留空由服务端生成
     */
    public static void validateSlugFormat(@Nullable String slug) {
        if (StringUtils.isNotBlank(slug) && (!Slugs.isValid(slug) || slug.length() > SlugGenerator.CHANNEL_MAX_LENGTH)) {
            throw new Http400Exception("error.channel.slugInvalid", slug);
        }
    }

    /**
     * 校验栏目模型、文章模型：存在、类型相符、属于本站或全局共享
     */
    public void validateModels(Long siteId, Long channelModelId, Long articleModelId) {
        requireModel(siteId, channelModelId, Channel.MODEL_TYPE);
        requireModel(siteId, articleModelId, Article.MODEL_TYPE);
    }

    /**
     * 校验栏目的审核流程（docs/spec/article-data-permission/plan.md D23）：留空即无流程；否则须是本站已部署的文章流程。
     * 只在控制台新增、改换流程键时调用——没改的照旧
     */
    public void validateProcessKey(Long siteId, @Nullable String processKey) {
        if (StringUtils.isBlank(processKey)) {
            return;
        }
        ProcessDefinitionPort port = processDefinitionPort.getIfAvailable();
        if (port == null || !port.isDeployed(siteId, Article.PROCESS_CATEGORY, processKey)) {
            throw new Http400Exception("error.channel.processInvalid", processKey);
        }
    }

    private void requireModel(Long siteId, Long modelId, String type) {
        Model model = modelService.select(modelId);
        if (model == null || !type.equals(model.getType()) || !(model.isGlobal() || siteId.equals(model.getSiteId()))) {
            throw new Http400Exception("error.channel.modelInvalid", String.valueOf(modelId));
        }
    }

    /**
     * 读取本站栏目：不存在为 404，不属于该站点为 400
     */
    public Channel selectInSite(Long id, Long siteId) {
        Channel bean = mapper.select(id);
        if (bean == null) {
            throw new Http404Exception(Channel.NOT_FOUND + id);
        }
        if (!siteId.equals(bean.getSiteId())) {
            throw new Http400Exception(String.format("data site id '%d' not in current site id '%d'",
                    bean.getSiteId(), siteId));
        }
        return bean;
    }

    /**
     * 新建栏目的默认授权：前台浏览用户组、文章权限角色、栏目权限角色、投稿用户组（空表示全部用户组可投）
     */
    public record Grants(List<Long> groupIds, List<Long> articleRoleIds, List<Long> channelRoleIds,
                         List<Long> contributeGroupIds) {
    }

    /**
     * 新建栏目的默认授权：有上级栏目则照搬上级的；根栏目则授予所有未设“全部权限”的用户组与角色
     * （设了全部权限的无需逐栏目授权）。投稿用户组同样照搬上级的，根栏目为空（全部用户组）
     */
    public Grants defaultGrants(Long siteId, @Nullable Long parentId) {
        if (parentId != null) {
            return new Grants(groupAccessMapper.listGroupByChannelId(parentId, null),
                    roleArticleMapper.listRoleByChannelId(parentId, null),
                    roleChannelMapper.listRoleByChannelId(parentId, null),
                    groupContributeMapper.listGroupByChannelId(parentId));
        }
        return new Grants(
                groupService.listNotAllAccessPermission().stream().map(GeneratedGroup::getId).toList(),
                roleService.listNotAllArticlePermission(siteId).stream().map(GeneratedRole::getId).toList(),
                roleService.listNotAllChannelPermission(siteId).stream().map(GeneratedRole::getId).toList(),
                List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Channel bean, Grants grants) {
        Long parentId = bean.getParentId();
        doInsert(bean, parentId != null ? mapper.select(parentId) : null, grants, slug -> false);
    }

    /**
     * 栏目的投稿用户组ID。空表示全部用户组可投（docs/spec/member-contribute/plan.md）
     */
    public List<Long> listContributeGroupIds(Long channelId) {
        return groupContributeMapper.listGroupByChannelId(channelId);
    }

    /**
     * @param parent   上级栏目，须与 {@code bean.getParentId()} 对应。根栏目为 {@code null}
     * @param reserved 生成别名时须额外避开的别名（批量新增时为同批中显式填写的别名）
     */
    private void doInsert(Channel bean, @Nullable Channel parent, Grants grants, Predicate<String> reserved) {
        bean.setId(snowflakeSequence.nextId());
        fillSlug(bean, reserved);
        Model model = Optional.ofNullable(modelService.select(bean.getChannelModelId())).orElseThrow(() ->
                new IllegalArgumentException(Model.NOT_FOUND + bean.getChannelModelId()));
        bean.disassembleCustoms(model, sanitizeService);
        treeService.insert(bean, parent, bean.getSiteId());
        ChannelExt ext = bean.getExt();
        ext.setId(bean.getId());
        extMapper.insert(ext);
        insertGroupIds(grants.groupIds(), bean.getId(), bean.getSiteId());
        insertArticleRoleIds(grants.articleRoleIds(), bean.getId(), bean.getSiteId());
        insertChannelRoleIds(grants.channelRoleIds(), bean.getId(), bean.getSiteId());
        insertContributeGroupIds(grants.contributeGroupIds(), bean.getId(), bean.getSiteId());
        attachmentService.insertRefer(GeneratedChannel.TABLE_NAME, bean.getId(), bean.getAttachmentUrls(model));
        contentStatEvictor.channel(bean.getSiteId());
    }

    /**
     * 批量新增的一项
     *
     * @param name   名称
     * @param slug   别名。留空由服务端生成
     * @param parent 上级在本批中的序号（从 0 起，须小于自身序号）；-1 表示直接建在所选的上级栏目（或根）之下
     */
    public record BatchItem(String name, @Nullable String slug, int parent) {
    }

    /**
     * 批量新增栏目（docs/spec/channel-management/plan.md D3、D4）。同一事务、全有或全无：全部校验通过后才写入第一条，
     * 跳过任何一行都会让它的下级失去上级。逐个走 {@link #doInsert}，与单个新增是同一条写入路径；
     * 其余设置沿用所选上级栏目（与新增表单的默认值一致），根栏目取实体默认值；授权同 {@link #defaultGrants}。
     *
     * @param parentId 上级栏目ID。为 {@code null} 则建在根下
     * @return 新建的栏目，顺序同 {@code items}
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Channel> insertBatch(Long siteId, @Nullable Long parentId, Long channelModelId, Long articleModelId,
                                     List<BatchItem> items) {
        Channel parent = parentId != null ? selectInSite(parentId, siteId) : null;
        validateModels(siteId, channelModelId, articleModelId);
        // 同批中显式填写的别名（小写：MySQL 默认排序规则下唯一索引不分大小写）
        Set<String> slugs = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            BatchItem item = items.get(i);
            String no = String.valueOf(i + 1);
            if (item.parent() < -1 || item.parent() >= i) {
                throw new Http400Exception("error.channel.batch.parentInvalid", no);
            }
            String name = StringUtils.trimToEmpty(item.name());
            if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
                throw new Http400Exception("error.channel.batch.nameInvalid", no);
            }
            String slug = StringUtils.trimToEmpty(item.slug());
            if (slug.isEmpty()) {
                continue;
            }
            validateSlugFormat(slug);
            if (!slugs.add(slug.toLowerCase(Locale.ROOT))) {
                throw new Http400Exception("error.channel.batch.slugDuplicated", slug);
            }
            if (slugTakenByOther(siteId, slug, null)) {
                throw new Http400Exception("error.channel.slugExist", slug);
            }
        }
        Grants grants = defaultGrants(siteId, parentId);
        Predicate<String> reserved = slug -> slugs.contains(slug.toLowerCase(Locale.ROOT));
        List<Channel> created = new ArrayList<>(items.size());
        for (BatchItem item : items) {
            Channel owner = item.parent() < 0 ? parent : created.get(item.parent());
            Channel bean = new Channel();
            bean.setSiteId(siteId);
            bean.setParentId(owner != null ? owner.getId() : null);
            bean.setName(StringUtils.trimToEmpty(item.name()));
            bean.setSlug(StringUtils.trimToEmpty(item.slug()));
            bean.setChannelModelId(channelModelId);
            bean.setArticleModelId(articleModelId);
            if (parent != null) {
                bean.setChannelTemplate(parent.getChannelTemplate());
                bean.setArticleTemplate(parent.getArticleTemplate());
                bean.setNav(parent.getNav());
                bean.setReal(parent.getReal());
                bean.setAllowComment(parent.getAllowComment());
                bean.setAllowContribute(parent.getAllowContribute());
                bean.setAllowSearch(parent.getAllowSearch());
                bean.setOrderDesc(parent.getOrderDesc());
                bean.setPageSize(parent.getPageSize());
            }
            doInsert(bean, owner, grants, reserved);
            created.add(bean);
        }
        return created;
    }

    private void insertGroupIds(List<Long> groupIds, Long channelId, Long siteId) {
        groupIds.forEach(groupId -> groupAccessMapper.insert(new GroupAccess(groupId, channelId, siteId)));
    }

    private void insertArticleRoleIds(List<Long> articleRoleIds, Long channelId, Long siteId) {
        articleRoleIds.forEach(roleId -> roleArticleMapper.insert(new RoleArticle(roleId, channelId, siteId)));
    }

    private void insertChannelRoleIds(List<Long> channelRoleIds, Long channelId, Long siteId) {
        channelRoleIds.forEach(roleId -> roleChannelMapper.insert(new RoleChannel(roleId, channelId, siteId)));
    }

    private void insertContributeGroupIds(List<Long> groupIds, Long channelId, Long siteId) {
        groupIds.forEach(groupId -> groupContributeMapper.insert(new GroupContribute(groupId, channelId, siteId)));
    }

    /**
     * @param contributeGroupIds 投稿用户组；{@code null} 表示不改动
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Channel bean, @Nullable List<Long> groupIds,
                       @Nullable List<Long> articleRoleIds, @Nullable List<Long> channelRoleIds,
                       @Nullable List<Long> contributeGroupIds) {
        fillSlug(bean, slug -> false);
        bean.setModified(OffsetDateTime.now());
        Model model = Optional.ofNullable(modelService.select(bean.getChannelModelId())).orElseThrow(() ->
                new IllegalArgumentException(Model.NOT_FOUND + bean.getChannelModelId()));
        bean.disassembleCustoms(model, sanitizeService);
        mapper.update(bean);
        extMapper.update(bean.getExt());
        if (groupIds != null) {
            groupAccessMapper.deleteByChannelId(bean.getId());
            insertGroupIds(groupIds, bean.getId(), bean.getSiteId());
        }
        if (articleRoleIds != null) {
            roleArticleMapper.deleteByChannelId(bean.getId());
            insertArticleRoleIds(articleRoleIds, bean.getId(), bean.getSiteId());
        }
        if (channelRoleIds != null) {
            roleChannelMapper.deleteByChannelId(bean.getId());
            insertChannelRoleIds(channelRoleIds, bean.getId(), bean.getSiteId());
        }
        if (contributeGroupIds != null) {
            groupContributeMapper.deleteByChannelId(bean.getId());
            insertContributeGroupIds(contributeGroupIds, bean.getId(), bean.getSiteId());
        }
        attachmentService.updateRefer(GeneratedChannel.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    /**
     * 批量设置的内容。{@code fields} 列出要改写的属性（见 {@link #BATCH_UPDATE_FIELDS}），只有列出的才写入；
     * 模板、审核流程列出而不给值即清空（模板回到“主题默认”、栏目不再走审核流程），其余属性列出就必须给值
     */
    public record BatchSettings(Collection<String> fields, @Nullable Long articleModelId,
                                @Nullable String channelTemplate, @Nullable String articleTemplate,
                                @Nullable String processKey, @Nullable Boolean nav, @Nullable Boolean real,
                                @Nullable Boolean allowSearch, @Nullable Short pageSize) {
    }

    /**
     * 批量设置的目标栏目：所选栏目，{@code includeChildren} 时连同各自的全部下级（按闭包表，每个栏目只算一次）。
     * 所选栏目须都属于本站，否则 404
     */
    public Set<Long> listBatchTargetIds(Long siteId, Collection<Long> ids, boolean includeChildren) {
        Set<Long> selected = new LinkedHashSet<>(ids);
        Set<Long> inSite = new HashSet<>();
        for (List<Long> chunk : chunks(selected)) {
            inSite.addAll(mapper.listIdsInSite(siteId, chunk));
        }
        for (Long id : selected) {
            if (!inSite.contains(id)) {
                throw new Http404Exception(Channel.NOT_FOUND + id);
            }
        }
        if (!includeChildren) {
            return selected;
        }
        Set<Long> targets = new LinkedHashSet<>();
        for (Long id : selected) {
            // 已随所选的上级计入：它的下级也已在其中
            if (targets.add(id)) {
                targets.addAll(mapper.listIdsByAncestorId(id));
            }
        }
        return targets;
    }

    /**
     * 批量设置（docs/spec/channel-management/plan.md D15–D20）：把勾选的属性写到全部目标栏目。同一事务、全有或全无，
     * 全部校验通过后才写入；规则与单个编辑相同。只改写勾选的列与修改时间，其余各列（含自定义字段）不动
     *
     * @param targetIds 目标栏目，见 {@link #listBatchTargetIds}
     * @return 更新的栏目数
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateBatch(Long siteId, Collection<Long> targetIds, BatchSettings settings) {
        Set<String> fields = new LinkedHashSet<>(settings.fields());
        if (fields.isEmpty()) {
            throw new Http400Exception("error.channel.batchUpdate.fieldInvalid", "");
        }
        for (String field : fields) {
            if (!BATCH_UPDATE_FIELDS.contains(field)) {
                throw new Http400Exception("error.channel.batchUpdate.fieldInvalid", field);
            }
        }
        // 承载新值：只有 fields 列出的属性会被 SQL 读取
        Channel values = new Channel();
        Long articleModelId = null;
        if (fields.contains(FIELD_ARTICLE_MODEL_ID)) {
            articleModelId = requireValue(settings.articleModelId(), FIELD_ARTICLE_MODEL_ID);
            requireModel(siteId, articleModelId, Article.MODEL_TYPE);
            values.setArticleModelId(articleModelId);
        }
        if (fields.contains(FIELD_CHANNEL_TEMPLATE)) {
            values.setChannelTemplate(templateValue(settings.channelTemplate(), FIELD_CHANNEL_TEMPLATE));
        }
        if (fields.contains(FIELD_ARTICLE_TEMPLATE)) {
            values.setArticleTemplate(templateValue(settings.articleTemplate(), FIELD_ARTICLE_TEMPLATE));
        }
        if (fields.contains(FIELD_PROCESS_KEY)) {
            String processKey = StringUtils.trimToNull(settings.processKey());
            if (processKey != null && processKey.length() > PROCESS_KEY_MAX_LENGTH) {
                throw new Http400Exception("error.channel.batchUpdate.fieldInvalid", FIELD_PROCESS_KEY);
            }
            validateProcessKey(siteId, processKey);
            values.setProcessKey(processKey);
        }
        if (fields.contains(FIELD_NAV)) {
            values.setNav(requireValue(settings.nav(), FIELD_NAV));
        }
        if (fields.contains(FIELD_REAL)) {
            values.setReal(requireValue(settings.real(), FIELD_REAL));
        }
        if (fields.contains(FIELD_ALLOW_SEARCH)) {
            values.setAllowSearch(requireValue(settings.allowSearch(), FIELD_ALLOW_SEARCH));
        }
        if (fields.contains(FIELD_PAGE_SIZE)) {
            Short pageSize = requireValue(settings.pageSize(), FIELD_PAGE_SIZE);
            if (pageSize < 1 || pageSize > PAGE_SIZE_MAX) {
                throw new Http400Exception("error.channel.pageSizeInvalid", String.valueOf(pageSize));
            }
            values.setPageSize(pageSize);
        }
        List<List<Long>> chunks = chunks(targetIds);
        if (articleModelId != null) {
            // 文章的模型取自所在栏目：栏目已有文章而改换文章模型，这些文章的自定义字段会在下次保存时丢失（同合并，D2）
            for (List<Long> chunk : chunks) {
                List<Long> blocked = mapper.listIdsHoldingArticlesUnderOtherModel(chunk, articleModelId);
                if (!blocked.isEmpty()) {
                    Channel channel = mapper.select(blocked.get(0));
                    throw new Http400Exception("error.channel.batchUpdate.modelHasArticles",
                            channel != null ? channel.getName() : String.valueOf(blocked.get(0)));
                }
            }
        }
        OffsetDateTime now = OffsetDateTime.now();
        int updated = 0;
        for (List<Long> chunk : chunks) {
            updated += mapper.updateSettings(siteId, chunk, fields, values, now);
        }
        return updated;
    }

    private static <T> T requireValue(@Nullable T value, String field) {
        if (value == null) {
            throw new Http400Exception("error.channel.batchUpdate.fieldInvalid", field);
        }
        return value;
    }

    /**
     * 模板名：留空即不指定（主题默认）。规则同 {@link Channel#getChannelTemplate()} 上的校验注解
     */
    @Nullable
    private static String templateValue(@Nullable String template, String field) {
        String value = StringUtils.trimToNull(template);
        if (value != null && (value.length() > TEMPLATE_MAX_LENGTH || value.contains(".."))) {
            throw new Http400Exception("error.channel.batchUpdate.fieldInvalid", field);
        }
        return value;
    }

    /**
     * 按 {@link #BATCH_MAX_SIZE} 分段：IN 列表过长在部分数据库上受限
     */
    private static List<List<Long>> chunks(Collection<Long> ids) {
        List<Long> list = new ArrayList<>(ids);
        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += BATCH_MAX_SIZE) {
            chunks.add(list.subList(i, Math.min(i + BATCH_MAX_SIZE, list.size())));
        }
        return chunks;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ChannelExt ext) {
        extMapper.update(ext);
    }

    /**
     * 仅写入翻译结果（栏目批量新增译文用，docs/spec/ai/article-translate.md §7.1）：以完整行读出后回写
     * {@link Translation} 中的非空字段，不触碰别名/定制字段/权限/附件引用。HTML 正文按文章保存同款
     * {@link PolicyTier#RICH} 策略净化；Markdown 原样写入（其 HTML 由调用方重渲后经 text 传入）。
     * 2026-09-04 由仅名称的 updateNames 扩展
     *
     * @param id          栏目ID（不存在则忽略）
     * @param translation 译文字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateTranslation(Long id, Translation translation) {
        Channel bean = mapper.select(id);
        if (bean == null) {
            return;
        }
        if (StringUtils.isNotBlank(translation.name())) {
            bean.setName(translation.name());
        }
        if (translation.seoTitle() != null) {
            bean.setSeoTitle(translation.seoTitle());
        }
        if (translation.seoKeywords() != null) {
            bean.setSeoKeywords(translation.seoKeywords());
        }
        if (translation.seoDescription() != null) {
            bean.setSeoDescription(translation.seoDescription());
        }
        mapper.update(bean);
        if (translation.text() != null || translation.markdown() != null) {
            ChannelExt ext = bean.getExt();
            if (translation.text() != null) {
                ext.setText(sanitizeService.sanitize(translation.text(), PolicyTier.RICH));
            }
            if (translation.markdown() != null) {
                ext.setMarkdown(translation.markdown());
            }
            extMapper.update(ext);
        }
    }

    /**
     * 栏目翻译写入项（为 {@code null} 的字段不改动）
     *
     * @param name           名称（空白视为不改动）
     * @param seoTitle       SEO 标题
     * @param seoKeywords    SEO 关键词
     * @param seoDescription SEO 描述
     * @param text           HTML 正文
     * @param markdown       Markdown 正文
     */
    public record Translation(@Nullable String name, @Nullable String seoTitle, @Nullable String seoKeywords,
                              @Nullable String seoDescription, @Nullable String text, @Nullable String markdown) {
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateNav(List<Long> ids, Boolean nav) {
        if (ids.isEmpty()) {
            return;
        }
        mapper.updateNav(ids, nav);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateReal(List<Long> ids, Boolean real) {
        if (ids.isEmpty()) {
            return;
        }
        mapper.updateReal(ids, real);
    }

    @Transactional(rollbackFor = Exception.class)
    public void move(Channel from, Channel to, TreeMoveType type, Long siteId) {
        treeService.move(from, to, type, siteId);
    }

    /**
     * 批量移动。同一事务、全有或全无。每移动一个栏目，其余栏目的 order 都已变化，故源、目标均逐个重新读取；
     * “之后”按逆序执行，移动后的先后才与传入顺序一致（“之前”“之内”顺序执行即一致）
     */
    @Transactional(rollbackFor = Exception.class)
    public void moveBatch(List<Long> fromIds, Long toId, TreeMoveType type, Long siteId) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(fromIds));
        if (type == TreeMoveType.AFTER) {
            Collections.reverse(ids);
        }
        for (Long fromId : ids) {
            Channel from = selectInSite(fromId, siteId);
            Channel to = selectInSite(toId, siteId);
            requireNotInSubtree(from, to);
            treeService.move(from, to, type, siteId);
        }
    }

    /**
     * 目标栏目不能是源栏目自身或其后代
     */
    private static void requireNotInSubtree(Channel from, Channel to) {
        for (Channel ancestor : to.getPaths()) {
            if (ancestor.getId().equals(from.getId())) {
                throw new Http400Exception("error.channel.moveToDescendant", from.getName(), to.getName());
            }
        }
    }

    /**
     * 合并栏目（docs/spec/channel-management/plan.md D2）：把源栏目及其全部后代栏目下的文章并入目标栏目，随后自下而上删除
     * 源栏目整棵子树。文章的模型取自所属栏目：文章模型不同且确有文章的栏目并入后，自定义字段会在下次保存时丢失，故拒绝。
     * 同一事务：删除被拒（如采集任务仍指向子树中的栏目）时文章不会留在半途
     *
     * @return 改属目标栏目的文章数
     */
    @Transactional(rollbackFor = Exception.class)
    public int merge(Long fromId, Long toId, Long siteId) {
        siteMapper.lockById(siteId);
        return doMerge(fromId, toId, siteId);
    }

    /**
     * 批量合并。同一事务、全有或全无。只处理最上层的源栏目：选中了上级时，其后代随上级一并合并，不必（也不能）再单独合并
     *
     * @return 改属目标栏目的文章数
     */
    @Transactional(rollbackFor = Exception.class)
    public int mergeBatch(List<Long> fromIds, Long toId, Long siteId) {
        // 锁序（docs/spec/article-data-permission/plan.md D22）：站点行在先，文章行在后
        siteMapper.lockById(siteId);
        Set<Long> ids = new LinkedHashSet<>(fromIds);
        Set<Long> nested = new HashSet<>();
        for (Long id : ids) {
            for (Long descendantId : mapper.listIdsByAncestorId(id)) {
                if (!descendantId.equals(id)) {
                    nested.add(descendantId);
                }
            }
        }
        int moved = 0;
        for (Long id : ids) {
            if (!nested.contains(id)) {
                moved += doMerge(id, toId, siteId);
            }
        }
        return moved;
    }

    private int doMerge(Long fromId, Long toId, Long siteId) {
        Channel from = selectInSite(fromId, siteId);
        Channel to = selectInSite(toId, siteId);
        requireNotInSubtree(from, to);
        for (Channel channel : selectList(ChannelArgs.of().ancestorId(fromId))) {
            if (!channel.getArticleModelId().equals(to.getArticleModelId())
                    && articleMapper.existsByChannelId(channel.getId()) > 0) {
                throw new Http400Exception("error.channel.merge.modelMismatch", channel.getName(), to.getName());
            }
        }
        requireNoPlacementCollision(fromId, toId);
        int moved = articleMapper.updateChannelId(fromId, toId);
        deleteTree(fromId);
        return moved;
    }

    /**
     * 合并后一篇文章不得在目标栏目里出现两次（docs/spec/article-data-permission/plan.md D21，D14 的规则）：源栏目子树与
     * 目标栏目里的全部落位中，同一落位家族（源文章及其推送、扩展栏目的子行）不得有两处。锁定读取这些行，再锁住家族里
     * 在别处的源文章（推送加的那把锁，D17）：并发的推送要等合并提交后才能判定，判定的是合并后的状态
     */
    private void requireNoPlacementCollision(Long fromId, Long toId) {
        List<Long> channelIds = new ArrayList<>(mapper.listIdsByAncestorId(fromId));
        channelIds.add(toId);
        List<ArticlePlacement> placements = articleMapper.lockPlacementsInChannels(channelIds);
        Set<Long> placedIds = placements.stream().map(ArticlePlacement::id).collect(Collectors.toSet());
        List<Long> sourcesElsewhere = placements.stream().map(ArticlePlacement::srcId).filter(Objects::nonNull)
                .filter(srcId -> !placedIds.contains(srcId)).distinct().sorted().toList();
        if (!sourcesElsewhere.isEmpty()) {
            articleMapper.lockByIds(sourcesElsewhere);
        }
        Set<Long> families = new HashSet<>();
        Set<Long> collided = new LinkedHashSet<>();
        for (ArticlePlacement placement : placements) {
            if (!families.add(placement.familyId())) {
                collided.add(placement.familyId());
            }
        }
        if (collided.isEmpty()) {
            return;
        }
        Long familyId = collided.iterator().next();
        String title = Optional.ofNullable(articleMapper.select(familyId)).map(Article::getTitle).orElse("");
        throw new Http400Exception("error.channel.merge.placementCollision", title, String.valueOf(collided.size()));
    }

    /**
     * 自下而上删除整棵子树（按 parent_id 递归，不依赖闭包表）。每个栏目删除前重新读取整行：
     * 它的 order 已因之前的删除而变化，清理静态页也需要 ext
     */
    private int deleteTree(Long id) {
        int deleted = 0;
        for (Channel child : mapper.listChildren(id)) {
            deleted += deleteTree(child.getId());
        }
        return deleted + delete(id);
    }

    /**
     * 整理树形结构：按 parent_id 重建 order、depth 与闭包表，兄弟间保持原有先后。同一事务：闭包表先删后建的中间状态对外不可见
     */
    @Transactional(rollbackFor = Exception.class)
    public void tidyTree(Long siteId) {
        List<Channel> list = mapper.listBySiteIdForTidy(siteId);
        List<TreeSortEntity> tree = treeService.toTree(list);
        treeService.tidyTreeOrderAndDepth(tree, list.size());
        treeMapper.deleteBySiteId(siteId);
        treeService.tidyTreeRelation(tree, list.size());
    }

    /**
     * 栏目及其全部后代栏目的ID（含自身，按闭包表）
     */
    public List<Long> listSubtreeIds(Long id) {
        return mapper.listIdsByAncestorId(id);
    }

    /**
     * 栏目及其后代栏目下是否还有文章
     */
    public boolean existsArticleInSubtree(Long id) {
        return articleMapper.existsByChannelAncestorId(id) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        Channel bean = mapper.select(id);
        if (bean == null) {
            return 0;
        }
        return delete(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Channel bean) {
        deleteListeners.forEach(it -> it.preChannelDelete(bean.getId()));
        attachmentService.deleteRefer(GeneratedChannel.TABLE_NAME, bean.getId());
        groupAccessMapper.deleteByChannelId(bean.getId());
        groupContributeMapper.deleteByChannelId(bean.getId());
        roleArticleMapper.deleteByChannelId(bean.getId());
        roleChannelMapper.deleteByChannelId(bean.getId());
        orgArticleMapper.deleteByChannelId(bean.getId());
        orgChannelMapper.deleteByChannelId(bean.getId());
        extMapper.delete(bean.getId());
        int count = treeService.delete(bean.getId(), bean.getOrder(), bean.getSiteId());
        htmlService.deleteChannelHtml(bean);
        contentStatEvictor.channel(bean.getSiteId());
        return count;
    }

    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Channel select(Long id) {
        return mapper.select(id);
    }

    @Nullable
    public Article findFirstArticle(Long channelId) {
        List<Article> list = PageMethod.offsetPage(0, 1, false).doSelectPage(() ->
                articleMapper.listByChannelId(channelId));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Nullable
    public Channel findFirstChild(Long channelId) {
        List<Channel> list = PageMethod.offsetPage(0, 1, false).doSelectPage(() ->
                mapper.listChildren(channelId));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Nullable
    public Channel findBySiteIdAndSlug(Long siteId, String slug) {
        List<Channel> list = PageMethod.offsetPage(0, 1, false).doSelectPage(() ->
                mapper.findBySiteIdAndSlug(siteId, slug));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public List<Channel> listBySiteId(Long siteId) {
        return selectList(ChannelArgs.of().siteId(siteId).parentIdIsNull());
    }

    public List<Channel> listBySiteIdAndSlug(@Nullable Long siteId, Collection<String> slugs,
            boolean isIncludeSubSite) {
        ChannelArgs args = ChannelArgs.of().inSlugs(slugs);
        if (isIncludeSubSite) {
            args.siteAncestorId(siteId);
        } else {
            args.siteId(siteId);
        }
        return selectList(args);
    }

    public List<Channel> listByChannelForSitemap(Long siteId) {
        List<Channel> list = mapper.listByChannelForSitemap(siteId);
        populateResolvedUrls(list);
        return list;
    }

    public List<Channel> listChildren(Long parentId) {
        List<Channel> list = mapper.listChildren(parentId);
        populateResolvedUrls(list);
        return list;
    }

    public List<Long> listChannelPermissions(Collection<Long> roleIds, Collection<Long> orgIds,
                                             @Nullable Long siteId) {
        return mapper.listChannelPermissions(roleIds, orgIds, siteId);
    }
    public List<Long> listArticlePermissions(Collection<Long> roleIds, Collection<Long> orgIds,
                                             @Nullable Long siteId) {
        return mapper.listArticlePermissions(roleIds, orgIds, siteId);
    }

    public List<Channel> selectList(ChannelArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedChannel.TABLE_NAME, "order,id");
        List<Channel> list = mapper.selectAll(queryInfo, args.isQueryHasChildren(), args.getArticleRoleIds(),
                args.getArticleOrgIds());
        populateResolvedUrls(list);
        return list;
    }

    public List<Channel> selectList(ChannelArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    /**
     * For each TYPE_LINK_CHILD channel in {@code list}, walk the chain via
     * {@link #findFirstChild(Long)} until a non-LINK_CHILD terminal channel is
     * found, then snapshot the five zero-arg URL getters off the terminal into
     * a {@link Channel.ResolvedUrls} record stored on the LINK_CHILD channel.
     * Jackson's zero-arg URL getters return those precomputed strings instead
     * of recursing through {@code getFirstChannel} during serialization.
     * <p>
     * Walks an explicit chain rather than calling the LINK_CHILD's own
     * zero-arg getter so intermediate channels (which may also be LINK_CHILD)
     * don't trigger their own lookup. If the chain has no terminal (no first
     * child anywhere) all five strings are {@code ""}, matching the original
     * {@code .orElse("")} contract.
     */
    private void populateResolvedUrls(List<Channel> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Channel channel : list) {
            if (channel.getType() != Channel.TYPE_LINK_CHILD) {
                continue;
            }
            Channel terminal = walkToTerminal(channel);
            if (terminal == null) {
                channel.setResolvedUrls(new Channel.ResolvedUrls("", "", "", "", ""));
            } else {
                channel.setResolvedUrls(new Channel.ResolvedUrls(
                        terminal.getUrl(),
                        terminal.getDynamicUrl(),
                        terminal.getStaticUrl(),
                        terminal.getFullUrl(),
                        terminal.getDynamicFullUrl()));
            }
        }
    }

    @Nullable
    private Channel walkToTerminal(Channel start) {
        Channel current = start;
        // Guard against accidental cycles (LINK_CHILD pointing at an ancestor).
        Set<Long> visited = new HashSet<>();
        while (current != null && current.getType() == Channel.TYPE_LINK_CHILD) {
            if (!visited.add(current.getId())) {
                return null;
            }
            current = findFirstChild(current.getId());
        }
        return current;
    }

    public boolean existsByModelId(Long modelId) {
        return mapper.existsByModelId(modelId) > 0;
    }

    public boolean existsBySlug(String slug, Long siteId) {
        return mapper.existsBySlug(slug, siteId) > 0;
    }

    public boolean existsByArticleRoleId(Long channelId, Collection<Long> roleIds) {
        return mapper.existsByArticleRoleId(channelId, roleIds) > 0;
    }

    /**
     * 统计栏目数量
     *
     * @param siteId  站点ID
     * @param created 创建日期
     * @return 栏目数量
     */
    public int countByCreated(Long siteId, @Nullable OffsetDateTime created) {
        return mapper.countByCreated(siteId, created);
    }

    @Override
    public void preModelDelete(Long modelId) {
        if (existsByModelId(modelId)) {
            throw new LogicException("error.refer.channel");
        }
    }

    @Override
    public void preSiteDelete(Long siteId) {
        extMapper.deleteBySiteId(siteId);
        treeMapper.deleteBySiteId(siteId);
        groupAccessMapper.deleteBySiteId(siteId);
        groupContributeMapper.deleteBySiteId(siteId);
        orgArticleMapper.deleteBySiteId(siteId);
        orgChannelMapper.deleteBySiteId(siteId);
        mapper.updateParentIdToNull(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }

    /**
     * 名称长度上限（同 {@code GeneratedChannel.name} 的 {@code @Length}）
     */
    public static final int NAME_MAX_LENGTH = 50;
    /**
     * 批量新增单次上限：操作日志记录整个请求体，且一棵树无法拆成多次提交
     */
    public static final int BATCH_MAX_SIZE = 500;
    /**
     * 分页条数上限
     */
    public static final int PAGE_SIZE_MAX = 200;
    /**
     * 模板名、流程标识的长度上限（同 {@code GeneratedChannel} 上的 {@code @Length}）
     */
    private static final int TEMPLATE_MAX_LENGTH = 255;
    private static final int PROCESS_KEY_MAX_LENGTH = 50;

    private static final String FIELD_ARTICLE_MODEL_ID = "articleModelId";
    private static final String FIELD_CHANNEL_TEMPLATE = "channelTemplate";
    private static final String FIELD_ARTICLE_TEMPLATE = "articleTemplate";
    private static final String FIELD_PROCESS_KEY = "processKey";
    private static final String FIELD_NAV = "nav";
    private static final String FIELD_REAL = "real";
    private static final String FIELD_ALLOW_SEARCH = "allowSearch";
    private static final String FIELD_PAGE_SIZE = "pageSize";
    /**
     * 可批量设置的属性
     */
    public static final Set<String> BATCH_UPDATE_FIELDS = Set.of(FIELD_ARTICLE_MODEL_ID, FIELD_CHANNEL_TEMPLATE,
            FIELD_ARTICLE_TEMPLATE, FIELD_PROCESS_KEY, FIELD_NAV, FIELD_REAL, FIELD_ALLOW_SEARCH, FIELD_PAGE_SIZE);

    private List<ChannelDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<ChannelDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }
}