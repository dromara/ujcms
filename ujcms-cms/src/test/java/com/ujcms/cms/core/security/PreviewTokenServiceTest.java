package com.ujcms.cms.core.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.ujcms.common.security.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 插槽预览令牌服务测试（Phase 7 / §7.0）：签发-校验往返、过期、签名/用途拒绝，以及关键安全属性——
 * 与访问令牌<b>签发人隔离</b>（预览令牌不能当 /api Bearer，反之亦然）。
 */
class PreviewTokenServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private SecretKey secretKey;
    private JwtEncoder encoder;
    private JwtProperties props;

    @BeforeEach
    void setUp() {
        secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSM3");
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(new OctetSequenceKey.Builder(secretKey).build())));
        props = new JwtProperties();
        props.setSecret(SECRET);
    }

    private PreviewTokenService service(long ttlSeconds) {
        return new PreviewTokenService(encoder, secretKey, props, ttlSeconds);
    }

    @Test
    void mintThenVerifyRoundTrips() {
        PreviewTokenService service = service(300);
        PreviewTokenService.Token token = service.mint("admin");
        assertEquals(300, token.expiresIn());
        assertEquals("admin", service.verify(token.token()));
    }

    @Test
    void verifyRejectsBlankTamperedOrGarbageToken() {
        PreviewTokenService service = service(300);
        assertNull(service.verify(null));
        assertNull(service.verify(""));
        assertNull(service.verify("not.a.jwt"));
        assertNull(service.verify(service.mint("admin").token() + "x"), "破坏签名应拒绝");
    }

    @Test
    void verifyRejectsExpiredToken() {
        // 签发于 5 分钟前、2 分钟前过期的预览令牌（exp 仍晚于 iat，故可签发），过期幅度超出默认 60s 容差
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(props.getIssuer() + "/slot_preview").subject("admin")
                .issuedAt(now.minusSeconds(300)).expiresAt(now.minusSeconds(120))
                .claim("purpose", PreviewTokenService.PURPOSE).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        assertNull(service(300).verify(token));
    }

    @Test
    void verifyRejectsTokenMissingPurposeClaim() {
        // 同密钥、同签发人但无 purpose 声明的 JWT 也不被接受（防同密钥任意 JWT 冒充）
        String noPurpose = encode(props.getIssuer() + "/slot_preview", "admin");
        assertNull(service(300).verify(noPurpose));
    }

    @Test
    void verifyRejectsAccessIssuerToken() {
        // 访问令牌签发人的 JWT（签名一致）不被预览解码器接受
        String accessIssued = encode(props.getAccessTokenIssuer(), "admin");
        assertNull(service(300).verify(accessIssued));
    }

    @Test
    void previewTokenRejectedByAccessTokenDecoder() {
        // 安全保证：预览令牌不能当 /api 访问令牌用——资源服务器解码器按访问令牌签发人校验，拒绝预览令牌
        String previewToken = service(300).mint("admin").token();
        NimbusJwtDecoder accessDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        accessDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.getAccessTokenIssuer()));
        assertThrows(JwtException.class, () -> accessDecoder.decode(previewToken));
    }

    private String encode(String issuer, String subject) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(subject)
                .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
