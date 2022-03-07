package com.ujcms.core.service;

import com.ujcms.core.domain.Role;
import com.ujcms.core.mapper.RoleMapper;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 角色 Service
 *
 * @author PONY
 */
@Service
public class RoleService {
    private RoleMapper mapper;

    private SeqService seqService;

    public RoleService(RoleMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }


    @Transactional(rollbackFor = Exception.class)
    public void insert(Role bean) {
        bean.setId(seqService.getNextVal(Role.TABLE_NAME));
        mapper.insert(bean);
    }


    @Transactional(rollbackFor = Exception.class)
    public void update(Role bean) {
        mapper.update(bean);
    }


    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Role> list) {
        short order = 1;
        for (Role bean : list) {
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
    public Role select(Integer id) {
        return mapper.select(id);
    }

    public List<Role> selectList(@Nullable Map<String, Object> queryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Role.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }
}