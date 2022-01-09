package com.ofwise.util.security.jwt;

import com.ofwise.util.security.Secures;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 介绍：<a href="https://jwt.io/introduction/">https://jwt.io/introduction/</a>
 * <p>
 * JWT 规范：<a href="https://tools.ietf.org/html/rfc7519">https://tools.ietf.org/html/rfc7519</a>
 *
 * @author PONY
 */
@ConfigurationProperties("jwt")
public class JwtProperties {
    /**
     * 用于返回客户端的名称
     */
    public static final String ACCESS_TOKEN = "accessToken";
    public static final String REFRESH_TOKEN = "refreshToken";
    public static final String EXPIRES_IN = "expiresIn";
    public static final String REFRESH_EXPIRES_IN = "refreshExpiresIn";
    public static final String REFRESH_AUTH_EXPIRES_IN = "refreshAuthExpiresIn";
    public static final String REMEMBERED = "remembered";
    public static final String SESSION_TIMEOUT = "sessionTimeout";
    /**
     * 使用 header(头信息) 传递 Access Token 的名称
     * <p>
     * 根据 <a href="https://jwt.io/introduction/">https://jwt.io/introduction/</a> 介绍，JWT HEADER 的值为 {@code Authorization}。
     * <p>
     * Whenever the user wants to access a protected route or resource, the user agent should send the JWT, typically in the Authorization header using the Bearer schema.
     * <p>
     * {@code Authorization: Bearer <token>}
     */
    private static final String JWT_HEADER_NAME = "Authorization";
    /**
     * 使用 header(头信息) 传递 Access Token 使用的 schema
     * <p>
     * 根据 <a href="https://jwt.io/introduction/">https://jwt.io/introduction/</a> 介绍， Authorization header 使用 Bearer schema。
     * <p>
     * Whenever the user wants to access a protected route or resource, the user agent should send the JWT, typically in the Authorization header using the Bearer schema.
     * <p>
     * {@code Authorization: Bearer <token>}
     */
    private static final String JWT_HEADER_SCHEMA = "Bearer ";
    /**
     * 使用 parameter(参数) 传递 Access Token 的名称
     */
    private static final String JWT_PARAMETER_NAME = "access_token";
    /**
     * JWT 秘钥。应设置一个32位随机秘钥，且不能泄露。如没有设置，系统启动时会自动生成一个随机秘钥，但在集群时会因为不同服务器秘钥不同而出现认证问题。
     */
    private String secret = Secures.randomAlphanumeric(32);
    /**
     * URL 参数名称
     */
    private String parameterName = JWT_PARAMETER_NAME;
    /**
     * Access Token 过期时间。默认为 30 。单位：分钟。
     * <p>
     * 模仿浏览器 Session 过期时间。一般每 5-10 分钟使用 Refresh Token 重新获取 Access Token。
     */
    private int expires = 30;
    /**
     * Refresh Token 过期时间。默认 365 。单位：天。
     * <p>
     * 在有效期的最后30天，当用户重开APP，应主动让用户重新登录，以免在使用过程中突然过期。
     */
    private int refreshExpires = 365;
    /**
     * Refresh Token 认证过期时间。默认 30。单位：天。
     * <p>
     * 超过认证有效期，Refresh Token 只能获取 rememberMe 权限的 Access Token。
     */
    private int refreshAuthExpires = 30;
    /**
     * JWT 允许的时间误差。默认 180 。单位：秒。不同服务器的时间可能出现一些偏差，在该误差范围内认为是合理的。一般误差在3分钟内。
     */
    private long leeway = 3 * 60;
    /**
     * 签发人
     */
    private String issuer = "ujcms";
    /**
     * 用于后台自动刷新。超过该时间后，则不再刷新，实现类似 session 过期时间的效果。单位：分钟。
     */
    private int sessionTimeout = 30;

    /**
     * @return expires * 60
     */
    public int getExpiresSeconds() {
        return expires * 60;
    }

    /**
     * @return expires * 60 * 1000
     */
    public long getExpiresMillis() {
        return (long) expires * 60 * 1000;
    }

    /**
     * @return refreshExpires * 24 * 60 * 60
     */
    public long getRefreshExpiresSeconds() {
        return (long) refreshExpires * 24 * 60 * 60;
    }

    /**
     * @return refreshExpires * 24 * 60 * 60 * 1000
     */
    public long getRefreshExpiresMillis() {
        return (long) refreshExpires * 24 * 60 * 60 * 1000;
    }

    /**
     * @return refreshAuthExpires * 60 * 60
     */
    public long getRefreshAuthExpiresSeconds() {
        return (long) refreshAuthExpires * 24 * 60 * 60;
    }

    /**
     * @return refreshAuthExpires * 60 * 60 * 1000
     */
    public long getRefreshAuthExpiresMillis() {
        return (long) refreshAuthExpires * 24 * 60 * 60 * 1000;
    }

    public String getAccessTokenIssuer() {
        return issuer + "/access_token";
    }

    public String getRefreshTokenIssuer() {
        return issuer + "/refresh_token";
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public static String getAccessToken() {
        return ACCESS_TOKEN;
    }

    public String getHeaderName() {
        return JWT_HEADER_NAME;
    }

    public String getHeaderSchema() {
        return JWT_HEADER_SCHEMA;
    }

    public String getParameterName() {
        return parameterName;
    }

    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }

    public int getExpires() {
        return expires;
    }

    public void setExpires(int expires) {
        this.expires = expires;
    }

    public int getRefreshExpires() {
        return refreshExpires;
    }

    public void setRefreshExpires(int refreshExpires) {
        this.refreshExpires = refreshExpires;
    }

    public int getRefreshAuthExpires() {
        return refreshAuthExpires;
    }

    public void setRefreshAuthExpires(int refreshAuthExpires) {
        this.refreshAuthExpires = refreshAuthExpires;
    }

    public long getLeeway() {
        return leeway;
    }

    public void setLeeway(long leeway) {
        this.leeway = leeway;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }
}
