package com.ujcms.commons.security;

import org.springframework.security.authentication.InternalAuthenticationServiceException;

/**
 * 用户未激活异常
 * <p>
 * 必须继承 InternalAuthenticationServiceException，不能继承 AccountStatusException。
 * 否则 DaoAuthenticationProvider#retrieveUser 方法将会对异常重新包装为 InternalAuthenticationServiceException
 *
 * @author PONY
 */
public class AccountUnactivatedException extends InternalAuthenticationServiceException {
    private static final long serialVersionUID = -362401064439331410L;

    public AccountUnactivatedException(String msg) {
        super(msg);
    }

    public AccountUnactivatedException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
