package com.ujcms.commons.security;

import org.springframework.security.authentication.InsufficientAuthenticationException;

/**
 * 验证码错误异常
 *
 * @author PONY
 */
public class CaptchaIncorrectException extends InsufficientAuthenticationException {
    private static final long serialVersionUID = 5407938256648819329L;

    public CaptchaIncorrectException(String msg) {
        super(msg);
    }
}
