package com.ujcms.cms.core.service;

import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.OrgTree;
import com.ujcms.cms.core.listener.OrgDeleteListener;
import com.ujcms.cms.core.mapper.OrgMapper;
import com.ujcms.cms.core.mapper.OrgTreeMapper;
import com.ujcms.cms.core.service.args.OrgArgs;
import com.ujcms.util.db.tree.TreeService;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 组织 Service
 *
 * @author PONY
 */
@Service
public class OrgService {
    private final OrgMapper mapper;
    private final OrgTreeMapper treeMapper;
    private final SeqService seqService;
    private final TreeService<Org, OrgTree> treeService;

    public OrgService(OrgMapper mapper, OrgTreeMapper treeMapper1, OrgTreeMapper treeMapper, SeqService seqService) {
        this.mapper = mapper;
        this.treeMapper = treeMapper1;
        this.seqService = seqService;
        this.treeService = new TreeService<>(mapper, treeMapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Org bean) {
        bean.setId(seqService.getNextVal(Org.TABLE_NAME));
        treeService.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Org bean, @Nullable Integer parentId) {
        treeService.update(bean, parentId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Org> list) {
        treeService.updateOrder(list);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        Org bean = mapper.select(id);
        if (bean == null) {
            return 0;
        }
        return delete(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Org bean) {
        int count = bean.getChildren().stream().mapToInt(item -> delete(item.getId())).sum();
        deleteListeners.forEach(it -> it.preOrgDelete(bean.getId()));
        return treeService.delete(bean.getId(), bean.getOrder()) + count;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Org select(Integer id) {
        return mapper.select(id);
    }

    public List<Org> selectList(OrgArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Org.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo, args.getParentId());
    }

    public List<Org> selectList(OrgArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public List<Integer> listByAncestorId(Integer ancestorId) {
        return treeMapper.listByAncestorId(ancestorId);
    }

    /**
     * 是否存在上下级关系
     *
     * @param userOrgId 用户组织ID
     * @param siteOrgId 站点组织ID
     * @return 存在返回 {@code true}，不存在返回 {@code false}
     */
    public boolean hasRelationship(Integer userOrgId, Integer siteOrgId) {
        return PageHelper.offsetPage(0, 1).doCount(() -> treeMapper.countByOrgId(userOrgId, siteOrgId)) > 0;
    }

    private List<OrgDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<OrgDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }
}