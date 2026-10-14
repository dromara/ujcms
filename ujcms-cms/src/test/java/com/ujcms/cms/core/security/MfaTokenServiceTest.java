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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双因子挑战令牌服务测试：签发-校验往返、过期、签名/用途拒绝，以及关键安全属性——与访问令牌、刷新令牌
 * <b>签发人隔离</b>：挑战令牌只证明“口令已通过”，绝不能当 /api Bearer 或刷新令牌使用，反之亦然。
 */
class MfaTokenServiceTest {
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

    private MfaTokenService service() {
        return new MfaTokenService(encoder, secretKey, props, 600);
    }

    private String tokenWith(String issuer, Instant issuedAt, Instant expiresAt, String purpose) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer).subject("admin")
                .issuedAt(issuedAt).expiresAt(expiresAt);
        if (purpose != null) {
            claims.claim("purpose", purpose);
        }
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    @Test
    void mintThenVerifyRoundTripsUsernameAndBrowserFlag() {
        MfaTokenService service = service();

        MfaTokenService.Challenge browser = service.verify(service.mint("admin", true));
        assertNotNull(browser);
        assertEquals("admin", browser.username());
        assertTrue(browser.browser());

        MfaTokenService.Challenge app = service.verify(service.mint("admin", false));
        assertNotNull(app);
        assertFalse(app.browser());
    }

    @Test
    void verifyRejectsBlankTamperedOrGarbageToken() {
        MfaTokenService service = service();
        assertNull(service.verify(null));
        assertNull(service.verify(""));
        assertNull(service.verify("not.a.jwt"));
        assertNull(service.verify(service.mint("admin", true) + "x"), "破坏签名应拒绝");
    }

    @Test
    void verifyRejectsExpiredToken() {
        // 过期幅度超出校验器默认 60s 容差
        Instant now = Instant.now();
        String expired = tokenWith(props.getIssuer() + "/mfa_token", now.minusSeconds(900), now.minusSeconds(120),
                MfaTokenService.PURPOSE);
        assertNull(service().verify(expired));
    }

    @Test
    void verifyRejectsTokenWithoutTheMfaPurpose() {
        Instant now = Instant.now();
        String issuer = props.getIssuer() + "/mfa_token";
        assertNull(service().verify(tokenWith(issuer, now, now.plusSeconds(600), null)));
        assertNull(service().verify(tokenWith(issuer, now, now.plusSeconds(600), "slot-preview")));
    }

    @Test
    void mfaTokenIsRejectedByAccessAndRefreshTokenDecoders() {
        String mfaToken = service().mint("admin", true);

        NimbusJwtDecoder accessDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        accessDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.getAccessTokenIssuer()));
        assertThrows(JwtException.class, () -> accessDecoder.decode(mfaToken), "挑战令牌不能当 /api Bearer");

        NimbusJwtDecoder refreshDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        refreshDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.getRefreshTokenIssuer()));
        assertThrows(JwtException.class, () -> refreshDecoder.decode(mfaToken), "挑战令牌不能当刷新令牌");
    }

    @Test
    void accessAndRefreshTokensAreRejectedAsMfaTokens() {
        Instant now = Instant.now();
        // 即使带上用途声明，签发人不同也必须拒绝
        assertNull(service().verify(tokenWith(props.getAccessTokenIssuer(), now, now.plusSeconds(600),
                MfaTokenService.PURPOSE)));
        assertNull(service().verify(tokenWith(props.getRefreshTokenIssuer(), now, now.plusSeconds(600),
                MfaTokenService.PURPOSE)));
    }
}
