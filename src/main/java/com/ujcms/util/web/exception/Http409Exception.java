package com.ujcms.util.web.exception;

import org.springframework.lang.Nullable;

/**
 * SC_CONFLICT 冲突 异常。如用户名相同。
 *
 * @author PONY
 */
public class Http409Exception extends MessagedException {
    public Http409Exception(String code, @Nullable String... args) {
        super(code, args);
    }
}