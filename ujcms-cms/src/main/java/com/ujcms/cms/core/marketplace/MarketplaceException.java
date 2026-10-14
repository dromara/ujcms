package com.ujcms.cms.core.marketplace;

import com.ujcms.common.web.exception.Http400Exception;

/**
 * 应用市场操作被拒或失败（面向操作员的可诉错误）。经既有
 * {@link com.ujcms.cms.core.web.support.ExceptionResolver} 解析为 400 响应，
 * {@code code} 即 messages.properties 的 i18n 键（{@code marketplace.error.*}），
 * 与 {@link com.ujcms.cms.core.component.ThemeInstallException} 同一模式。
 */
public class MarketplaceException extends Http400Exception {
    private static final long serialVersionUID = 1L;

    private final String detail;

    public MarketplaceException(String code, String detail) {
        super(code);
        this.detail = detail;
    }

    public MarketplaceException(String code, String detail, Throwable cause) {
        super(code);
        this.detail = detail;
        initCause(cause);
    }

    /** Human-readable English fallback (the i18n {@code code} is in {@link #getMessage()}). */
    public String getDetail() {
        return detail;
    }
}
