package com.ujcms.common.captcha;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一道验证码题目：签进令牌的声明，与发给浏览器的载荷。
 * <p>
 * 声明随令牌（签名但不加密）发给浏览器，所以答案在这里只能以哈希或密文出现；载荷是浏览器渲染题目所需的公开内容
 * （图片、盐值、厂商的公开标识等），作为 {@link CaptchaToken} 的附加字段原样输出。
 *
 * @param claims  令牌声明。键不得与 {@link CaptchaTokenService} 的保留声明冲突
 * @param payload 浏览器载荷
 * @author PONY
 */
public record CaptchaChallenge(Map<String, Object> claims, Map<String, Object> payload) {
    public CaptchaChallenge {
        claims = Collections.unmodifiableMap(new LinkedHashMap<>(claims));
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /**
     * 没有声明、只有载荷的题目（第三方厂商：答案由厂商接口校验）
     */
    public static CaptchaChallenge ofPayload(Map<String, Object> payload) {
        return new CaptchaChallenge(Map.of(), payload);
    }
}
