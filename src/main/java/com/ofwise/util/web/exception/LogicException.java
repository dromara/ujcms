package com.ofwise.util.web.exception;

import org.springframework.lang.Nullable;

/**
 * @author PONY
 */
public class LogicException extends MessagedException {
    public LogicException(String code, @Nullable String... args) {
        super(code, args);
    }
}
