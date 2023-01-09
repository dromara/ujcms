package com.ujcms.cms.core.service;

import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.ChannelBuffer;
import com.ujcms.cms.core.domain.ChannelCustom;
import com.ujcms.cms.core.domain.ChannelExt;
import com.ujcms.cms.core.domain.ChannelTree;
import com.ujcms.cms.core.domain.GroupAccess;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.RoleArticle;
import com.ujcms.cms.core.domain.RoleChannel;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.listener.ChannelDeleteListener;
import com.ujcms.cms.core.listener.ModelDeleteListener;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ChannelBufferMapper;
import com.ujcms.cms.core.mapper.ChannelCustomMapper;
import com.ujcms.cms.core.mapper.ChannelExtMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.ChannelTreeMapper;
import com.ujcms.cms.core.mapper.GroupAccessMapper;
import com.ujcms.cms.core.mapper.RoleArticleMapper;
import com.ujcms.cms.core.mapper.RoleChannelMapper;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.util.db.tree.TreeService;
import com.ujcms.util.query.CustomFieldQuery;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
import com.ujcms.util.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import org.owasp.html.PolicyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    private final ChannelBufferMapper bufferMapper;
    private final ChannelTreeMapper treeMapper;
    private final ArticleMapper articleMapper;
    private final GroupAccessMapper groupAccessMapper;
    private final RoleArticleMapper roleArticleMapper;
    private final RoleChannelMapper roleChannelMapper;
    private final ChannelCustomMapper customMapper;
    private final SeqService seqService;
    private final TreeService<Channel, ChannelTree> treeService;

    public ChannelService(HtmlService htmlService, PolicyFactory policyFactory, AttachmentService attachmentService,
                          ModelService modelService, ChannelMapper mapper, ChannelExtMapper extMapper,
                          ChannelBufferMapper bufferMapper, ChannelTreeMapper treeMapper,
                          ArticleMapper articleMapper, GroupAccessMapper groupAccessMapper,
                          RoleArticleMapper roleArticleMapper, RoleChannelMapper roleChannelMapper,
                          ChannelCustomMapper customMapper,
                          SeqService seqService) {
        this.htmlService = htmlService;
        this.policyFactory = policyFactory;
        this.attachmentService = attachmentService;
        this.modelService = modelService;
        this.bufferMapper = bufferMapper;
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
        bean.setId(seqService.getNextVal(Channel.TABLE_NAME));
        if (StringUtils.isBlank(bean.getAlias())) {
            bean.setAlias(String.valueOf(bean.getId()));
        }
        treeService.insert(bean, bean.getSiteId());
        ext.setId(bean.getId());
        extMapper.insert(ext);
        bufferMapper.insert(new ChannelBuffer(bean.getId()));
        insertGroupIds(groupIds, bean.getId(), bean.getSiteId());
        insertArticleRoleIds(articleRoleIds, bean.getId(), bean.getSiteId());
        insertChannelRoleIds(channelRoleIds, bean.getId(), bean.getSiteId());
        Model model = Optional.ofNullable(modelService.select(bean.getChannelModelId()))
                .orElseThrow(() -> new IllegalArgumentException("bean.channelModelId cannot be null"));
        List<ChannelCustom> customList = Channel.disassembleCustoms(model, bean.getId(), customs);
        insertCustoms(customList, bean.getId());
        attachmentService.insertRefer(Channel.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
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
            it.setId(seqService.getNextValLong(ChannelCustom.TABLE_NAME));
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
        attachmentService.updateRefer(Channel.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
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
        int count = bean.getChildren().stream().mapToInt(item -> delete(item.getId())).sum();
        deleteListeners.forEach(it -> it.preChannelDelete(bean.getId()));
        attachmentService.deleteRefer(Channel.TABLE_NAME, bean.getId());
        groupAccessMapper.deleteByChannelId(bean.getId());
        roleArticleMapper.deleteByChannelId(bean.getId());
        roleChannelMapper.deleteByChannelId(bean.getId());
        customMapper.deleteByChannelId(bean.getId());
        bufferMapper.delete(bean.getId());
        extMapper.delete(bean.getId());
        count += treeService.delete(bean.getId(), bean.getOrder(), bean.getSiteId());
        htmlService.deleteChannelHtml(bean);
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Channel select(Integer id) {
        return mapper.select(id);
    }

    public void fetchFirstData(Channel channel) {
        if (channel.getType() == Channel.TYPE_LINK_ARTICLE
                && !channel.isFetchedFirstData() && channel.getFirstArticle() == null) {
            channel.setFirstArticle(findFirstArticle(channel.getId()));
            channel.setFetchedFirstData(true);
        }
        if (channel.getType() == Channel.TYPE_LINK_CHILD
                && !channel.isFetchedFirstData() && channel.getFirstChild() == null) {
            channel.setFirstChild(findFirstChild(channel.getId()));
            channel.setFetchedFirstData(true);
        }
    }

    @Nullable
    public Article findFirstArticle(Integer channelId) {
        List<Article> list = PageHelper.offsetPage(0, 1, false).doSelectPage(() ->
                articleMapper.listByChannelId(channelId));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Nullable
    public Channel findFirstChild(Integer channelId) {
        List<Channel> list = PageHelper.offsetPage(0, 1, false).doSelectPage(() ->
                mapper.listChildren(channelId));
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Nullable
    public Channel findBySiteIdAndAlias(Integer siteId, String alias) {
        List<Channel> list = listBySiteIdAndAlias(siteId, Collections.singletonList(alias), false);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public List<Channel> listBySiteIdAndAlias(@Nullable Integer siteId, Collection<String> aliases,
                                              boolean isIncludeSubSite) {
        ChannelArgs args = ChannelArgs.of().inAliases(aliases);
        if (isIncludeSubSite) {
            args.subSiteId(siteId);
        } else {
            args.siteId(siteId);
        }
        return selectList(args);
    }

    public List<Channel> listByChannelForSitemap(Integer siteId) {
        return mapper.listByChannelForSitemap(siteId);
    }

    public List<Channel> selectList(ChannelArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Channel.TABLE_NAME, "order,id");
        List<QueryInfo.WhereCondition> customsCondition = CustomFieldQuery.parse(args.getCustomsQueryMap());
        List<Channel> list = mapper.selectAll(queryInfo, customsCondition);
        list.forEach(this::fetchFirstData);
        return list;
    }

    public List<Channel> selectList(ChannelArgs args, int offset, int limit) {
        List<Channel> list = PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
        list.forEach(this::fetchFirstData);
        return list;
    }

    public boolean existsByModelId(Integer modelId) {
        return PageHelper.offsetPage(0, 1, false).<Number>doSelectPage(() ->
                mapper.countByModelId(modelId)).iterator().next().intValue() > 0;
    }

    @Override
    public void preModelDelete(Integer modelId) {
        if (existsByModelId(modelId)) {
            throw new LogicException("error.refer.channel");
        }
    }

    @Override
    public void preSiteDelete(Integer siteId) {
        bufferMapper.deleteBySiteId(siteId);
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