package com.ujcms.commons.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Requirement;
import com.nimbusds.jose.crypto.impl.HMAC;
import com.nimbusds.jose.proc.BadJWSException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.text.ParseException;
import java.util.Date;

/**
 * JWT 工具类
 *
 * @author PONY
 */
public final class JwtUtils {
    public static final JWSAlgorithm HMAC_SM3 = new JWSAlgorithm("HmacSM3", Requirement.REQUIRED);
    protected static final Provider PROVIDER = new BouncyCastleProvider();

    public static byte[] signByHmacSm3(String secret, byte[] message, byte[]... messages) {
        try {
            for (byte[] m : messages) {
                message = ArrayUtils.addAll(message, m);
            }
            return HMAC.compute(HMAC_SM3.getName(), secret.getBytes(StandardCharsets.UTF_8),
                    message, PROVIDER);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JWTClaimsSet verify(JWSVerifier jwsVerifier, JWTClaimsSetVerifier<SecurityContext> claimsSetVerifier,
                                      SignedJWT jwt) throws BadJWTException, JOSEException, ParseException, BadJWSException {
        if (!jwt.verify(jwsVerifier)) {
            throw new BadJWSException("Signed JWT rejected: Invalid signature");
        }
        JWTClaimsSet claimsSet = jwt.getJWTClaimsSet();
        claimsSetVerifier.verify(claimsSet, null);
        return claimsSet;
    }

    /**
     * Access Token 权限
     */
    public static final String CLAIM_SCOPE = "scope";
    /**
     * Access Token 记住我
     */
    public static final String CLAIM_REMEMBERED = "remembered";
    /**
     * Token 登录时间
     */
    public static final String CLAIM_LOGIN_TIME = "loginTime";
    /**
     * Token 登录ID
     */
    public static final String CLAIM_LOGIN_ID = "loginId";
    /**
     * Refresh Token 认证过期时间
     */
    public static final String CLAIM_AUTH_EXPIRES_AT = "authExpiresAt";

    /**
     * 设置 rememberMe Claim
     */
    public static void withRememberedClaim(JWTClaimsSet.Builder builder, boolean rememberMe) {
        if (rememberMe) {
            builder.claim(CLAIM_REMEMBERED, true);
        }
    }

    /**
     * 获取 rememberMe Claim
     */
    public static boolean getRememberedClaim(JWTClaimsSet jwt) {
        Object remembered = jwt.getClaim(CLAIM_REMEMBERED);
        return remembered instanceof Boolean && (Boolean) remembered;
    }

    /**
     * 设置 loginTime Claim
     */
    public static void withLoginTimeClaim(JWTClaimsSet.Builder builder, Date loginTime) {
        builder.claim(CLAIM_LOGIN_TIME, loginTime);
    }

    /**
     * 获取 loginTime Claim
     */
    public static Date getLoginTimeClaim(JWTClaimsSet jwt) throws BadJWTException {
        Object loginTime = jwt.getClaim(CLAIM_LOGIN_TIME);
        if (loginTime instanceof Date) {
            return (Date) loginTime;
        }
        throw new BadJWTException(CLAIM_LOGIN_TIME + " not instanceof Date");
    }

    /**
     * 设置 loginId Claim
     */
    public static void withLoginIdClaim(JWTClaimsSet.Builder builder, String loginId) {
        builder.claim(CLAIM_LOGIN_ID, loginId);
    }

    /**
     * 获取 loginId Claim
     */
    public static String getLoginIdClaim(JWTClaimsSet jwt) {
        Object loginId = jwt.getClaim(CLAIM_LOGIN_ID);
        return loginId instanceof String ? (String) loginId : null;
    }

    /**
     * 设置 authExpiresAt Claim
     */
    public static void withAuthExpiresAtClaim(JWTClaimsSet.Builder builder, Date authExpiresAt) {
        builder.claim(CLAIM_AUTH_EXPIRES_AT, authExpiresAt);
    }

    /**
     * 获取 authExpiresAt Claim
     */
    public static Date getAuthExpiresAtClaim(JWTClaimsSet jwt) throws BadJWTException {
        Object authExpiresAt = jwt.getClaim(CLAIM_AUTH_EXPIRES_AT);
        if (authExpiresAt instanceof Date) {
            return (Date) authExpiresAt;
        }
        throw new BadJWTException(CLAIM_AUTH_EXPIRES_AT + " not instanceof Date");
    }

    private JwtUtils() {
        throw new IllegalStateException("Utility class");
    }

}
