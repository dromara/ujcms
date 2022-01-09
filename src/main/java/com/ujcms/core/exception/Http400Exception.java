package com.ujcms.core.exception;

import org.springframework.lang.Nullable;

/**
 * 请求错误异常
 *
 * @author PONY
 */
public class Http400Exception extends MessagedException {
    public Http400Exception(String code, @Nullable String... args) {
        super(code, args);
    }
}
