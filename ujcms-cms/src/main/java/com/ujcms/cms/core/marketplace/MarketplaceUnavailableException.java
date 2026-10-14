package com.ujcms.cms.core.marketplace;

import org.jspecify.annotations.Nullable;

/**
 * 应用市场当前不可达（网络故障 / 服务端 5xx / 每 IP 限流 429）。
 *
 * <p>商店宕机不等于控制台报错（kickoff C1 §2）：目录读取端点把本异常降级为可用性信封
 * （{@code available=false} + 原因），控制台据此呈现离线提示；安装路径则转译为
 * {@code marketplace.error.*} 的可诉 400（{@link MarketplaceException}）。
 */
public class MarketplaceUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 不可用原因（信封 {@code reason} 的取值来源） */
    public enum Reason {
        /** 网络不可达 / 超时 / 服务端错误 */
        OFFLINE("offline"),
        /** 每 IP 限流命中（429，携带 Retry-After，服务端 D-M3-4） */
        RATE_LIMITED("rate-limited");

        private final String value;

        Reason(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private final Reason reason;
    @Nullable
    private final Integer retryAfterSeconds;

    public MarketplaceUnavailableException(Reason reason, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.retryAfterSeconds = null;
    }

    public MarketplaceUnavailableException(Reason reason, String message, @Nullable Integer retryAfterSeconds) {
        super(message);
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Reason getReason() {
        return reason;
    }

    @Nullable
    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
