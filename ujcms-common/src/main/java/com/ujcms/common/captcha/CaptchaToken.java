package com.ujcms.common.captcha;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 验证码题目：类型、令牌、有效期，以及该类型的浏览器载荷（平铺为附加字段）。
 * <p>
 * 载荷按类型不同：{@code char} 有 {@code image}；{@code slider} 有 {@code image}、{@code piece}、{@code width}、
 * {@code height}、{@code pieceWidth}、{@code pieceHeight}、{@code y}；{@code pow} 有 {@code salt}、{@code difficulty}；
 * 第三方厂商为其公开标识（{@code captchaId}、{@code appId}、{@code siteKey} 等）。契约见 docs/spec/captcha/plan.md。
 *
 * @author PONY
 */
@Schema(description = "验证码题目")
public class CaptchaToken {
    public CaptchaToken() {
    }

    public CaptchaToken(String type, String token, int expiresIn, Map<String, Object> payload) {
        this.type = type;
        this.token = token;
        this.expiresIn = expiresIn;
        this.payload.putAll(payload);
    }

    @Schema(description = "类型：slider | char | pow | 厂商代码")
    private String type = "";
    @Schema(description = "TOKEN。随答案一并提交（captchaToken）")
    private String token = "";
    @Schema(description = "有效期。单位：秒")
    private int expiresIn;
    private final Map<String, Object> payload = new LinkedHashMap<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }

    /**
     * 该类型的浏览器载荷，序列化时平铺到顶层
     */
    @JsonAnyGetter
    public Map<String, Object> getPayload() {
        return payload;
    }

    @JsonAnySetter
    public void putPayload(String name, Object value) {
        payload.put(name, value);
    }
}
