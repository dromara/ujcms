package com.ujcms.core.service;

import com.github.pagehelper.PageHelper;
import com.ujcms.core.domain.Org;
import com.ujcms.core.mapper.OrgMapper;
import com.ofwise.util.query.QueryInfo;
import com.ofwise.util.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 组织 QueryService
 *
 * @author PONY
 */
@Service
public class OrgQueryService {
    private OrgMapper mapper;
    // private OrgTreeMapper treeMapper;
    // private SeqService seqService;

    public OrgQueryService(OrgMapper mapper) {
        this.mapper = mapper;
        // this.treeMapper = treeMapper;
        // this.seqService = seqService;
    }
    //
    // @Transactional(rollbackFor = Exception.class)
    // public void insert(Org bean) {
    //     bean.setId(seqService.getNextVal(Org.TABLE_NAME));
    //     if (bean.getParentId() != null) {
    //         Org parent = select(bean.getParentId());
    //         bean.setDepth((short) ((parent != null ? parent.getDepth() : 0) + 1));
    //     }
    //     mapper.insert(bean);
    //     // 处理树结构关系
    //     treeMapper.insert(new OrgTree(bean.getId(), bean.getId()));
    //     if (bean.getParentId() != null) {
    //         treeMapper.add(bean.getId(), bean.getParentId());
    //     }
    // }
    //
    // @Transactional(rollbackFor = Exception.class)
    // public void update(Org bean, @Nullable Integer parentId) {
    //     // 处理树结构关系
    //     int origDepth = 0, newDepth = 0;
    //     if (!Objects.equals(bean.getParentId(), parentId)) {
    //         if (bean.getParent() != null) {
    //             origDepth = bean.getParent().getDepth();
    //         }
    //         treeMapper.move(bean.getId());
    //         if (parentId != null) {
    //             Org newParent = select(parentId);
    //             if (newParent != null) {
    //                 newDepth = newParent.getDepth();
    //             }
    //             treeMapper.append(bean.getId(), parentId);
    //         }
    //         bean.setParentId(parentId);
    //     }
    //     mapper.update(bean);
    //     if (newDepth != origDepth) {
    //         mapper.updateDepth(bean.getId(), (short) (newDepth - origDepth));
    //     }
    // }
    //
    //
    // @Transactional(rollbackFor = Exception.class)
    // public void updateOrder(List<Org> list) {
    //     short order = 1;
    //     for (Org bean : list) {
    //         bean.setOrder(order);
    //         mapper.update(bean);
    //         order += 1;
    //     }
    // }
    //
    // @Transactional(rollbackFor = Exception.class)
    // public int delete(int id) {
    //     Org bean = mapper.select(id);
    //     if (bean == null) {
    //         return 0;
    //     }
    //     int count = bean.getChildren().stream().mapToInt(item -> delete(item.getId())).sum();
    //     deleteListeners.forEach(it -> it.preOrgDelete(id));
    //     treeMapper.deleteById(id);
    //     return mapper.delete(id) + count;
    // }
    //
    // @Transactional(rollbackFor = Exception.class)
    // public int delete(List<Integer> ids) {
    //     return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    // }

    @Nullable
    public Org select(int id) {
        return mapper.select(id);
    }

    public List<Org> selectList(@Nullable Map<String, Object> queryMap) {
        QueryInfo queryInfo = QueryParser.parse(queryMap, Org.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Org> selectList(@Nullable Map<String, Object> queryMap, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(queryMap));
    }

    // private List<OrgDeleteListener> deleteListeners = Collections.emptyList();
    //
    // @Lazy
    // @Autowired(required = false)
    // public void setDeleteListeners(List<OrgDeleteListener> deleteListeners) {
    //     this.deleteListeners = deleteListeners;
    // }
}