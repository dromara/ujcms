package com.ujcms.util.web.exception;

import org.springframework.lang.Nullable;

/**
 * 请求错误异常
 *
 * @author PONY
 */
public class Http400Exception extends AbstractMessagedException {
    private static final long serialVersionUID = -815907008054999080L;

    public Http400Exception(String code, @Nullable String... args) {
        super(code, args);
    }
}
