package com.ofwise.util.web.exception;

import org.springframework.lang.Nullable;

/**
 * 找不到资源异常
 *
 * @author PONY
 */
public class Http404Exception extends MessagedException {
    public Http404Exception(String code, @Nullable String... args) {
        super(code, args);
    }
}