package com.ujcms.core.security;

import com.ujcms.core.domain.User;
import com.ujcms.core.service.UserService;
import com.ofwise.util.security.CredentialsDigest;
import com.ofwise.util.security.CredentialsMatcherAdapter;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.ExcessiveAttemptsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;

import java.util.HashSet;

/**
 * Shiro 数据库认证类
 * @author PONY
 */
public class DbRealm extends AuthorizingRealm {
    public DbRealm(CredentialsDigest credentialsDigest, UserService userService) {
        this.userService = userService;
        // 设定Password校验的Hash算法与迭代次数.
        setCredentialsMatcher(new CredentialsMatcherAdapter(credentialsDigest));
    }

    /**
     * 认证回调函数,登录时调用.
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authcToken) throws AuthenticationException {
        UsernamePasswordToken token = (UsernamePasswordToken) authcToken;
        User user = userService.selectByUsername(token.getUsername());
        // 前后台登录共用，非管理员也可登录。
        if (user != null) {
            if (user.isExcessiveAttempts()) {
                // 是否尝试过多要放在第一位，否则由于登录次数过多的用户状态正常，导致登录成功
                throw new ExcessiveAttemptsException();
            } else if (user.isLocked()) {
                throw new LockedAccountException();
            } else if (user.isDisabled()) {
                throw new DisabledAccountException();
            }
            return new SimpleAuthenticationInfo(user.getId(), user.getPassword(), ByteSource.Util.bytes(user.getSalt()), getName());
        }
        return null;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        SimpleAuthorizationInfo auth = new SimpleAuthorizationInfo();
        Integer userId = (Integer) principals.getPrimaryPrincipal();
        User user = userService.select(userId);
        if (user != null) {
            auth.setStringPermissions(new HashSet<>(user.getPermissions()));
        }
        return auth;
    }

    private UserService userService;
}
