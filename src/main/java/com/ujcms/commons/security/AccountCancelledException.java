package com.ujcms.commons.security;

import org.springframework.security.authentication.InternalAuthenticationServiceException;

/**
 * 用户已注销异常
 * <p>
 * 必须继承 InternalAuthenticationServiceException，不能继承 AccountStatusException。
 * 否则 DaoAuthenticationProvider#retrieveUser 方法将会对异常重新包装为 InternalAuthenticationServiceException
 *
 * @author PONY
 */
public class AccountCancelledException extends InternalAuthenticationServiceException {
    private static final long serialVersionUID = 2511451094987231874L;

    public AccountCancelledException(String msg) {
        super(msg);
    }

    public AccountCancelledException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
