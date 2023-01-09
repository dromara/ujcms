package com.ujcms.util.web.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * 未登录异常
 *
 * @author PONY
 */
public class Http401Exception extends AuthenticationException {
    private static final long serialVersionUID = -1325093172977830437L;

    public Http401Exception() {
        super("Unauthorized");
    }

    public Http401Exception(String message) {
        super(message);
    }
}
