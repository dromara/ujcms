package com.ujcms.cms.core.domain;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ujcms.cms.core.domain.generated.GeneratedShortMessage;
import com.ujcms.cms.core.security.PiiNormalizer;

/**
 * 短信实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ShortMessage extends GeneratedShortMessage {

    public ShortMessage() {
    }

    public ShortMessage(Short type, String receiver, String code, String ip, Short usage) {
        setType(type);
        setReceiver(receiver);
        setCode(code);
        setIp(ip);
        setUsage(usage);
    }

    /**
     * 验证码本身不输出到 JSON，改由 {@link #getDisplayCode()} 按状态决定是否输出。
     */
    @JsonIgnore
    @Override
    public String getCode() {
        return super.getCode();
    }

    /**
     * 供后台查看的验证码：仅当验证码已不可能再通过校验时才输出（{@code @JsonInclude(NON_NULL)} 下为 {@code null} 即不输出）。
     * <p>
     * 仍然有效的验证码就是一枚可用的登录第二因子。短信记录属于普通的日志查看权限，若原样回显，
     * 有此权限的操作员即可读到任何人（包括超级管理员）正在使用的验证码。
     * 已使用（验证正确、过期、尝试次数过多）或已超过最长有效期的验证码无法再用于校验，输出以便排查问题。
     */
    @Nullable
    @JsonProperty("code")
    public String getDisplayCode() {
        return isUsed() || isExpired(MAX_EXPIRES_MINUTES) ? getCode() : null;
    }

    /**
     * 是否过期
     *
     * @param expires 过期时间（分钟）
     */
    public boolean isExpired(int expires) {
        return Duration.between(getSendDate(), OffsetDateTime.now()).toMinutes() > expires;
    }

    /**
     * 是否错误
     *
     * @param receiver 接收者（手机号码或邮箱地址）
     * @param code     验证码
     * @return 是否错误
     */
    public boolean isWrong(@Nullable String receiver, @Nullable String code) {
        return !isSameReceiver(receiver) || !getCode().equalsIgnoreCase(code);
    }

    /**
     * 接收者是否一致。两侧都先规范化再比较：用户库中的手机号码可能带国家码分隔符（如 {@code +86 138-0013-8000}），
     * 与盲索引使用同一套 {@link PiiNormalizer} 规则，保证“能按号码查到用户”与“能通过验证码校验”结论一致。
     */
    public boolean isSameReceiver(@Nullable String receiver) {
        if (getType() == TYPE_MOBILE) {
            String expected = PiiNormalizer.normalizeMobile(getReceiver());
            return expected != null && expected.equals(PiiNormalizer.normalizeMobile(receiver));
        }
        String expected = PiiNormalizer.normalizeEmail(getReceiver());
        return expected != null && expected.equals(PiiNormalizer.normalizeEmail(receiver));
    }

    /**
     * 是否使用
     */
    public boolean isUsed() {
        return getStatus() != STATUS_UNUSED;
    }

    /**
     * 允许的最大尝试次数
     */
    public static final int MAX_ATTEMPTS = 3;
    /**
     * 验证码有效期的上限（分钟）。与 {@code Config.Sms} / {@code Config.Email} 中 {@code codeExpires} 的
     * {@code @Max(30)} 一致：超过该时长的验证码在任何配置下都已过期。
     */
    public static final int MAX_EXPIRES_MINUTES = 30;
    /**
     * 类型：手机短信
     */
    public static final short TYPE_MOBILE = 1;
    /**
     * 类型：邮件短信
     */
    public static final short TYPE_EMAIL = 2;

    /**
     * 用途：测试
     */
    public static final short USAGE_TEST = 0;
    /**
     * 用途：注册
     */
    public static final short USAGE_REGISTER = 1;
    /**
     * 用途：登录
     */
    public static final short USAGE_LOGIN = 2;
    /**
     * 用途：双因子登录
     */
    public static final short USAGE_MFA_LOGIN = 3;
    /**
     * 用途：重置密码
     */
    public static final short USAGE_RESET_PASSWORD = 4;
    /**
     * 用途：修改手机号码
     */
    public static final short USAGE_MODIFY_MOBILE = 5;
    /**
     * 用途：修改邮箱地址
     */
    public static final short USAGE_MODIFY_EMAIL = 6;

    /**
     * 状态：未使用
     */
    public static final short STATUS_UNUSED = 0;
    /**
     * 状态：验证正确
     */
    public static final short STATUS_CORRECT = 1;
    /**
     * 状态：验证错误
     */
    public static final short STATUS_WRONG = 2;
    /**
     * 状态：已过期
     */
    public static final short STATUS_EXPIRED = 3;
    /**
     * 状态：尝试次数过多
     */
    public static final short STATUS_EXCEEDED = 4;

}