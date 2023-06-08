package com.ujcms.commons.security;

import org.springframework.security.core.AuthenticationException;

/**
 * IP登录尝试过多异常
 *
 * @author PONY
 */
public class IpExcessiveAttemptsException extends AuthenticationException {
    private static final long serialVersionUID = 9101844650019280920L;

    public IpExcessiveAttemptsException(String msg) {
        super(msg);
    }
}
