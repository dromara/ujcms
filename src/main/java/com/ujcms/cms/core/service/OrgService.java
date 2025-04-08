package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.domain.base.OrgBase;
import com.ujcms.cms.core.listener.OrgDeleteListener;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.service.args.OrgArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.db.tree.TreeMoveType;
import com.ujcms.commons.db.tree.TreeService;
import com.ujcms.commons.db.tree.TreeSortEntity;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 组织 Service
 *
 * @author PONY
 */
@Service
public class OrgService {
    private final OrgMapper mapper;
    private final OrgTreeMapper treeMapper;
    private final OrgArticleMapper orgArticleMapper;
    private final OrgChannelMapper orgChannelMapper;
    private final OrgPermMapper orgPermMapper;
    private final UserOrgMapper userOrgMapper;
    private final RoleOrgMapper roleOrgMapper;
    private final SnowflakeSequence snowflakeSequence;
    private final TreeService<Org> treeService;

    public OrgService(OrgMapper mapper, OrgTreeMapper treeMapper, OrgArticleMapper orgArticleMapper,
                      OrgChannelMapper orgChannelMapper, OrgPermMapper orgPermMapper, UserOrgMapper userOrgMapper,
                      RoleOrgMapper roleOrgMapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.treeMapper = treeMapper;
        this.orgArticleMapper = orgArticleMapper;
        this.orgChannelMapper = orgChannelMapper;
        this.orgPermMapper = orgPermMapper;
        this.userOrgMapper = userOrgMapper;
        this.roleOrgMapper = roleOrgMapper;
        this.snowflakeSequence = snowflakeSequence;
        this.treeService = new TreeService<>(mapper, treeMapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Org bean) {
        bean.setId(snowflakeSequence.nextId());
        treeService.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Org bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void move(Org from, Org to, TreeMoveType type) {
        treeService.move(from, to, type);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePermission(
            Long id, Long siteId, Collection<Long> articlePermissions, Collection<Long> channelPermissions,
            @Nullable Long ancestorOrgId, Collection<Long> orgPermissions) {
        orgArticleMapper.deleteByOrgId(id, siteId);
        orgChannelMapper.deleteByOrgId(id, siteId);
        orgPermMapper.deleteByOrgIdAndAncestorOrgId(id, ancestorOrgId);
        articlePermissions.forEach(channelId ->
                orgArticleMapper.insert(new OrgArticle(id, channelId, siteId)));
        channelPermissions.forEach(channelId ->
                orgChannelMapper.insert(new OrgChannel(id, channelId, siteId)));
        orgPermissions.forEach(perOrgId -> {
            if (orgPermMapper.select(id, perOrgId) == null) {
                orgPermMapper.insert(new OrgPerm(id, perOrgId));
            }
        });
    }

    public List<Org> listForTidy() {
        return mapper.listForTidy();
    }

    public List<TreeSortEntity> toTree(List<Org> list) {
        return treeService.toTree(list);
    }

    @Transactional(rollbackFor = Exception.class)
    public void tidyTreeOrderAndDepth(List<TreeSortEntity> tree, int size) {
        treeService.tidyTreeOrderAndDepth(tree, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation() {
        treeMapper.deleteAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public void tidyTreeRelation(List<TreeSortEntity> tree, int size) {
        treeService.tidyTreeRelation(tree, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Org bean) {
        deleteListeners.forEach(it -> it.preOrgDelete(bean.getId()));
        orgArticleMapper.deleteByOrgId(bean.getId(), null);
        orgChannelMapper.deleteByOrgId(bean.getId(), null);
        orgPermMapper.deleteByOrgId(bean.getId());
        userOrgMapper.deleteByOrgId(bean.getId());
        roleOrgMapper.deleteByOrgId(bean.getId());
        return treeService.delete(bean.getId(), bean.getOrder());
    }

    @Nullable
    public Org select(Long id) {
        return mapper.select(id);
    }

    public List<Org> selectList(OrgArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), OrgBase.TABLE_NAME, "order,id");
        return mapper.selectAll(queryInfo, args.getAncestorId(), args.isQueryHasChildren());
    }

    public List<Org> selectList(OrgArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public List<Org> listChildren(Long parentId) {
        return mapper.listChildren(parentId);
    }

    public List<Long> listByAncestorId(Long ancestorId) {
        return treeMapper.listByAncestorId(ancestorId);
    }

    public List<Long> listArticlePermissions(Long orgId, @Nullable Long siteId) {
        return orgArticleMapper.listChannelByOrgId(orgId, siteId);
    }

    public List<Long> listChannelPermissions(Long orgId, @Nullable Long siteId) {
        return orgChannelMapper.listChannelByOrgId(orgId, siteId);
    }

    public List<Long> listOrgPermissions(Long orgId, @Nullable Long siteId) {
        return orgPermMapper.listPermOrgByOrgId(orgId, siteId);
    }

    public List<Long> listPermissions(Collection<Long> roleIds, Collection<Long> orgIds) {
        return mapper.listPermissions(roleIds, orgIds);
    }

    public boolean existsByArticleOrgId(Long channelId, Collection<Long> orgIds) {
        return mapper.existsByArticleOrgId(channelId, orgIds) > 0;
    }

    /**
     * 是否存在上下级关系
     *
     * @param userOrgId 用户组织ID
     * @param siteOrgId 站点组织ID
     * @return 存在返回 {@code true}，不存在返回 {@code false}
     */
    public boolean hasRelationship(Long userOrgId, Long siteOrgId) {
        return PageMethod.offsetPage(0, 1).doCount(() -> treeMapper.countByOrgId(userOrgId, siteOrgId)) > 0;
    }

    /**
     * 是否后代
     *
     * @param ancestorId   祖先ID
     * @param descendantId 后代ID
     * @return 是否后代
     */
    public boolean isDescendant(Long ancestorId, Long descendantId) {
        return PageMethod.offsetPage(0, 1).doCount(() ->
                treeMapper.countByOrgId(ancestorId, descendantId)) > 0;
    }

    private List<OrgDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<OrgDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }
}