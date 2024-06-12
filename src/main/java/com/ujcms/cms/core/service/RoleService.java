package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.RoleArticle;
import com.ujcms.cms.core.domain.RoleChannel;
import com.ujcms.cms.core.domain.RoleOrg;
import com.ujcms.cms.core.domain.base.RoleBase;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.service.args.RoleArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 角色 Service
 *
 * @author PONY
 */
@Service
public class RoleService implements SiteDeleteListener {
    private final RoleChannelMapper roleChannelMapper;
    private final RoleArticleMapper roleArticleMapper;
    private final RoleOrgMapper roleOrgMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public RoleService(RoleChannelMapper roleChannelMapper, RoleArticleMapper roleArticleMapper,
                       RoleOrgMapper roleOrgMapper, UserRoleMapper userRoleMapper,
                       RoleMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.roleChannelMapper = roleChannelMapper;
        this.roleArticleMapper = roleArticleMapper;
        this.roleOrgMapper = roleOrgMapper;
        this.userRoleMapper = userRoleMapper;
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Role bean, Long siteId) {
        bean.setId(snowflakeSequence.nextId());
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Role bean, Long siteId) {
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Role bean, Collection<Long> articlePermissions, Collection<Long> channelPermissions,
                       Collection<Long> orgPermissions, Long siteId) {
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.update(bean);
        roleArticleMapper.deleteByRoleId(bean.getId(), siteId);
        roleChannelMapper.deleteByRoleId(bean.getId(), siteId);
        roleOrgMapper.deleteByRoleId(bean.getId(), siteId);
        articlePermissions.forEach(channelId ->
                roleArticleMapper.insert(new RoleArticle(bean.getId(), channelId, siteId)));
        channelPermissions.forEach(channelId ->
                roleChannelMapper.insert(new RoleChannel(bean.getId(), channelId, siteId)));
        orgPermissions.forEach(orgId ->
                roleOrgMapper.insert(new RoleOrg(bean.getId(), orgId, siteId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(List<Role> list) {
        int order = 1;
        for (Role bean : list) {
            bean.setOrder(order);
            mapper.update(bean);
            order += 1;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        roleArticleMapper.deleteByRoleId(id, null);
        roleChannelMapper.deleteByRoleId(id, null);
        roleOrgMapper.deleteByRoleId(id, null);
        userRoleMapper.deleteByRoleId(id);
        return mapper.delete(id);
    }


    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Role select(Long id) {
        return mapper.select(id);
    }

    public List<Role> selectList(RoleArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), RoleBase.TABLE_NAME, "scope_desc,rank,order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Long> listArticlePermissions(Long roleId, @Nullable Long siteId) {
        return roleArticleMapper.listChannelByRoleId(roleId, siteId);
    }

    public List<Long> listChannelPermissions(Long roleId, @Nullable Long siteId) {
        return roleChannelMapper.listChannelByRoleId(roleId, siteId);
    }

    public List<Long> listOrgPermissions(Long roleId, @Nullable Long siteId) {
        return roleOrgMapper.listOrgByRoleId(roleId, siteId);
    }

    public List<Role> listNotAllArticlePermission(@Nullable Long siteId) {
        RoleArgs args = RoleArgs.of().allArticlePermission(false).scopeSiteId(siteId);
        return selectList(args);
    }

    public List<Role> listNotAllChannelPermission(@Nullable Long siteId) {
        RoleArgs args = RoleArgs.of().allChannelPermission(false).scopeSiteId(siteId);
        return selectList(args);
    }

    @Override
    public void preSiteDelete(Long siteId) {
        roleChannelMapper.deleteBySiteId(siteId);
        roleArticleMapper.deleteBySiteId(siteId);
        roleOrgMapper.deleteBySiteId(siteId);
        userRoleMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 150;
    }
}