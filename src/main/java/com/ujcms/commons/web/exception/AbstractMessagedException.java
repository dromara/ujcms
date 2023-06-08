package com.ujcms.commons.web.exception;

import org.springframework.lang.Nullable;

/**
 * @author PONY
 */
public class AbstractMessagedException extends RuntimeException implements MessagedException {
    private static final long serialVersionUID = 1579261230046148492L;

    public AbstractMessagedException(String code, @Nullable String... args) {
        super(code);
        this.args = args;
    }

    @Nullable
    @Override
    public String getCode() {
        return getMessage();
    }

    @Nullable
    @Override
    public String[] getArgs() {
        return args;
    }

    @Nullable
    private final String[] args;
}
