package com.ujcms.cms.core.security;

import com.ujcms.common.security.jwt.JwtProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;

/**
 * 双因子登录挑战令牌服务。
 * <p>
 * 双因子登录分两步：先校验口令，通过后签发一枚<b>短时、签发人隔离</b>的挑战令牌；客户端凭它请求发送验证码、
 * 提交验证码完成登录。令牌只证明“持有者刚刚通过了口令校验”，不授予任何访问权限。
 * <p>
 * 签发人为 {@code <issuer>/mfa_token}，区别于访问令牌的 {@code <issuer>/access_token} 与刷新令牌的
 * {@code <issuer>/refresh_token}：各解码器按各自签发人校验，挑战令牌无法充当 API Bearer 或刷新令牌。
 * 令牌无状态、无需服务端撤销：即使登录成功后被重放，仍需一枚新的、一次性的验证码才能再次登录。
 *
 * @author PONY
 */
@Service
public class MfaTokenService {
    /** 挑战令牌用途声明值，校验时核对。 */
    public static final String PURPOSE = "mfa-login";
    /** 用途声明名。 */
    private static final String CLAIM_PURPOSE = "purpose";
    /** 是否浏览器访问声明名。沿用第一步登录请求的取值，决定最终签发的刷新令牌有效期。 */
    private static final String CLAIM_BROWSER = "browser";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder mfaDecoder;
    private final String issuer;
    private final long ttlSeconds;

    public MfaTokenService(JwtEncoder jwtEncoder, SecretKey secretKey, JwtProperties jwtProperties,
                           @Value("${ujcms.security.mfa-token-ttl-seconds:600}") long ttlSeconds) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = jwtProperties.getIssuer() + "/mfa_token";
        this.ttlSeconds = ttlSeconds;
        // 复用同一 HmacSM3 秘钥，但绑定独立签发人——与访问令牌、刷新令牌解码器互不接受（签名一致、签发人不同）。
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        this.mfaDecoder = decoder;
    }

    /**
     * 为已通过口令校验的用户签发挑战令牌。
     *
     * @param username 用户名（令牌 subject）
     * @param browser  是否浏览器访问
     * @return 令牌值
     */
    public String mint(String username, boolean browser) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(username)
                .issuedAt(now).expiresAt(now.plusSeconds(ttlSeconds))
                .claim(CLAIM_PURPOSE, PURPOSE).claim(CLAIM_BROWSER, browser).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * 校验挑战令牌（签名 + 签发人 + 过期 + 用途）。
     *
     * @param token 令牌值
     * @return 通过则返回挑战信息；否则 {@code null}
     */
    @Nullable
    public Challenge verify(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Jwt jwt = mfaDecoder.decode(token);
            if (!PURPOSE.equals(jwt.getClaimAsString(CLAIM_PURPOSE))) {
                return null;
            }
            return new Challenge(jwt.getSubject(), Boolean.TRUE.equals(jwt.getClaimAsBoolean(CLAIM_BROWSER)));
        } catch (JwtException e) {
            return null;
        }
    }

    /** 挑战信息（{@code username} 已通过口令校验的用户名，{@code browser} 是否浏览器访问）。 */
    public record Challenge(String username, boolean browser) {
    }
}
