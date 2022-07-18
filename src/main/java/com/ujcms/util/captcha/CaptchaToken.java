package com.ujcms.util.captcha;

/**
 * 图像验证码 Token
 *
 * @author PONY
 */
public class CaptchaToken {
    public CaptchaToken() {
    }

    public CaptchaToken(String token, int expiresIn, String image) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.image = image;
    }

    private String token;
    private int expiresIn;
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
