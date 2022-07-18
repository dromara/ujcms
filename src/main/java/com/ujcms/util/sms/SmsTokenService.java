package com.ujcms.util.sms;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.primitives.Longs;
import com.ujcms.util.security.jwt.HmacSm3Algorithm;
import com.ujcms.util.web.Strings;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.Date;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ENGLISH;

/**
 * 使用 Token 方式实现短信验证码。由于 RESTful 是无状态的，无法使用 Session 记住短信验证码。
 * <p>
 * 可以将验证码写入数据库，这种方式显得太重了。这里使用自验证的方式，不依赖数据库，甚至在分布式系统也适用。
 * <p>
 * 使用缓存记住已经使用的验证码，防止重复使用。
 *
 * @author liufang
 */
public class SmsTokenService {
    public SmsTokenService(SmsTokenCache cache, SmsTokenProperties properties) {
        this.cache = cache;
        this.properties = properties;

        this.algorithm = new HmacSm3Algorithm(properties.getSecret());
        this.verifier = JWT.require(algorithm).withIssuer(properties.getIssuer())
                .acceptLeeway(properties.getLeeway()).build();
    }

    /**
     * 获取 JWT Token
     */
    public String createToken(Integer id, String phoneNumber, String code) {
        Date now = new Date();
        // 只取秒级，不要毫秒。JWT 的 expiresAt 只能记录到秒
        Date expiresAt = new Date(now.getTime() / 1000 * 1000 + properties.getExpires() * 60 * 1000);
        String subject = sign(code, expiresAt);
        return JWT.create().withIssuer(properties.getIssuer()).withIssuedAt(now).withExpiresAt(expiresAt)
                .withSubject(subject).withJWTId(id.toString()).withClaim(PHONE_NUMBER, phoneNumber).sign(algorithm);
    }

    /**
     * 获取 ID
     */
    public Integer getId(@Nullable String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        try {
            DecodedJWT jwt = verifier.verify(token);
            String jti = jwt.getId();
            return jti == null ? null : Integer.valueOf(jti);
        } catch (JWTVerificationException e) {
            // 验证失败
            return null;
        }
    }

    /**
     * 检查验证码是否正确，无论正确与否，该验证码立即失效，不可以再次使用。
     *
     * @param token jwt token
     * @param code  短信验证码
     * @return 是否验证成功
     */
    public boolean validateCode(@Nullable String token, @Nullable String phoneNumber, @Nullable String code) {
        if (StringUtils.isBlank(token) || StringUtils.isBlank(code)) {
            return false;
        }
        try {
            DecodedJWT jwt = verifier.verify(token);
            if (cache.isAttempted(jwt.getSignature())) {
                return false;
            }
            cache.setAttempted(jwt.getSignature());
            if (!StringUtils.equals(phoneNumber, jwt.getClaim(PHONE_NUMBER).asString())) {
                return false;
            }
            return verifySubject(jwt.getSubject(), code, jwt.getExpiresAt());
        } catch (JWTVerificationException e) {
            // 验证失败
            return false;
        }
    }

    private boolean verifySubject(String subject, String code, Date expiresAt) {
        return sign(code, expiresAt).equals(subject);
    }

    private String sign(String code, Date expiresAt) {
        // 不区分大小写
        byte[] subjectBytes = algorithm.sign(code.toUpperCase(ENGLISH).getBytes(UTF_8), Longs.toByteArray(expiresAt.getTime()));
        return Strings.encodeUrlBase64(subjectBytes);
    }

    private static final String PHONE_NUMBER = "phoneNumber";

    private SmsTokenCache cache;
    private SmsTokenProperties properties;

    private Algorithm algorithm;
    private JWTVerifier verifier;
}
