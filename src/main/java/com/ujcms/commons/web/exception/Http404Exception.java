package com.ujcms.commons.web.exception;

import org.springframework.lang.Nullable;

/**
 * SC_NOT_FOUND 找不到资源异常
 *
 * @author PONY
 * @see javax.servlet.http.HttpServletResponse#SC_NOT_FOUND
 */
public class Http404Exception extends AbstractMessagedException {
    private static final long serialVersionUID = 4838098260767633127L;

    public Http404Exception(String code, @Nullable String... args) {
        super(code, args);
    }
}