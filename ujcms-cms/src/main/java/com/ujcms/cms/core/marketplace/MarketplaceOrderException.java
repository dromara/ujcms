package com.ujcms.cms.core.marketplace;

import org.jspecify.annotations.Nullable;

/**
 * 实例订单面（{@code /api/v1/orders/**} / {@code /api/v1/payment-providers}，C4b / 市场 D-M14-2）的业务性拒绝，
 * 按 HTTP 状态与 problem+json {@code code} 归类为可判别的 {@link Kind}——购买服务据此给出可诉的
 * {@code marketplace.error.*} 文案或类型化结果（取消遭 503 → 「订单保留」），而不是笼统失败。
 * 401 / 403 {@code insufficient_scope} 仍走 {@link MarketplaceAuthException}（刷新重试 / 须重连）。
 */
public class MarketplaceOrderException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 拒绝类别 */
    public enum Kind {
        /** 404：条目未发布/不存在，或订单不存在/不属于令牌账号（服务端对外同一个 404） */
        NOT_FOUND,
        /** 409 {@code already_owned}：账号已拥有该条目（刷新授权即可安装） */
        ALREADY_OWNED,
        /** 422 {@code invariant_violation}：免费条目不可下单 / 订单已不在 created 状态（含刚被结算为 paid，D-M12-6） */
        INVARIANT_VIOLATION,
        /** 503 {@code payment_unavailable}：支付渠道不可用 / 取消时渠道无法确认关单（订单保留 created，D-M12-5） */
        PAYMENT_UNAVAILABLE,
        /** 400：未知支付方式等请求错误（detail 列出可用渠道） */
        BAD_REQUEST
    }

    private final Kind kind;
    @Nullable
    private final String code;

    public MarketplaceOrderException(Kind kind, @Nullable String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public Kind getKind() {
        return kind;
    }

    /** problem+json 的 {@code code}（如 {@code already_owned}），无法解析时为 {@code null} */
    @Nullable
    public String getCode() {
        return code;
    }

    public boolean is(Kind expected) {
        return kind == expected;
    }
}
