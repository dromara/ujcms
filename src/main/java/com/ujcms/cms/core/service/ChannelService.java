package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.domain.base.ChannelBase;
import com.ujcms.cms.core.domain.base.ChannelCustomBase;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.listener.ChannelDeleteListener;
import com.ujcms.cms.core.listener.ModelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.commons.db.tree.TreeService;
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
    private final ChannelCustomMapper customMapper;
    private final SeqService seqService;
    private final TreeService<Channel, ChannelTree> treeService;

    public ChannelService(
            HtmlService htmlService, PolicyFactory policyFactory, AttachmentService attachmentService,
            ModelService modelService, ChannelMapper mapper, ChannelExtMapper extMapper, ChannelTreeMapper treeMapper,
            ArticleMapper articleMapper, GroupAccessMapper groupAccessMapper, RoleArticleMapper roleArticleMapper,
            RoleChannelMapper roleChannelMapper, ChannelCustomMapper customMapper, SeqService seqService) {
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
        this.customMapper = customMapper;
        this.seqService = seqService;
        this.treeService = new TreeService<>(mapper, treeMapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Channel bean, ChannelExt ext, List<Integer> groupIds, List<Integer> articleRoleIds,
                       List<Integer> channelRoleIds, Map<String, Object> customs) {
        bean.setId(seqService.getNextVal(ChannelBase.TABLE_NAME));
        if (StringUtils.isBlank(bean.getAlias())) {
            bean.setAlias(String.valueOf(bean.getId()));
        }
        treeService.insert(bean, bean.getSiteId());
        ext.setId(bean.getId());
        extMapper.insert(ext);
        insertGroupIds(groupIds, bean.getId(), bean.getSiteId());
        insertArticleRoleIds(articleRoleIds, bean.getId(), bean.getSiteId());
        insertChannelRoleIds(channelRoleIds, bean.getId(), bean.getSiteId());
        Model model = Optional.ofNullable(modelService.select(bean.getChannelModelId()))
                .orElseThrow(() -> new IllegalArgumentException("bean.channelModelId cannot be null"));
        List<ChannelCustom> customList = Channel.disassembleCustoms(model, bean.getId(), customs);
        insertCustoms(customList, bean.getId());
        attachmentService.insertRefer(ChannelBase.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    private void insertGroupIds(List<Integer> groupIds, Integer channelId, Integer siteId) {
        groupIds.forEach(groupId -> groupAccessMapper.insert(new GroupAccess(groupId, channelId, siteId)));
    }

    private void insertArticleRoleIds(List<Integer> articleRoleIds, Integer channelId, Integer siteId) {
        articleRoleIds.forEach(roleId -> roleArticleMapper.insert(new RoleArticle(roleId, channelId, siteId)));
    }

    private void insertChannelRoleIds(List<Integer> channelRoleIds, Integer channelId, Integer siteId) {
        channelRoleIds.forEach(roleId -> roleChannelMapper.insert(new RoleChannel(roleId, channelId, siteId)));
    }

    private void insertCustoms(List<ChannelCustom> customList, Integer channelId) {
        customList.forEach(it -> {
            it.setId(seqService.getNextLongVal(ChannelCustomBase.TABLE_NAME));
            it.setChannelId(channelId);
            if (it.isRichEditor()) {
                it.setValue(policyFactory.sanitize(it.getValue()));
            }
            customMapper.insert(it);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Channel bean, ChannelExt ext, @Nullable Integer parentId, @Nullable List<Integer> groupIds,
                       @Nullable List<Integer> articleRoleIds, @Nullable List<Integer> channelRoleIds,
                       Map<String, Object> customs) {
        treeService.update(bean, parentId, bean.getSiteId());
        extMapper.update(ext);
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
        Model model = Optional.ofNullable(modelService.select(bean.getChannelModelId()))
                .orElseThrow(() -> new IllegalArgumentException("bean.channelModelId cannot be null"));
        List<ChannelCustom> customList = Channel.disassembleCustoms(model, bean.getId(), customs);
        // 要先将修改后的数据放入bean中，否则bean.getAttachmentUrls()会获取修改前的值
        bean.setCustomList(customList);

        customMapper.deleteByChannelId(bean.getId());
        insertCustoms(customList, bean.getId());
        attachmentService.updateRefer(ChannelBase.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ChannelExt ext) {
        extMapper.update(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Channel> list) {
        treeService.updateOrder(list);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
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
        customMapper.deleteByChannelId(bean.getId());
        extMapper.delete(bean.getId());
        int count = treeService.delete(bean.getId(), bean.getOrder(), bean.getSiteId());
        htmlService.deleteChannelHtml(bean);
        return count;
    }

    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    public Map<Integer, Integer> copyBySite(Integer siteId, Integer fromSiteId, Map<Integer, Integer> modelPairMap) {
        Map<Integer, Integer> channelPairMap = new HashMap<>(32);
        for (Channel channel : listBySiteId(fromSiteId)) {
            copy(channel, null, siteId, modelPairMap, channelPairMap);
        }
        return channelPairMap;
    }

    private void copy(Channel channel, @Nullable Integer parentId, Integer siteId, Map<Integer, Integer> modelPairMap,
                      Map<Integer, Integer> channelPairMap) {
        channel.setSiteId(siteId);
        channel.setParentId(parentId);
        Optional.ofNullable(modelPairMap.get(channel.getChannelModelId())).ifPresent(channel::setChannelModelId);
        Optional.ofNullable(modelPairMap.get(channel.getArticleModelId())).ifPresent(channel::setArticleModelId);

        channel.setStaticFile(null);
        channel.setMobileStaticFile(null);
        channel.setViews(0L);
        channel.setSelfViews(0L);
        Integer origId = channel.getId();
        List<Integer> groupIds = groupAccessMapper.listGroupByChannelId(channel.getId(), null);
        List<Integer> articleRoleIds = roleArticleMapper.listRoleByChannelId(channel.getId(), null);
        List<Integer> channelRoleIds = roleChannelMapper.listRoleByChannelId(channel.getId(), null);

        insert(channel, channel.getExt(), groupIds, articleRoleIds, channelRoleIds, channel.getCustoms());
        channelPairMap.put(origId, channel.getId());
        for (Channel child : listChildren(origId)) {
            copy(child, channel.getId(), siteId, modelPairMap, channelPairMap);
        }

    }

    @Nullable
    public Channel select(Integer id) {
        return mapper.select(id);
    }

    @Nullable
    public Article findFirstArticle(Integer channelId) {
        List<Article> list = PageMethod.offsetPage(0, 1, false).doSelectPage(() ->
                articleMapper.listByChannelId(channelId));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Nullable
    public Channel findFirstChild(Integer channelId) {
        List<Channel> list = PageMethod.offsetPage(0, 1, false).doSelectPage(() ->
                mapper.listChildren(channelId));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Nullable
    public Channel findBySiteIdAndAlias(Integer siteId, String alias) {
        List<Channel> list = PageMethod.offsetPage(0, 1, false).doSelectPage(() ->
                mapper.findBySiteIdAndAlias(siteId, alias));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public List<Channel> listBySiteId(Integer siteId) {
        return selectList(ChannelArgs.of().siteId(siteId).parentIdIsNull());
    }

    public List<Channel> listBySiteIdAndAlias(@Nullable Integer siteId, Collection<String> aliases,
                                              boolean isIncludeSubSite) {
        ChannelArgs args = ChannelArgs.of().inAliases(aliases);
        if (isIncludeSubSite) {
            args.siteAncestorId(siteId);
        } else {
            args.siteId(siteId);
        }
        return selectList(args);
    }

    public List<Channel> listByChannelForSitemap(Integer siteId) {
        return mapper.listByChannelForSitemap(siteId);
    }

    public List<Channel> listChildren(Integer parentId) {
        return mapper.listChildren(parentId);
    }

    public List<Channel> selectList(ChannelArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), ChannelBase.TABLE_NAME, "order,id");
        List<QueryInfo.WhereCondition> customsCondition = CustomFieldQuery.parse(args.getCustomsQueryMap());
        return mapper.selectAll(queryInfo, customsCondition, args.isQueryHasChildren(), args.isOnlyParent(),
                args.getArticleRoleIds());
    }

    public List<Channel> selectList(ChannelArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public boolean existsByModelId(Integer modelId) {
        return mapper.existsByModelId(modelId) > 0;
    }

    public boolean existsByAlias(String alias, Integer siteId) {
        return mapper.existsByAlias(alias, siteId) > 0;
    }

    public boolean existsByArticleRoleId(Integer channelId, Collection<Integer> roleIds) {
        return mapper.existsByArticleRoleId(channelId, roleIds) > 0;
    }

    /**
     * 统计栏目数量
     *
     * @param siteId  站点ID
     * @param created 创建日期
     * @return 栏目数量
     */
    public int countByCreated(Integer siteId, @Nullable OffsetDateTime created) {
        return mapper.countByCreated(siteId, created);
    }

    @Override
    public void preModelDelete(Integer modelId) {
        if (existsByModelId(modelId)) {
            throw new LogicException("error.refer.channel");
        }
    }

    @Override
    public void preSiteDelete(Integer siteId) {
        customMapper.deleteBySiteId(siteId);
        extMapper.deleteBySiteId(siteId);
        treeMapper.deleteBySiteId(siteId);
        groupAccessMapper.deleteBySiteId(siteId);
        roleArticleMapper.deleteBySiteId(siteId);
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