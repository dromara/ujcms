package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.base.LoginLogBase;
import org.springframework.lang.Nullable;

import java.io.Serializable;

/**
 * 登录日志实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class LoginLog extends LoginLogBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public static LoginLog ofLoginFailure(@Nullable Long userId, String loginName, String ip, short status) {
        LoginLog bean = new LoginLog();
        bean.setUserId(userId);
        bean.setLoginName(loginName);
        bean.setIp(ip);
        bean.setType(LoginLog.TYPE_LOGIN);
        bean.setStatus(status);
        return bean;
    }

    public static LoginLog ofChangePasswordFailure(Long userId, String ip, short status) {
        LoginLog bean = new LoginLog();
        bean.setUserId(userId);
        bean.setIp(ip);
        bean.setType(LoginLog.TYPE_CHANGE_PASSWORD);
        bean.setStatus(status);
        return bean;
    }

    public static LoginLog ofLoginSuccess(@Nullable Long userId, String loginName, String ip) {
        LoginLog bean = new LoginLog();
        bean.setUserId(userId);
        bean.setLoginName(loginName);
        bean.setIp(ip);
        bean.setType(LoginLog.TYPE_LOGIN);
        bean.setStatus(STATUS_SUCCESS);
        return bean;
    }

    public static LoginLog ofLogout(Long userId, String ip) {
        LoginLog bean = new LoginLog();
        bean.setUserId(userId);
        bean.setIp(ip);
        bean.setType(LoginLog.TYPE_LOGOUT);
        bean.setStatus(LoginLog.STATUS_SUCCESS);
        return bean;
    }

    @JsonIncludeProperties({"id", "username", "nickname"})
    @Nullable
    private User user;

    @Nullable
    public User getUser() {
        return user;
    }

    public void setUser(@Nullable User user) {
        this.user = user;
    }

    /**
     * 类型：登录
     */
    public static final short TYPE_LOGIN = 1;
    /**
     * 类型：修改密码
     */
    public static final short TYPE_CHANGE_PASSWORD = 2;
    /**
     * 类型：退出
     */
    public static final short TYPE_LOGOUT = 9;

    /**
     * 状态：成功
     */
    public static final short STATUS_SUCCESS = 0;
    /**
     * 状态：用户名不存在
     */
    public static final short STATUS_LOGIN_NAME_NOT_FOUND = 1;
    /**
     * 状态：密码错误
     */
    public static final short STATUS_PASSWORD_WRONG = 2;
    /**
     * 状态：验证码错误
     */
    public static final short STATUS_CAPTCHA_WRONG = 3;
    /**
     * 状态：短信错误
     */
    public static final short STATUS_SHORT_MESSAGE_WRONG = 4;
    /**
     * 状态：IP超过尝试次数
     */
    public static final short STATUS_IP_EXCESSIVE_ATTEMPTS = 11;
    /**
     * 状态：用户超过尝试次数
     */
    public static final short STATUS_USER_EXCESSIVE_ATTEMPTS = 12;
    /**
     * 状态：密码已过期
     */
    public static final short STATUS_PASSWORD_EXPIRED = 13;
    /**
     * 状态：用户未激活
     */
    public static final short STATUS_USER_INACTIVATED = 14;
    /**
     * 状态：用户已锁定
     */
    public static final short STATUS_USER_LOCKED = 15;
    /**
     * 状态：用户已注销
     */
    public static final short STATUS_USER_CANCELLED = 16;
    /**
     * 状态：用户已禁用
     */
    public static final short STATUS_USER_DISABLED = 17;


}