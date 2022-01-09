package com.ofwise.util.security.jwt;

import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

/**
 * JWT 工具类
 *
 * @author PONY
 */
public final class JwtUtils {
    /**
     * Access Token 记住我
     */
    private static String CLAIM_REMEMBERED = "remembered";
    /**
     * Token 登录时间
     */
    private static String CLAIM_LOGIN_TIME = "loginTime";
    /**
     * Token 登录ID
     */
    private static String CLAIM_LOGIN_ID = "loginId";
    /**
     * Refresh Token 认证过期时间
     */
    private static String CLAIM_AUTH_EXPIRES_AT = "authExpiresAt";

    /**
     * 设置 rememberMe Claim
     */
    public static void withRememberedClaim(JWTCreator.Builder builder, boolean rememberMe) {
        if (rememberMe) {
            builder.withClaim(CLAIM_REMEMBERED, true);
        }
    }

    /**
     * 获取 rememberMe Claim
     */
    public static boolean getRememberedClaim(DecodedJWT jwt) {
        Boolean remembered = jwt.getClaim(CLAIM_REMEMBERED).asBoolean();
        return remembered != null && remembered;
    }

    /**
     * 设置 loginTime Claim
     */
    public static void withLoginTimeClaim(JWTCreator.Builder builder, Date loginTime) {
        builder.withClaim(CLAIM_LOGIN_TIME, loginTime);
    }

    /**
     * 获取 loginTime Claim
     */
    public static Date getLoginTimeClaim(DecodedJWT jwt) {
        return jwt.getClaim(CLAIM_LOGIN_TIME).asDate();
    }

    /**
     * 设置 loginId Claim
     */
    public static void withLoginIdClaim(JWTCreator.Builder builder, String loginId) {
        builder.withClaim(CLAIM_LOGIN_ID, loginId);
    }

    /**
     * 获取 loginId Claim
     */
    public static String getLoginIdClaim(DecodedJWT jwt) {
        return jwt.getClaim(CLAIM_LOGIN_ID).asString();
    }

    /**
     * 设置 authExpiresAt Claim
     */
    public static void withAuthExpiresAtClaim(JWTCreator.Builder builder, Date authExpiresAt) {
        builder.withClaim(CLAIM_AUTH_EXPIRES_AT, authExpiresAt);
    }

    /**
     * 获取 authExpiresAt Claim
     */
    public static Date getAuthExpiresAtClaim(DecodedJWT jwt) {
        return jwt.getClaim(CLAIM_AUTH_EXPIRES_AT).asDate();
    }
}
