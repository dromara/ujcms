package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.domain.base.ChannelBase;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.listener.ChannelDeleteListener;
import com.ujcms.cms.core.listener.ModelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.db.tree.TreeMoveType;
import com.ujcms.commons.db.tree.TreeService;
import com.ujcms.commons.db.tree.TreeSortEntity;
import com.ujcms.commons.query.CustomFieldQuery;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import com.ujcms.commons.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import org.owasp.html.PolicyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * 栏目 Service
 *
 * @author PONY
 */
@Service
public class ChannelService implements ModelDeleteListener, SiteDeleteListener {
    private final HtmlService htmlService;
    private final PolicyFactory policyFactory;
    private final AttachmentService attachmentService;
    private final ModelService modelService;
    private final ChannelMapper mapper;
    private final ChannelExtMapper extMapper;
    private final ChannelTreeMapper treeMapper;
    private final ArticleMapper articleMapper;
    private final GroupAccessMapper groupAccessMapper;
    private final RoleArticleMapper roleArticleMapper;
    private final RoleChannelMapper roleChannelMapper;
    private final OrgArticleMapper orgArticleMapper;
    private final OrgChannelMapper orgChannelMapper;
    private final SnowflakeSequence snowflakeSequence;
    private final TreeService<Channel> treeService;

    public ChannelService(
            HtmlService htmlService, PolicyFactory policyFactory, AttachmentService attachmentService,
            ModelService modelService, ChannelMapper mapper, ChannelExtMapper extMapper, ChannelTreeMapper treeMapper,
            ArticleMapper articleMapper, GroupAccessMapper groupAccessMapper, RoleArticleMapper roleArticleMapper,
            RoleChannelMapper roleChannelMapper, OrgArticleMapper orgArticleMapper, OrgChannelMapper orgChannelMapper,
            SnowflakeSequence snowflakeSequence) {
        this.htmlService = htmlService;
        this.policyFactory = policyFactory;
        this.attachmentService = attachmentService;
        this.modelService = modelService;
        this.articleMapper = articleMapper;
        this.groupAccessMapper = groupAccessMapper;
        this.mapper = mapper;
        this.extMapper = extMapper;
        this.treeMapper = treeMapper;
        this.roleArticleMapper = roleArticleMapper;
        this.roleChannelMapper = roleChannelMapper;
        this.orgArticleMapper = orgArticleMapper;
        this.orgChannelMapper = orgChannelMapper;
        this.snowflakeSequence = snowflakeSequence;
        this.treeService = new TreeService<>(mapper, treeMapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Channel bean, List<Long> groupIds, List<Long> articleRoleIds,
                       List<Long> channelRoleIds) {
        bean.setId(snowflakeSequence.nextId());
        if (StringUtils.isBlank(bean.getAlias())) {
            bean.setAlias(String.valueOf(bean.getId()));
        }
        Model model = Optional.ofNullable(modelService.select(bean.getChannelModelId())).orElseThrow(() ->
                new IllegalArgumentException(Model.NOT_FOUND + bean.getChannelModelId()));
        bean.disassembleCustoms(model, policyFactory);
        treeService.insert(bean, bean.getSiteId());
        ChannelExt ext = bean.getExt();
        ext.setId(bean.getId());
        extMapper.insert(ext);
        insertGroupIds(groupIds, bean.getId(), bean.getSiteId());
        insertArticleRoleIds(articleRoleIds, bean.getId(), bean.getSiteId());
        insertChannelRoleIds(channelRoleIds, bean.getId(), bean.getSiteId());
        attachmentService.insertRefer(ChannelBase.TABLE_NAME, bean.getId(), bean.getAttachmentUrls(model));
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

    @Transactional(rollbackFor = Exception.class)
    public void update(Channel bean, @Nullable List<Long> groupIds,
                       @Nullable List<Long> articleRoleIds, @Nullable List<Long> channelRoleIds) {
        Model model = Optional.ofNullable(modelService.select(bean.getChannelModelId())).orElseThrow(() ->
                new IllegalArgumentException(Model.NOT_FOUND + bean.getChannelModelId()));
        bean.disassembleCustoms(model, policyFactory);
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
            insertArticleRoleIds(channelRoleIds, bean.getId(), bean.getSiteId());
        }
        attachmentService.updateRefer(ChannelBase.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ChannelExt ext) {
        extMapper.update(ext);
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

    @Transactional(rollbackFor = Exception.class)
    public void merge(Channel from,Channel to) {
        articleMapper.updateChannelId(from.getId(), to.getId());
        delete(from);
    }

    public List<Channel> listBySiteIdForTidy(Long siteId) {
        return mapper.listBySiteIdForTidy(siteId);
    }

    public List<TreeSortEntity> toTree(List<Channel> list) {
        return treeService.toTree(list);
    }

    @Transactional(rollbackFor = Exception.class)
    public void tidyTreeOrderAndDepth(List<TreeSortEntity> tree, int size) {
        treeService.tidyTreeOrderAndDepth(tree, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRelationBySiteId(Long siteId) {
        treeMapper.deleteBySiteId(siteId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void tidyTreeRelation(List<TreeSortEntity> tree, int size) {
        treeService.tidyTreeRelation(tree, size);
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
        attachmentService.deleteRefer(ChannelBase.TABLE_NAME, bean.getId());
        groupAccessMapper.deleteByChannelId(bean.getId());
        roleArticleMapper.deleteByChannelId(bean.getId());
        roleChannelMapper.deleteByChannelId(bean.getId());
        orgArticleMapper.deleteByChannelId(bean.getId());
        orgChannelMapper.deleteByChannelId(bean.getId());
        extMapper.delete(bean.getId());
        int count = treeService.delete(bean.getId(), bean.getOrder(), bean.getSiteId());
        htmlService.deleteChannelHtml(bean);
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
    public Channel findBySiteIdAndAlias(Long siteId, String alias) {
        List<Channel> list = PageMethod.offsetPage(0, 1, false).doSelectPage(() ->
                mapper.findBySiteIdAndAlias(siteId, alias));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public List<Channel> listBySiteId(Long siteId) {
        return selectList(ChannelArgs.of().siteId(siteId).parentIdIsNull());
    }

    public List<Channel> listBySiteIdAndAlias(@Nullable Long siteId, Collection<String> aliases,
                                              boolean isIncludeSubSite) {
        ChannelArgs args = ChannelArgs.of().inAliases(aliases);
        if (isIncludeSubSite) {
            args.siteAncestorId(siteId);
        } else {
            args.siteId(siteId);
        }
        return selectList(args);
    }

    public List<Channel> listByChannelForSitemap(Long siteId) {
        return mapper.listByChannelForSitemap(siteId);
    }

    public List<Channel> listChildren(Long parentId) {
        return mapper.listChildren(parentId);
    }

    public List<Long> listChannelPermissions(Collection<Long> roleIds, Collection<Long> orgIds,
                                             @Nullable Long siteId) {
        return mapper.listChannelPermissions(roleIds, orgIds, siteId);
    }

    public List<Channel> selectList(ChannelArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), ChannelBase.TABLE_NAME, "order,id");
        List<QueryInfo.WhereCondition> customsCondition = CustomFieldQuery.parse(args.getCustomsQueryMap());
        return mapper.selectAll(queryInfo, customsCondition, args.isQueryHasChildren(),
                args.getArticleRoleIds(), args.getArticleOrgIds());
    }

    public List<Channel> selectList(ChannelArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public boolean existsByModelId(Long modelId) {
        return mapper.existsByModelId(modelId) > 0;
    }

    public boolean existsByAlias(String alias, Long siteId) {
        return mapper.existsByAlias(alias, siteId) > 0;
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
        orgArticleMapper.deleteBySiteId(siteId);
        orgChannelMapper.deleteBySiteId(siteId);
        mapper.updateParentIdToNull(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }

    private List<ChannelDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<ChannelDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }
}