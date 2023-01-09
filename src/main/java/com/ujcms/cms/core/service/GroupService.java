package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.GroupAccess;
import com.ujcms.cms.core.listener.GroupDeleteListener;
import com.ujcms.cms.core.mapper.GroupAccessMapper;
import com.ujcms.cms.core.mapper.GroupMapper;
import com.ujcms.cms.core.service.args.GroupArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 用户组 Service
 *
 * @author PONY
 */
@Service
public class GroupService {
    private final GroupAccessMapper groupAccessMapper;
    private final GroupMapper mapper;
    private final SeqService seqService;

    public GroupService(GroupAccessMapper groupAccessMapper, GroupMapper mapper, SeqService seqService) {
        this.groupAccessMapper = groupAccessMapper;
        this.mapper = mapper;
        this.seqService = seqService;
    }


    @Transactional(rollbackFor = Exception.class)
    public void insert(Group bean) {
        bean.setId(seqService.getNextVal(Group.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Group bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Group bean, Collection<Integer> accessPermissions, Integer siteId) {
        mapper.update(bean);
        groupAccessMapper.deleteByGroupId(bean.getId(), siteId);
        insertAccessPermissions(accessPermissions, bean.getId(), siteId);
    }

    private void insertAccessPermissions(Collection<Integer> accessPermissions, Integer groupId, Integer siteId) {
        accessPermissions.forEach(channelId -> groupAccessMapper.insert(new GroupAccess(groupId, channelId, siteId)));
    }


    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Group> list) {
        short order = 1;
        for (Group bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        deleteListeners.forEach(it -> it.preGroupDelete(id));
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Group select(Integer id) {
        return mapper.select(id);
    }

    public List<Group> selectList(GroupArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Group.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Integer> listAccessPermissions(Integer groupId, @Nullable Integer siteId) {
        return groupAccessMapper.listChannelByGroupId(groupId, siteId);
    }

    public Group getAnonymous() {
        Group group = select(Group.ANONYMOUS_ID);
        if (group == null) {
            throw new IllegalStateException("Anonymous Group data not exist!");
        }
        return group;
    }

    private List<GroupDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<GroupDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }
}