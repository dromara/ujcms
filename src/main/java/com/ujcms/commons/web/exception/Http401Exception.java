package com.ujcms.commons.web.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * SC_UNAUTHORIZED 未登录 异常
 *
 * @author PONY
 * @see javax.servlet.http.HttpServletResponse#SC_UNAUTHORIZED
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
