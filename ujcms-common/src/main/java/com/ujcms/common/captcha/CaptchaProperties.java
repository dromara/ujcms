package com.ujcms.common.captcha;

import com.ujcms.common.security.Secures;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图形验证码属性
 *
 * @author PONY
 */
@ConfigurationProperties(prefix = "captcha")
public class CaptchaProperties {
    /**
     * 加密密钥。默认生成一个32位随机字符串。集群时需要指定一个固定的密钥。
     */
    private String secret = Secures.randomAlphanumeric(32);
    private String issuer = "ujcms/captcha";
    private long leeway = 3 * 60;
    /**
     * 过期时间。单位：分钟。默认15分钟。
     */
    private int expires = 15;
    /**
     * 最大缓存数量。默认 100,000
     */
    private long maximumSize = 100_000;
    /**
     * 最大可尝试次数
     */
    private int maxAttempts = 5;
    /**
     * 图片宽
     */
    private int width = 120;
    /**
     * 图片高
     */
    private int height = 36;
    /**
     * 字高（逻辑像素）。整词放不进画布时按比例缩小
     */
    private int fontSize = 30;
    /**
     * 字符数量
     */
    private int wordLength = 5;
    /**
     * 字符扭曲程度
     */
    private float radius = 20;
    /**
     * 像素密度倍数。实际图片尺寸为 width、height 乘以该倍数，高分屏显示更清晰；页面以 CSS 固定显示尺寸。默认 2
     */
    private int scale = 2;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getLeeway() {
        return leeway;
    }

    public void setLeeway(long leeway) {
        this.leeway = leeway;
    }

    public int getExpires() {
        return expires;
    }

    public void setExpires(int expires) {
        this.expires = expires;
    }

    public long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
        this.maximumSize = maximumSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public int getWordLength() {
        return wordLength;
    }

    public void setWordLength(int wordLength) {
        this.wordLength = wordLength;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }
}
