package com.ofwise.util.web.exception;

/**
 * 未登录异常
 *
 * @author PONY
 */
public class Http401Exception extends RuntimeException {
    public Http401Exception() {
        super();
    }

    public Http401Exception(String message) {
        super(message);
    }
}
