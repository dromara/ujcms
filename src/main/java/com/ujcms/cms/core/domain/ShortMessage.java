package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.ShortMessageBase;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 短信实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ShortMessage extends ShortMessageBase implements Serializable {
    private static final long serialVersionUID = 1L;

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
        return !getReceiver().equalsIgnoreCase(receiver) || !getCode().equalsIgnoreCase(code);
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