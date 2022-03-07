package com.ofwise.util.web.exception;

import org.springframework.lang.Nullable;

/**
 * @author PONY
 */
public class MessagedException extends RuntimeException {
    @Nullable
    private String[] args;

    public MessagedException(String code, @Nullable String... args) {
        super(code);
        this.args = args;
    }

    @Nullable
    public String[] getArgs() {
        if (args == null) {
            return null;
        }
        return args.clone();
    }
}
