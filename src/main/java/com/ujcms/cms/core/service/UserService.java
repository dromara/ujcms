package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.UserExt;
import com.ujcms.cms.core.domain.UserRole;
import com.ujcms.cms.core.listener.OrgDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.UserExtMapper;
import com.ujcms.cms.core.mapper.UserMapper;
import com.ujcms.cms.core.mapper.UserRoleMapper;
import com.ujcms.cms.core.service.args.UserArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
import com.ujcms.util.security.CredentialsDigest;
import com.ujcms.util.security.Secures;
import com.ujcms.util.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 用户 Service
 *
 * @author PONY
 */
@Service
public class UserService implements OrgDeleteListener {
    private CredentialsDigest credentialsDigest;
    private UserMapper mapper;
    private UserExtMapper extMapper;
    private UserRoleMapper userRoleMapper;
    private SeqService seqService;

    public UserService(CredentialsDigest credentialsDigest, UserMapper mapper, UserExtMapper extMapper,
                       UserRoleMapper userRoleMapper, SeqService seqService) {
        this.credentialsDigest = credentialsDigest;
        this.mapper = mapper;
        this.extMapper = extMapper;
        this.userRoleMapper = userRoleMapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(User bean, UserExt ext, @Nullable List<Integer> roleIds) {
        bean.setId(seqService.getNextVal(User.TABLE_NAME));
        if (StringUtils.isNotBlank(bean.getPlainPassword())) {
            bean.setSalt(Secures.nextSalt());
            bean.setPassword(credentialsDigest.digest(bean.getPlainPassword(), bean.getSalt()));
        }
        ext.setId(bean.getId());
        mapper.insert(bean);
        extMapper.insert(ext);
        if (roleIds != null) {
            roleIds.forEach(roleId -> userRoleMapper.insert(new UserRole(bean.getId(), roleId)));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean, UserExt ext, @Nullable List<Integer> roleIds) {
        update(bean, roleIds);
        update(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean, @Nullable List<Integer> roleIds) {
        update(bean);
        if (roleIds != null) {
            userRoleMapper.deleteByUserId(bean.getId());
            roleIds.forEach(roleId -> userRoleMapper.insert(new UserRole(bean.getId(), roleId)));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean) {
        if (StringUtils.isNotBlank(bean.getPlainPassword())) {
            bean.setSalt(Secures.nextSalt());
            bean.setPassword(credentialsDigest.digest(bean.getPlainPassword(), bean.getSalt()));
        }
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UserExt ext) {
        extMapper.update(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateLogin(UserExt userExt, String ip) {
        userExt.setLoginCount(userExt.getLoginCount() + 1);
        userExt.setLoginDate(OffsetDateTime.now());
        userExt.setLoginIp(ip);
        extMapper.update(userExt);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        deleteListeners.forEach(it -> it.preUserDelete(id));
        extMapper.delete(id);
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public User select(Integer id) {
        return mapper.select(id);
    }

    @Nullable
    public User selectByUsername(String username) {
        return mapper.selectByUsername(username);
    }

    @Nullable
    public User selectByEmail(String email) {
        return mapper.selectByEmail(email);
    }

    @Nullable
    public User selectByMobile(String mobile) {
        return mapper.selectByMobile(mobile);
    }

    public List<User> selectList(UserArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), User.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo, args.getOrgId());
    }

    public List<User> selectList(UserArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<User> selectPage(UserArgs args, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public boolean existsByOrgId(Integer orgId) {
        return PageHelper.offsetPage(0, 1, false).doCount(() -> mapper.countByOrgId(orgId)) > 0;
    }

    public boolean existsByRoleId(Integer roleId, Integer notOrgId) {
        return PageHelper.offsetPage(0, 1, false).doCount(() -> mapper.countByRoleId(roleId, notOrgId)) > 0;
    }

    @Override
    public void preOrgDelete(Integer orgId) {
        if (existsByOrgId(orgId)) {
            throw new LogicException("error.refer.user");
        }
    }

    private List<UserDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<UserDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }
}