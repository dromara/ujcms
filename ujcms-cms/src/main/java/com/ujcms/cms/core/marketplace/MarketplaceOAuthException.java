package com.ujcms.cms.core.marketplace;

import org.jspecify.annotations.Nullable;

/**
 * 应用市场 OAuth 端点的 RFC 6749/8628 错误应答（{@code {"error": …}}，非 problem+json）。
 * 设备授权流轮询语义即错误码：{@code authorization_pending}（继续轮询）、{@code slow_down}
 * （轮询间隔 +5 秒，RFC 8628 §3.5）、{@code access_denied}（用户拒绝）、{@code expired_token}
 * （设备码过期）；刷新流的 {@code invalid_grant} 表示 refresh token 已作废（吊销 / 轮换竞态），
 * 连接须重建。
 */
public class MarketplaceOAuthException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public static final String AUTHORIZATION_PENDING = "authorization_pending";
    public static final String SLOW_DOWN = "slow_down";
    public static final String ACCESS_DENIED = "access_denied";
    public static final String EXPIRED_TOKEN = "expired_token";
    public static final String INVALID_GRANT = "invalid_grant";

    private final String error;

    public MarketplaceOAuthException(String error, @Nullable String description) {
        super(error + (description == null ? "" : ": " + description));
        this.error = error;
    }

    /** RFC 错误码（{@code error} 字段原值） */
    public String getError() {
        return error;
    }

    public boolean is(String code) {
        return error.equals(code);
    }
}
