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
 * 插槽预览令牌服务（Phase 7 / §11.2 / §7.0）。控制台预览面板以 iframe 渲染前台时，浏览器导航无法携带
 * 控制台的 {@code Authorization: Bearer} 头，也没有后台前台会话 Cookie；故由已登录管理员（{@code /api/backend}
 * JWT 鉴权）签发一枚<b>短时、签发人隔离</b>的预览令牌，作为 {@code ?_preview=draft&_ptoken=} 传入。
 * {@link PreviewAuthFilter} 在前台过滤链校验它并置 {@code SecurityContext}，使
 * {@code FrontendInterceptor} / {@code SlotController} 的既有草稿门禁不改一行即可通过。
 *
 * <p>签发人为 {@code <issuer>/slot_preview}，区别于访问令牌的 {@code <issuer>/access_token}：{@code /api}
 * 资源服务器解码器按访问令牌签发人校验，会<b>拒绝</b>预览令牌，故预览令牌无法当 API Bearer 使用；5 分钟默认
 * TTL 限定泄漏（Referer / 日志）窗口；令牌只授予前台只读渲染的草稿可见性，不参与任何授权。
 *
 * @author PONY
 */
@Service
public class PreviewTokenService {
    /** 预览令牌用途声明值，校验时核对。 */
    public static final String PURPOSE = "slot-preview";
    /** 用途声明名。 */
    private static final String CLAIM_PURPOSE = "purpose";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder previewDecoder;
    private final String issuer;
    private final long ttlSeconds;

    public PreviewTokenService(JwtEncoder jwtEncoder, SecretKey secretKey, JwtProperties jwtProperties,
                                   @Value("${ujcms.composition.preview-token-ttl-seconds:300}") long ttlSeconds) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = jwtProperties.getIssuer() + "/slot_preview";
        this.ttlSeconds = ttlSeconds;
        // 复用同一 HmacSM3 秘钥，但绑定独立签发人——与 /api 访问令牌解码器互不接受（签名一致、签发人不同）。
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        this.previewDecoder = decoder;
    }

    /**
     * 为指定管理员签发预览令牌。
     *
     * @param username 管理员用户名（令牌 subject）
     * @return 令牌值 + 有效期（秒）
     */
    public Token mint(String username) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(username)
                .issuedAt(now).expiresAt(now.plusSeconds(ttlSeconds)).claim(CLAIM_PURPOSE, PURPOSE).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new Token(value, ttlSeconds);
    }

    /**
     * 校验预览令牌（签名 + 签发人 + 过期 + 用途）。
     *
     * @param token 令牌值
     * @return 通过则返回 subject（用户名）；否则 {@code null}
     */
    @Nullable
    public String verify(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Jwt jwt = previewDecoder.decode(token);
            if (!PURPOSE.equals(jwt.getClaimAsString(CLAIM_PURPOSE))) {
                return null;
            }
            return jwt.getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    /** 预览令牌签发结果（{@code token} 令牌值，{@code expiresIn} 有效期秒）。 */
    public record Token(String token, long expiresIn) {
    }
}
