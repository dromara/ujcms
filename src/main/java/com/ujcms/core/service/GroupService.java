package com.ujcms.core.service;

import com.ujcms.core.domain.Group;
import com.ujcms.core.mapper.GroupMapper;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户组 Service
 *
 * @author PONY
 */
@Service
public class GroupService {
    private GroupMapper mapper;

    private SeqService seqService;

    public GroupService(GroupMapper mapper, SeqService seqService) {
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
        return mapper.delete(id);
    }


    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(mapper::delete).sum();
    }

    @Nullable
    public Group select(Integer id) {
        return mapper.select(id);
    }

    public List<Group> selectList(@Nullable Map<String, Object> queryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Group.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }
}