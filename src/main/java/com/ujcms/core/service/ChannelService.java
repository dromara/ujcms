package com.ujcms.core.service;

import com.github.pagehelper.PageHelper;
import com.ofwise.util.query.CustomFieldQuery;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import com.ujcms.core.domain.Channel;
import com.ujcms.core.domain.ChannelBuffer;
import com.ujcms.core.domain.ChannelCustom;
import com.ujcms.core.domain.ChannelExt;
import com.ujcms.core.domain.ChannelGroup;
import com.ujcms.core.domain.ChannelTree;
import com.ujcms.core.generator.HtmlService;
import com.ujcms.core.listener.ChannelDeleteListener;
import com.ujcms.core.mapper.ChannelBufferMapper;
import com.ujcms.core.mapper.ChannelCustomMapper;
import com.ujcms.core.mapper.ChannelExtMapper;
import com.ujcms.core.mapper.ChannelGroupMapper;
import com.ujcms.core.mapper.ChannelMapper;
import com.ujcms.core.mapper.ChannelTreeMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 栏目 Service
 *
 * @author PONY
 */
@Service
public class ChannelService {
    private HtmlService htmlService;
    private AttachmentService attachmentService;
    private ChannelMapper mapper;
    private ChannelExtMapper extMapper;
    private ChannelBufferMapper bufferMapper;
    private ChannelTreeMapper treeMapper;
    private ChannelGroupMapper channelGroupMapper;
    private ChannelCustomMapper customMapper;
    private SeqService seqService;

    public ChannelService(HtmlService htmlService, AttachmentService attachmentService, ChannelMapper mapper,
                          ChannelExtMapper extMapper, ChannelBufferMapper bufferMapper, ChannelTreeMapper treeMapper,
                          ChannelGroupMapper channelGroupMapper, ChannelCustomMapper customMapper,
                          SeqService seqService) {
        this.htmlService = htmlService;
        this.attachmentService = attachmentService;
        this.bufferMapper = bufferMapper;
        this.channelGroupMapper = channelGroupMapper;
        this.mapper = mapper;
        this.extMapper = extMapper;
        this.treeMapper = treeMapper;
        this.customMapper = customMapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Channel bean, ChannelExt ext, List<Integer> groupIds, List<ChannelCustom> customList) {
        bean.setId(seqService.getNextVal(Channel.TABLE_NAME));
        ext.setId(bean.getId());
        if (StringUtils.isBlank(bean.getAlias())) {
            bean.setAlias(String.valueOf(bean.getId()));
        }
        if (bean.getParentId() != null) {
            Channel parent = select(bean.getParentId());
            bean.setDepth((short) ((parent != null ? parent.getDepth() : 0) + 1));
        }
        mapper.insert(bean);
        extMapper.insert(ext);
        bufferMapper.insert(new ChannelBuffer(bean.getId()));
        // 处理树结构关系
        treeMapper.insert(new ChannelTree(bean.getId(), bean.getId()));
        if (bean.getParentId() != null) {
            treeMapper.add(bean.getId(), bean.getParentId());
        }
        insertGroupIds(groupIds, bean.getId());
        customList.forEach(it -> {
            it.setChannelId(bean.getId());
            customMapper.insert(it);
        });
        attachmentService.insertRefer(Channel.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    private void insertGroupIds(List<Integer> groupIds, Integer channelId) {
        groupIds.forEach(groupId -> channelGroupMapper.insert(new ChannelGroup(channelId, groupId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Channel bean, ChannelExt ext, @Nullable Integer parentId, @Nullable List<Integer> groupIds,
                       @Nullable List<ChannelCustom> customList) {
        // 处理树结构关系
        int origDepth = 0, newDepth = 0;
        if (!Objects.equals(bean.getParentId(), parentId)) {
            if (bean.getParent() != null) {
                origDepth = bean.getParent().getDepth();
            }
            treeMapper.move(bean.getId());
            if (parentId != null) {
                Channel newParent = select(parentId);
                if (newParent != null) {
                    newDepth = newParent.getDepth();
                }
                treeMapper.append(bean.getId(), parentId);
            }
            bean.setParentId(parentId);
        }
        mapper.update(bean);
        extMapper.update(ext);
        if (newDepth != origDepth) {
            mapper.updateDepth(bean.getId(), (short) (newDepth - origDepth));
        }
        if (groupIds != null) {
            channelGroupMapper.deleteByChannelId(bean.getId());
            insertGroupIds(groupIds, bean.getId());
        }
        if (customList != null) {
            customMapper.deleteByChannelId(bean.getId());
            customList.forEach(it -> {
                it.setChannelId(bean.getId());
                customMapper.insert(it);
            });
        }
        attachmentService.updateRefer(Channel.TABLE_NAME, bean.getId(), bean.getAttachmentUrls());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ChannelExt ext) {
        extMapper.update(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Channel> list) {
        short order = 1;
        for (Channel bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        Channel bean = mapper.select(id);
        if (bean == null) {
            return 0;
        }
        int count = bean.getChildren().stream().mapToInt(item -> delete(item.getId())).sum();
        deleteListeners.forEach(it -> it.preChannelDelete(id));
        attachmentService.deleteRefer(Channel.TABLE_NAME, id);
        treeMapper.deleteById(id);
        channelGroupMapper.deleteByChannelId(id);
        customMapper.deleteByChannelId(id);
        bufferMapper.delete(id);
        extMapper.delete(id);
        count += mapper.delete(id);
        htmlService.deleteChannelHtml(bean);
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    @Transactional(readOnly = true)
    public Channel select(Integer id) {
        return mapper.select(id);
    }

    @Nullable
    @Transactional(readOnly = true)
    public Channel findBySiteIdAndAlias(Integer siteId, String alias) {
        List<Channel> list = listBySiteIdAndAlias(siteId, Collections.singletonList(alias), false);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Transactional(readOnly = true)
    public List<Channel> listBySiteIdAndAlias(@Nullable Integer siteId, Collection<String> alias,
                                              boolean isIncludeSubSite) {
        Map<String, Object> queryMap = new HashMap<>(16);
        if (siteId != null) {
            if (isIncludeSubSite) {
                queryMap.put("EQ_site@SiteTree@descendant-ancestorId_Int", siteId);
            } else {
                queryMap.put("EQ_siteId_Int", siteId);
            }
        }
        queryMap.put("In_alias", alias);
        return selectList(queryMap, null);
    }

    @Transactional(readOnly = true)
    public List<Channel> selectList(@Nullable Map<String, Object> queryMap,
                                    @Nullable Map<String, String> customsQueryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Channel.TABLE_NAME, "order,id");
        List<QueryInfo.WhereCondition> customsCondition = CustomFieldQuery.parse(customsQueryMap);
        return mapper.selectAll(queryInfo, customsCondition);
    }

    @Transactional(readOnly = true)
    public List<Channel> selectList(@Nullable Map<String, Object> queryMap,
                                    @Nullable Map<String, String> customsQueryMap,
                                    @Nullable Integer offset, @Nullable Integer limit) {
        return PageHelper.offsetPage(offset == null ? 0 : offset, limit == null ? Integer.MAX_VALUE : limit, false)
                .doSelectPage(() -> selectList(queryMap, customsQueryMap));
    }

    private List<ChannelDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<ChannelDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }
}