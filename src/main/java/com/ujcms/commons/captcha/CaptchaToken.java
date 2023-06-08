package com.ujcms.commons.captcha;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 图像验证码 Token
 *
 * @author PONY
 */
@Schema(description = "验证码Token")
public class CaptchaToken {
    public CaptchaToken() {
    }

    public CaptchaToken(String token, int expiresIn, String image) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.image = image;
    }

    @Schema(description = "TOKEN")
    private String token;
    @Schema(description = "有效期。单位：秒")
    private int expiresIn;
    @Schema(description = "图片Base64编码")
    private String image;

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

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
