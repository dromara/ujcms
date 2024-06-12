package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.domain.base.UserBase;
import com.ujcms.cms.core.listener.GroupDeleteListener;
import com.ujcms.cms.core.listener.OrgDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.service.args.UserArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import com.ujcms.commons.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.ujcms.cms.core.domain.User.ANONYMOUS_ID;

/**
 * 用户 Service
 *
 * @author PONY
 */
@Service
public class UserService implements OrgDeleteListener, GroupDeleteListener {
    private final PasswordEncoder passwordEncoder;
    private final AttachmentService attachmentService;
    private final UserMapper mapper;
    private final UserExtMapper extMapper;
    private final UserOpenidMapper openidMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserOrgMapper userOrgMapper;
    private final OrgMapper orgMapper;
    private final SnowflakeSequence snowflakeSequence;

    public UserService(PasswordEncoder passwordEncoder, AttachmentService attachmentService,
                       UserMapper mapper, UserExtMapper extMapper, UserOpenidMapper openidMapper,
                       UserRoleMapper userRoleMapper, UserOrgMapper userOrgMapper,
                       OrgMapper orgMapper, SnowflakeSequence snowflakeSequence) {
        this.passwordEncoder = passwordEncoder;
        this.attachmentService = attachmentService;
        this.mapper = mapper;
        this.extMapper = extMapper;
        this.openidMapper = openidMapper;
        this.userRoleMapper = userRoleMapper;
        this.userOrgMapper = userOrgMapper;
        this.orgMapper = orgMapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    private void insertUserOrg(Long userId, List<Long> orgIds) {
        for (Long orgId : orgIds) {
            Org org = orgMapper.select(orgId);
            if (org == null || userOrgMapper.select(userId, orgId) != null) {
                continue;
            }
            userOrgMapper.insert(new UserOrg(userId, orgId, org.getOrder()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(User bean, UserExt ext, List<Long> orgIds) {
        bean.setId(snowflakeSequence.nextId());
        ext.setId(bean.getId());
        mapper.insert(bean);
        extMapper.insert(ext);
        insertUserOrg(bean.getId(), orgIds);
        attachmentService.insertRefer(UserBase.TABLE_NAME, bean.getId(), bean.getAvatarList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertOpenid(UserOpenid bean) {
        openidMapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteOpenid(Long userId, String provider) {
        return openidMapper.delete(userId, provider);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean, UserExt ext, @Nullable Long ancestorOrgId, List<Long> orgIds) {
        update(bean);
        update(ext);
        userOrgMapper.deleteByUserIdAndAncestorOrgId(bean.getId(), ancestorOrgId);
        insertUserOrg(bean.getId(), orgIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean, UserExt ext) {
        update(bean);
        update(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean, List<Long> roleIds) {
        update(bean);
        userRoleMapper.deleteByUserId(bean.getId());
        roleIds.forEach(roleId -> userRoleMapper.insert(new UserRole(bean.getId(), roleId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean) {
        attachmentService.updateRefer(UserBase.TABLE_NAME, bean.getId(), bean.getAvatarList());
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UserExt ext) {
        extMapper.update(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(User user, UserExt userExt, String password) {
        String origPassword = user.getPassword();
        if (StringUtils.isNotBlank(origPassword)) {
            user.addHistoryPassword(origPassword);
        }
        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordModified(OffsetDateTime.now());
        userExt.setErrorCount(0);
        mapper.update(user);
        extMapper.update(userExt);
    }

    @Transactional(rollbackFor = Exception.class)
    public void loginSuccess(UserExt userExt, String ip) {
        userExt.setLoginCount(userExt.getLoginCount() + 1);
        userExt.setLoginDate(OffsetDateTime.now());
        userExt.setLoginIp(ip);
        userExt.setErrorCount(0);
        extMapper.update(userExt);
    }


    @Transactional(rollbackFor = Exception.class)
    public void loginFailure(UserExt userExt, int lockMinutes) {
        OffsetDateTime now = OffsetDateTime.now();
        if (Duration.between(userExt.getErrorDate(), now).toMinutes() > lockMinutes) {
            // 超过锁定时间，重新计数
            userExt.setErrorCount(1);
        } else {
            // 累加
            userExt.setErrorCount(userExt.getErrorCount() + 1);
        }
        userExt.setErrorDate(now);
        extMapper.update(userExt);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        deleteListeners.forEach(it -> it.preUserDelete(id));
        userRoleMapper.deleteByUserId(id);
        attachmentService.deleteRefer(UserBase.TABLE_NAME, id);
        openidMapper.deleteByUserId(id);
        extMapper.delete(id);
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public User select(Long id) {
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

    @Nullable
    public User selectByOpenid(String provider, String openid) {
        return mapper.selectByOpenid(provider, openid);
    }

    public List<User> selectList(UserArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), UserBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo, args.getOrgId());
    }

    public User anonymous() {
        return Optional.ofNullable(select(ANONYMOUS_ID)).orElseThrow(() ->
                new IllegalStateException("Anonymous user(ID=0) not found."));
    }

    public List<User> selectList(UserArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<User> selectPage(UserArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    /**
     * 统计用户数量
     *
     * @param created 创建日期
     * @return 用户数量
     */
    public int countByCreated(@Nullable OffsetDateTime created) {
        return mapper.countByCreated(created);
    }

    public boolean existsByOrgId(Long orgId) {
        return mapper.existsByOrgId(orgId) > 0;
    }

    public boolean existsByGroupId(Long groupId) {
        return mapper.existsByGroupId(groupId) > 0;
    }

    public boolean existsByRoleId(Long roleId, Long notOrgId) {
        return mapper.existsByRoleId(roleId, notOrgId) > 0;
    }

    @Override
    public void preOrgDelete(Long orgId) {
        if (existsByOrgId(orgId)) {
            throw new LogicException("error.refer.user");
        }
    }

    @Override
    public void preGroupDelete(Long groupId) {
        if (existsByGroupId(groupId)) {
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