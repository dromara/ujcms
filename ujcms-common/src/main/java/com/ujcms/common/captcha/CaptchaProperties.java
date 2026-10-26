package com.ujcms.common.captcha;

import com.ujcms.common.security.Secures;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 验证码属性
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
     * 字符验证码图片宽
     */
    private int width = 120;
    /**
     * 字符验证码图片高
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
    /**
     * 滑块拼图背景逻辑宽。默认 300
     */
    private int sliderWidth = 300;
    /**
     * 滑块拼图背景逻辑高。默认 150
     */
    private int sliderHeight = 150;
    /**
     * 滑块拼图主体边长（逻辑像素）。默认 50
     */
    private int sliderPieceSize = 50;
    /**
     * 滑块拼图允许误差（逻辑像素）。默认 5
     */
    private int sliderTolerance = 5;
    /**
     * 工作量证明难度（前导零比特数，10-24）的默认值。全局设置可覆盖。默认 18
     */
    private int powDifficulty = 18;

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

    public int getSliderWidth() {
        return sliderWidth;
    }

    public void setSliderWidth(int sliderWidth) {
        this.sliderWidth = sliderWidth;
    }

    public int getSliderHeight() {
        return sliderHeight;
    }

    public void setSliderHeight(int sliderHeight) {
        this.sliderHeight = sliderHeight;
    }

    public int getSliderPieceSize() {
        return sliderPieceSize;
    }

    public void setSliderPieceSize(int sliderPieceSize) {
        this.sliderPieceSize = sliderPieceSize;
    }

    public int getSliderTolerance() {
        return sliderTolerance;
    }

    public void setSliderTolerance(int sliderTolerance) {
        this.sliderTolerance = sliderTolerance;
    }

    public int getPowDifficulty() {
        return powDifficulty;
    }

    public void setPowDifficulty(int powDifficulty) {
        this.powDifficulty = powDifficulty;
    }
}
