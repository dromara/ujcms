package com.ujcms.commons.web.exception;

import org.springframework.lang.Nullable;

/**
 * SC_BAD_REQUEST 请求错误 异常
 *
 * @author PONY
 * @see javax.servlet.http.HttpServletResponse#SC_BAD_REQUEST
 */
public class Http400Exception extends AbstractMessagedException {
    private static final long serialVersionUID = -815907008054999080L;

    public Http400Exception(String code, @Nullable String... args) {
        super(code, args);
    }
}
