package com.ujcms.common.sms;

import org.jspecify.annotations.Nullable;

/**
 * 短信发送错误。
 * <p>
 * 服务商的原始错误码和错误信息只用于日志和后台（管理员）诊断；面向终端用户的接口应根据 {@link #kind()} 给出友好提示，
 * 不要直接输出原始信息（可能泄露余额不足、密钥无效、签名或模板名称等内部情况）。
 *
 * @param kind     错误分类
 * @param provider 错误来源的短信平台（服务商）标识，如 {@code aliyun}、{@code tencentcloud}。对应消息键 {@code smsProvider.<provider>}
 * @param code     服务商原始错误码。非服务商返回的错误（如网络异常）为 {@code null}
 * @param message  服务商原始错误信息
 * @author PONY
 */
public record SmsError(Kind kind, String provider, @Nullable String code, String message) {
    /**
     * 错误分类
     */
    public enum Kind {
        /**
         * 触发服务商的发送频率限制（同一号码每分钟、每小时、每天的发送条数）
         */
        RATE_LIMITED,
        /**
         * 手机号码不正确
         */
        INVALID_MOBILE,
        /**
         * 其它错误（配置错误、余额不足、网络异常等）
         */
        OTHER
    }

    public static SmsError of(Kind kind, String provider, @Nullable String code, @Nullable String message) {
        return new SmsError(kind, provider, code, message != null ? message : "Unknown error");
    }

    public static SmsError other(String provider, String message) {
        return new SmsError(Kind.OTHER, provider, null, message);
    }

    /**
     * 服务商的原始错误信息（不含来源），用于后台诊断
     */
    public String detail() {
        return code != null ? "code: " + code + ", message: " + message : message;
    }

    /**
     * 带来源的原始错误信息，用于日志
     */
    @Override
    public String toString() {
        return "provider: " + provider + ", " + detail();
    }
}
