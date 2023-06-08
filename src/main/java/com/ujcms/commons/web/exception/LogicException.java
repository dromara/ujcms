package com.ujcms.commons.web.exception;

import org.springframework.lang.Nullable;

/**
 * @author PONY
 */
public class LogicException extends AbstractMessagedException {
    private static final long serialVersionUID = 153270552617788233L;

    public LogicException(String code, @Nullable String... args) {
        super(code, args);
    }
}
