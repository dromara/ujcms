package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.base.LoginLogBase;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.LoginLogMapper;
import com.ujcms.cms.core.service.args.LoginLogArgs;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 登录日志 Service（商业版）
 *
 * @author PONY
 */
@Service
public class LoginLogService implements UserDeleteListener {
    private final LoginLogMapper mapper;
    private final SeqService seqService;

    public LoginLogService(LoginLogMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void loginFailure(String loginName, String ip, short status) {
        insert(LoginLog.ofLoginFailure(null, loginName, ip, status));
    }

    @Transactional(rollbackFor = Exception.class)
    public void loginFailure(Integer userId, String loginName, String ip, short status) {
        insert(LoginLog.ofLoginFailure(userId, loginName, ip, status));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePasswordFailure(Integer userId, String ip, short status) {
        insert(LoginLog.ofChangePasswordFailure(userId, ip, status));
    }

    @Transactional(rollbackFor = Exception.class)
    public void loginSuccess(Integer userId, String loginName, String ip) {
        insert(LoginLog.ofLoginSuccess(userId, loginName, ip));
    }

    @Transactional(rollbackFor = Exception.class)
    public void logout(Integer userId, String ip) {
        insert(LoginLog.ofLogout(userId, ip));
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(LoginLog bean) {
        bean.setId(seqService.getNextVal(LoginLogBase.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(LoginLog bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(int id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public LoginLog select(int id) {
        return mapper.select(id);
    }

    public List<LoginLog> selectList(LoginLogArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), LoginLogBase.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<LoginLog> selectList(LoginLogArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<LoginLog> selectPage(LoginLogArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    @Override
    public void preUserDelete(Integer userId) {
        mapper.deleteByUserId(userId);
    }
}