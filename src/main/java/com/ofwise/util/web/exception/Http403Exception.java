package com.ofwise.util.web.exception;

import org.springframework.lang.Nullable;

/**
 * 无权限异常
 *
 * @author PONY
 */
public class Http403Exception extends MessagedException {
    public Http403Exception(String code, @Nullable String... args) {
        super(code, args);
    }
}
