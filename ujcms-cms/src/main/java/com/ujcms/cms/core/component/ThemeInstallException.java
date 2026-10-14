package com.ujcms.cms.core.component;

import com.ujcms.common.web.exception.Http400Exception;

/**
 * Thrown when an uploaded theme ZIP fails any of the {@link ThemeInstallValidator} fail-fast
 * rules, or when the {@link com.ujcms.cms.core.service.ThemeInstallService} install / uninstall
 * / activate flow rejects the request (e.g. theme name collision, theme in use).
 *
 * <p>Extends {@link Http400Exception} so that the existing
 * {@link com.ujcms.cms.core.web.support.ExceptionResolver} translates it into a 400 response
 * with the i18n {@code code} as the message body, matching the pattern used by the rest of the
 * admin API.
 */
public class ThemeInstallException extends Http400Exception {
    private static final long serialVersionUID = 1L;

    private final String detail;

    public ThemeInstallException(String code, String detail) {
        super(code);
        this.detail = detail;
    }

    /**
     * 带 i18n 占位参数的构造：{@code args} 经 {@code MessagedException.getArgs()} 送入
     * {@code MessageSource}，使 messages.properties 中的 {@code {0}}/{@code {1}} 得以替换
     * （如版本门槛需在报错里点名「要求版本 vs 当前版本」）。
     */
    public ThemeInstallException(String code, String detail, String... args) {
        super(code, args);
        this.detail = detail;
    }

    public ThemeInstallException(String code, String detail, Throwable cause) {
        super(code);
        this.detail = detail;
        if (cause != null) {
            initCause(cause);
        }
    }

    /** Human-readable English fallback (the i18n {@code code} is in {@link #getMessage()}). */
    public String getDetail() {
        return detail;
    }
}
