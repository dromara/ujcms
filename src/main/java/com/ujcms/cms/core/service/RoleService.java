package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.RoleArticle;
import com.ujcms.cms.core.domain.RoleChannel;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.RoleArticleMapper;
import com.ujcms.cms.core.mapper.RoleChannelMapper;
import com.ujcms.cms.core.mapper.RoleMapper;
import com.ujcms.cms.core.mapper.UserRoleMapper;
import com.ujcms.cms.core.service.args.RoleArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
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
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper mapper;
    private final SeqService seqService;

    public RoleService(RoleChannelMapper roleChannelMapper, RoleArticleMapper roleArticleMapper,
                       UserRoleMapper userRoleMapper, RoleMapper mapper, SeqService seqService) {
        this.roleChannelMapper = roleChannelMapper;
        this.roleArticleMapper = roleArticleMapper;
        this.userRoleMapper = userRoleMapper;
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Role bean, Integer siteId) {
        bean.setId(seqService.getNextVal(Role.TABLE_NAME));
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Role bean, Collection<Integer> articlePermissions, Integer siteId) {
        insert(bean, siteId);
        insertArticlePermissions(articlePermissions, bean.getId(), siteId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Role bean, Integer siteId) {
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Role bean, Collection<Integer> articlePermissions, Collection<Integer> channelPermissions,
                       Integer siteId) {
        // 全局共享数据的站点ID设置为null
        bean.setSiteId(bean.isGlobal() ? null : siteId);
        mapper.update(bean);
        roleArticleMapper.deleteByRoleId(bean.getId(), siteId);
        roleChannelMapper.deleteByRoleId(bean.getId(), siteId);
        insertArticlePermissions(articlePermissions, bean.getId(), siteId);
        insertChannelPermissions(channelPermissions, bean.getId(), siteId);
    }

    private void insertArticlePermissions(Collection<Integer> articlePermissions, Integer roleId, Integer siteId) {
        articlePermissions.forEach(channelId -> roleArticleMapper.insert(new RoleArticle(roleId, channelId, siteId)));
    }

    private void insertChannelPermissions(Collection<Integer> channelPermissions, Integer roleId, Integer siteId) {
        channelPermissions.forEach(channelId -> roleChannelMapper.insert(new RoleChannel(roleId, channelId, siteId)));
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
        roleArticleMapper.deleteByRoleId(id, null);
        roleChannelMapper.deleteByRoleId(id, null);
        userRoleMapper.deleteByRoleId(id);
        return mapper.delete(id);
    }


    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public Role select(Integer id) {
        return mapper.select(id);
    }

    public List<Role> selectList(RoleArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), Role.TABLE_NAME, "scope_desc,rank,order,id");
        return mapper.selectAll(queryInfo);
    }

    public List<Integer> listArticlePermissions(Integer roleId, @Nullable Integer siteId) {
        return roleArticleMapper.listChannelByRoleId(roleId, siteId);
    }

    public List<Integer> listChannelPermissions(Integer roleId, @Nullable Integer siteId) {
        return roleChannelMapper.listChannelByRoleId(roleId, siteId);
    }

    public List<Integer> listChannelPermissions(Collection<Integer> roleIds, @Nullable Integer siteId) {
        return roleChannelMapper.listChannelByRoleIds(roleIds, siteId);
    }

    @Override
    public void preSiteDelete(Integer siteId) {
        roleChannelMapper.deleteBySiteId(siteId);
        roleArticleMapper.deleteBySiteId(siteId);
        userRoleMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }

    @Override
    public int deleteListenerOrder() {
        return 150;
    }
}