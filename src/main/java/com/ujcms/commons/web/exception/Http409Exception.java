package com.ujcms.commons.web.exception;

import org.springframework.lang.Nullable;

/**
 * SC_CONFLICT 冲突 异常。如用户名相同。
 *
 * @author PONY
 * @see javax.servlet.http.HttpServletResponse#SC_CONFLICT
 */
public class Http409Exception extends AbstractMessagedException {
    private static final long serialVersionUID = 7393692827921653383L;

    public Http409Exception(String code, @Nullable String... args) {
        super(code, args);
    }
}