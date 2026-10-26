package com.ujcms.common.captcha;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.proc.BadJWSException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import com.ujcms.common.security.jwt.HmacSm3JwsSigner;
import com.ujcms.common.security.jwt.HmacSm3JwsVerifier;
import com.ujcms.common.security.jwt.JwtUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.ujcms.common.security.jwt.JwtUtils.HMAC_SM3;

/**
 * 使用 Token 方式实现验证码。由于 RESTful 是无状态的，无法使用 Session 记住验证码。
 * <p>
 * 可以将验证码写入数据库，这种方式显得太重了。这里使用自验证的方式，不依赖数据库，甚至在分布式系统也适用。
 * 使用缓存记住已经使用的验证码，防止重复使用。
 * <p>
 * 题目由 {@link CaptchaProvider} 出、由它判；本类按类型路由，并统一处理令牌签发与校验、过期、一次性消费、尝试次数。
 * 令牌声明 {@value #CLAIM_TYPE} 记录类型，{@value #CLAIM_IP} 记录出题时的访客 IP（厂商二次校验需要）。
 * 静默类型（{@link CaptchaProvider#visible()} 为 false 的工作量证明）只有调用方显式允许时才接受：
 * 既有的十余个消费者调用 {@link #validateCaptcha(String, String)}，始终要求可见挑战。
 *
 * @author PONY
 */
public class CaptchaTokenService {
    private static final Logger logger = LoggerFactory.getLogger(CaptchaTokenService.class);

    /**
     * 类型声明
     */
    public static final String CLAIM_TYPE = "typ";
    /**
     * 出题时的访客 IP 声明
     */
    public static final String CLAIM_IP = "ip";

    /**
     * 校验结论
     */
    public enum Verdict {
        /**
         * 通过
         */
        OK,
        /**
         * 不正确（含令牌无效、过期、已使用、尝试过多）
         */
        INCORRECT,
        /**
         * 令牌有效但为静默类型，而本次校验要求可见挑战
         */
        VISIBLE_REQUIRED;

        public boolean ok() {
            return this == OK;
        }
    }

    public CaptchaTokenService(Collection<? extends CaptchaProvider> providers, CaptchaCache cache,
                               CaptchaProperties properties) throws KeyLengthException {
        Map<String, CaptchaProvider> map = new LinkedHashMap<>();
        for (CaptchaProvider provider : providers) {
            CaptchaProvider previous = map.put(provider.type(), provider);
            if (previous != null) {
                throw new IllegalStateException("duplicate captcha provider type: " + provider.type());
            }
        }
        this.providers = Collections.unmodifiableMap(map);
        this.cache = cache;
        this.properties = properties;
        this.jwsSigner = new HmacSm3JwsSigner(properties.getSecret());
        this.jwsVerifier = new HmacSm3JwsVerifier(properties.getSecret());
        this.claimsVerifier = new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(properties.getIssuer()).build(), null);
    }

    /**
     * 已注册的类型代码，按注册顺序
     */
    public Set<String> types() {
        return providers.keySet();
    }

    public boolean supports(@Nullable String type) {
        return type != null && providers.containsKey(type);
    }

    @Nullable
    public CaptchaProvider provider(@Nullable String type) {
        return type == null ? null : providers.get(type);
    }

    /**
     * 出题并签发令牌
     *
     * @param type 类型代码，必须已注册
     * @param ip   访客 IP，可为 null
     * @return 题目
     * @throws IllegalArgumentException 类型未注册
     */
    public CaptchaToken getCaptchaToken(String type, @Nullable String ip) {
        CaptchaProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("captcha type not registered: " + type);
        }
        CaptchaChallenge challenge = provider.challenge();
        Date now = new Date();
        // 只取秒级，不要毫秒。JWT 的 expiresAt 只能记录到秒
        Date expiresAt = new Date(now.getTime() / 1000 * 1000 + (long) properties.getExpires() * 60 * 1000);
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().issuer(properties.getIssuer())
                .issueTime(now).expirationTime(expiresAt)
                .claim(CLAIM_TYPE, type);
        if (StringUtils.isNotBlank(ip)) {
            builder.claim(CLAIM_IP, ip);
        }
        for (Map.Entry<String, Object> entry : challenge.claims().entrySet()) {
            if (CLAIM_TYPE.equals(entry.getKey()) || CLAIM_IP.equals(entry.getKey())) {
                throw new IllegalStateException("captcha provider '" + type + "' uses a reserved claim: " + entry.getKey());
            }
            builder.claim(entry.getKey(), entry.getValue());
        }
        try {
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(HMAC_SM3), builder.build());
            signedJwt.sign(jwsSigner);
            return new CaptchaToken(type, signedJwt.serialize(), properties.getExpires() * 60, challenge.payload());
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign captcha token", e);
        }
    }

    /**
     * 用于前台检查验证码是否正确，提高用户体验，方便。尝试次数限制一般为 5 次。不消费令牌。
     *
     * @param token   captcha token
     * @param captcha 验证码
     * @return 是否验证成功
     */
    public boolean tryCaptcha(@Nullable String token, @Nullable String captcha) {
        Parsed parsed = parse(token, captcha);
        if (parsed == null || !parsed.provider.supportsTry()) {
            return false;
        }
        int count = cache.getAttempts(parsed.signature);
        // count == -1 代表已经被使用过
        if (count < 0 || count >= properties.getMaxAttempts()) {
            return false;
        }
        if (parsed.provider.verify(parsed.claims, parsed.answer)) {
            return true;
        }
        cache.updateAttempts(parsed.signature, count + 1);
        return false;
    }

    /**
     * 检查验证码是否正确，无论正确与否，该验证码立即失效，不可以再次使用。只接受可见挑战。
     *
     * @param token   captcha token
     * @param captcha 验证码
     * @return 是否验证成功
     */
    public boolean validateCaptcha(@Nullable String token, @Nullable String captcha) {
        return validate(token, captcha, false).ok();
    }

    /**
     * 检查验证码是否正确，无论正确与否，该验证码立即失效，不可以再次使用。
     *
     * @param token       captcha token
     * @param captcha     验证码
     * @param allowSilent 是否接受静默类型（工作量证明）
     * @return 校验结论
     */
    public Verdict validate(@Nullable String token, @Nullable String captcha, boolean allowSilent) {
        Parsed parsed = parse(token, captcha);
        if (parsed == null) {
            return Verdict.INCORRECT;
        }
        // 策略拒绝不消费令牌：调用方据此提示“请完成人机验证”，令牌本身并无过错
        if (!allowSilent && !parsed.provider.visible()) {
            return Verdict.VISIBLE_REQUIRED;
        }
        int count = cache.getAttempts(parsed.signature);
        // 已使用或者尝试过多，直接返回false
        if (count < 0 || count >= properties.getMaxAttempts()) {
            return Verdict.INCORRECT;
        }
        // count == -1 代表已经被使用过
        cache.updateAttempts(parsed.signature, -1);
        return parsed.provider.verify(parsed.claims, parsed.answer) ? Verdict.OK : Verdict.INCORRECT;
    }

    /**
     * 令牌的类型。令牌无效时返回 {@code null}
     */
    @Nullable
    public String tokenType(@Nullable String token) {
        Parsed parsed = parse(token, "-");
        return parsed == null ? null : parsed.provider.type();
    }

    @Nullable
    private Parsed parse(@Nullable String token, @Nullable String captcha) {
        String answer = StringUtils.trimToNull(captcha);
        if (StringUtils.isBlank(token) || answer == null) {
            return null;
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claimsSet = JwtUtils.verify(jwsVerifier, claimsVerifier, jwt);
            String type = claimsSet.getStringClaim(CLAIM_TYPE);
            CaptchaProvider provider = type == null ? null : providers.get(type);
            if (provider == null) {
                logger.debug("captcha token of unregistered type: {}", type);
                return null;
            }
            return new Parsed(provider, claimsSet.getClaims(), jwt.getSignature().decodeToString(), answer);
        } catch (ParseException | JOSEException | BadJWTException | BadJWSException e) {
            // 验证失败
            return null;
        }
    }

    private record Parsed(CaptchaProvider provider, Map<String, Object> claims, String signature, String answer) {
    }

    private final Map<String, CaptchaProvider> providers;
    private final CaptchaCache cache;
    private final CaptchaProperties properties;
    private final HmacSm3JwsSigner jwsSigner;
    private final HmacSm3JwsVerifier jwsVerifier;
    private final JWTClaimsSetVerifier<SecurityContext> claimsVerifier;
}
