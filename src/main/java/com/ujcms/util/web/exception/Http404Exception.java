package com.ujcms.util.web.exception;

import org.springframework.lang.Nullable;

/**
 * 找不到资源异常
 *
 * @author PONY
 */
public class Http404Exception extends AbstractMessagedException {
    private static final long serialVersionUID = 4838098260767633127L;

    public Http404Exception(String code, @Nullable String... args) {
        super(code, args);
    }
}