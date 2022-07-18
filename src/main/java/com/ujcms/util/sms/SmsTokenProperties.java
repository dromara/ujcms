package com.ujcms.util.sms;

import com.ujcms.util.security.Secures;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 短信验证码属性
 *
 * @author PONY
 */
@ConfigurationProperties(prefix = "sms")
public class SmsTokenProperties {
    /**
     * 加密密钥。默认生成一个32位随机字符串。集群时需要指定一个固定的密钥。
     */
    private String secret = Secures.randomAlphanumeric(32);
    private String issuer = "ujcms/sms";
    private long leeway = 3 * 60;
    /**
     * 过期时间。单位：分钟。默认30分钟。
     */
    private int expires = 30;
    /**
     * 最大缓存数量。默认 100,000
     */
    private long maximumSize = 100_000;

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
}
