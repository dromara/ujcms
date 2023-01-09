package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.UserExt;
import com.ujcms.cms.core.domain.UserOpenid;
import com.ujcms.cms.core.domain.UserRole;
import com.ujcms.cms.core.listener.GroupDeleteListener;
import com.ujcms.cms.core.listener.OrgDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.UserExtMapper;
import com.ujcms.cms.core.mapper.UserMapper;
import com.ujcms.cms.core.mapper.UserOpenidMapper;
import com.ujcms.cms.core.mapper.UserRoleMapper;
import com.ujcms.cms.core.service.args.UserArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;
import com.ujcms.util.web.exception.LogicException;
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
    private final SeqService seqService;

    public UserService(PasswordEncoder passwordEncoder, AttachmentService attachmentService,
                       UserMapper mapper, UserExtMapper extMapper, UserOpenidMapper openidMapper,
                       UserRoleMapper userRoleMapper, SeqService seqService) {
        this.passwordEncoder = passwordEncoder;
        this.attachmentService = attachmentService;
        this.mapper = mapper;
        this.extMapper = extMapper;
        this.openidMapper = openidMapper;
        this.userRoleMapper = userRoleMapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(User bean, UserExt ext) {
        bean.setId(seqService.getNextVal(User.TABLE_NAME));
        ext.setId(bean.getId());
        mapper.insert(bean);
        extMapper.insert(ext);
        attachmentService.insertRefer(User.TABLE_NAME, bean.getId(), bean.getAvatarList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertOpenid(UserOpenid bean) {
        openidMapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteOpenid(Integer userId, String provider) {
        return openidMapper.delete(userId, provider);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean, UserExt ext) {
        update(bean);
        update(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean, List<Integer> roleIds) {
        update(bean);
        userRoleMapper.deleteByUserId(bean.getId());
        roleIds.forEach(roleId -> userRoleMapper.insert(new UserRole(bean.getId(), roleId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean) {
        attachmentService.updateRefer(User.TABLE_NAME, bean.getId(), bean.getAvatarList());
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UserExt ext) {
        extMapper.update(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(User user, UserExt userExt, String password) {
        String origPassword = user.getPassword();
        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordModified(OffsetDateTime.now());
        user.addHistoryPassword(origPassword);
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
    public int delete(Integer id) {
        deleteListeners.forEach(it -> it.preUserDelete(id));
        attachmentService.deleteRefer(User.TABLE_NAME, id);
        openidMapper.deleteByUserId(id);
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

    @Nullable
    public User selectByOpenid(String provider, String openid) {
        return mapper.selectByOpenid(provider, openid);
    }

    public List<User> selectList(UserArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), User.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo, args.getOrgId());
    }

    public User anonymous() {
        return Optional.ofNullable(select(ANONYMOUS_ID)).orElseThrow(() ->
                new IllegalStateException("Anonymous user(ID=0) not found."));
    }

    public List<User> selectList(UserArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<User> selectPage(UserArgs args, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    public boolean existsByOrgId(Integer orgId) {
        return PageHelper.offsetPage(0, 1, false).<Number>doSelectPage(() ->
                mapper.countByOrgId(orgId)).iterator().next().intValue() > 0;
    }

    public boolean existsByGroupId(Integer groupId) {
        return PageHelper.offsetPage(0, 1, false).<Number>doSelectPage(() ->
                mapper.countByGroupId(groupId)).iterator().next().intValue() > 0;
    }

    public boolean existsByRoleId(Integer roleId, Integer notOrgId) {
        return PageHelper.offsetPage(0, 1, false).<Number>doSelectPage(() ->
                mapper.countByRoleId(roleId, notOrgId)).iterator().next().intValue() > 0;
    }

    @Override
    public void preOrgDelete(Integer orgId) {
        if (existsByOrgId(orgId)) {
            throw new LogicException("error.refer.user");
        }
    }

    @Override
    public void preGroupDelete(Integer groupId) {
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