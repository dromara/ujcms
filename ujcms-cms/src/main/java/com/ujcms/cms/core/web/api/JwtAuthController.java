package com.ujcms.cms.core.web.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.component.JwtAuthService;
import com.ujcms.cms.core.component.SmsCodeSender;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.security.MfaTokenService;
import com.ujcms.cms.core.security.PiiMasker;
import com.ujcms.cms.core.security.PiiNormalizer;
import com.ujcms.cms.core.security.TwoFactorGuard;
import com.ujcms.cms.core.security.spi.SecondFactorProvider;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.captcha.IpLoginAttemptService;
import com.ujcms.common.security.Secures;
import com.ujcms.common.security.jwt.JwtProperties;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.Servlets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;

import static com.ujcms.common.security.jwt.JwtUtils.*;

/**
 * @author PONY
 */
@Tag(name = "JWT认证接口")
@RestController
@RequestMapping(UrlConstants.API + "/auth/jwt")
public class JwtAuthController {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthController.class);

    /**
     * 登录响应状态码：需先修改密码（首次登录强制改密）。前台据此引导用户修改密码后重新登录。
     */
    public static final int STATUS_PASSWORD_CHANGE_REQUIRED = 1010;

    /**
     * 登录响应状态码：密码已过期需先修改密码。前台据此引导用户修改密码后重新登录，与首次登录强制改密共用改密弹窗。
     */
    public static final int STATUS_PASSWORD_EXPIRED = 1011;

    /**
     * 登录响应状态码：口令已通过，还需完成第二因子（短信验证码）验证。响应的 {@code result} 为
     * {@link SecondFactorChallenge}，前台据此请求发送验证码并提交验证码完成登录。
     */
    public static final int STATUS_SECOND_FACTOR_REQUIRED = 1020;

    /**
     * 登录响应状态码：双因子挑战令牌无效或已过期。前台据此回到用户名密码一步重新登录。
     */
    public static final int STATUS_SECOND_FACTOR_EXPIRED = 1021;

    private final Props props;
    private final JwtProperties properties;
    private final JwtDecoder refreshTokenDecoder;
    private final JwtAuthService service;

    public JwtAuthController(Props props, JwtProperties properties, SecretKey secretKey, JwtAuthService service) {
        this.props = props;
        this.properties = properties;
        this.service = service;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getRefreshTokenIssuer()));
        this.refreshTokenDecoder = decoder;
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ResponseEntity<Body> login(@RequestBody @Valid LoginParams params, HttpServletRequest request) {
        Config config = configService.getUnique();
        Config.Security security = config.getSecurity();
        String ip = Servlets.getRemoteAddr(request, props.getIpProxyDepth());
        // IP登录失败超过限制次数
        if (ipLoginAttemptService.isExcessive(ip, security.getIpMaxAttempts())) {
            loginLogService.loginFailure(params.username, ip, LoginLog.STATUS_IP_EXCESSIVE_ATTEMPTS);
            return Responses.failure(request, "error.ipExcessiveAttempts");
        }
        // 是否需要验证码。开启双因子认证时同样需要：第二因子在口令之后校验，口令这一步仍需防暴力破解
        if (ipLoginAttemptService.isExcessive(ip, security.getIpCaptchaAttempts()) &&
                !captchaTokenService.validateCaptcha(params.captchaToken, params.captcha)) {
            loginLogService.loginFailure(params.username, ip, LoginLog.STATUS_CAPTCHA_WRONG);
            return Responses.failure(request, "error.captchaIncorrect");
        }
        User user = userService.selectByUsername(params.username);
        // 用户名不存在
        if (user == null) {
            ipLoginAttemptService.failure(ip);
            loginLogService.loginFailure(params.username, ip, LoginLog.STATUS_LOGIN_NAME_NOT_FOUND);
            return Responses.failure(request, "error.usernameNotExist");
        }
        // 用户登录是否超过尝试次数
        if (user.isExcessiveAttempts(security.getUserMaxAttempts(), security.getUserLockMinutes())) {
            loginLogService.loginFailure(user.getId(), params.username, ip, LoginLog.STATUS_USER_EXCESSIVE_ATTEMPTS);
            return Responses.failure(request, "error.userExcessiveAttempts");
        }
        // 前台密码已通过SM2加密，此处进行解密
        String rawPassword = Secures.sm2Decrypt(params.password, props.getClientSm2PrivateKey());
        String password = user.getPassword();
        // 密码错误
        if (StringUtils.isBlank(password) || !passwordEncoder.matches(rawPassword, password)) {
            return service.incorrectPassword(user, params.username, ip, security, request);
        }
        // 非正常状态用户
        if (user.isDisabled()) {
            return service.disabledUser(user, params.username, ip, request);
        }
        // 首次登录强制改密：口令已校验通过，但该用户需先改密，返回专用状态码由前台引导修改后重新登录
        if (security.isForceChangePasswordOnFirstLogin() && Boolean.TRUE.equals(user.getPwdMustChange())) {
            return Responses.status(STATUS_PASSWORD_CHANGE_REQUIRED, request, "error.passwordChangeRequired");
        }
        // 密码已过期：口令已校验通过后再判断，返回专用状态码由前台引导修改密码后重新登录（与首次登录强制改密共用改密弹窗）
        if (user.isPasswordExpired(security.getPasswordMaxDays())) {
            loginLogService.loginFailure(user.getId(), params.username, ip, LoginLog.STATUS_PASSWORD_EXPIRED);
            return Responses.status(STATUS_PASSWORD_EXPIRED, request, "error.passwordExpired");
        }
        // 双因子认证：口令是第一因子，通过后才进入第二因子。验证码只发往账号登记的手机号码，且只在口令通过后发送；
        // 此处不签发访问令牌，也不清零登录失败计数（见 JwtAuthService#completeLogin）。
        // 强制改密 / 密码过期先于第二因子处理：改密接口本就只需旧口令，先处理可避免白白消耗一条验证码。
        if (twoFactorGuard.isBypassed(security)) {
            logger.warn("Two-factor authentication is bypassed by ujcms.security.disable-two-factor. user: {}, IP: {}",
                    user.getUsername(), ip);
        }
        if (twoFactorGuard.isEnabled(security)) {
            if (PiiNormalizer.normalizeMobile(user.getMobile()) == null) {
                // 账号未登记手机号码：明确告知原因（此时口令已通过，不构成账号枚举），由管理员在用户管理中补登
                loginLogService.loginFailure(user.getId(), params.username, ip, LoginLog.STATUS_MOBILE_MISSING);
                return Responses.failure(request, "error.twoFactorMobileMissing");
            }
            String mfaToken = mfaTokenService.mint(user.getUsername(), params.browser);
            Body body = new Body(STATUS_SECOND_FACTOR_REQUIRED,
                    Servlets.getMessage(request, "error.secondFactorRequired"));
            body.setResult(new SecondFactorChallenge(mfaToken, PiiMasker.maskMobile(user.getMobile())));
            return ResponseEntity.ok().body(body);
        }
        return service.completeLogin(user, params.username, ip, params.browser, security);
    }

    @Operation(summary = "发送双因子登录验证码。验证码发往账号登记的手机号码")
    @PostMapping("/mfa/send")
    public ResponseEntity<Body> mfaSend(@RequestBody @Valid MfaSendParams params, HttpServletRequest request) {
        Config config = configService.getUnique();
        Config.Security security = config.getSecurity();
        String ip = Servlets.getRemoteAddr(request, props.getIpProxyDepth());
        Challenged challenged = findChallenged(params.mfaToken);
        if (challenged == null) {
            return Responses.status(STATUS_SECOND_FACTOR_EXPIRED, request, "error.secondFactorExpired");
        }
        User user = challenged.user();
        ResponseEntity<Body> locked = rejectIfLocked(user, ip, security, request);
        if (locked != null) {
            return locked;
        }
        SmsCodeSender.SendResult result = smsCodeSender.sendMfaCode(user.getMobile(), ip, config.getSms());
        if (!result.isSuccess()) {
            return result.toFailure(request);
        }
        return Responses.ok(new MfaSendResult(result.getMessage().getId(), PiiMasker.maskMobile(user.getMobile()),
                result.getCooldownSeconds(), config.getSms().getCodeLength()));
    }

    @Operation(summary = "校验双因子登录验证码，完成登录")
    @PostMapping("/mfa/verify")
    public ResponseEntity<Body> mfaVerify(@RequestBody @Valid MfaVerifyParams params, HttpServletRequest request) {
        Config config = configService.getUnique();
        Config.Security security = config.getSecurity();
        String ip = Servlets.getRemoteAddr(request, props.getIpProxyDepth());
        Challenged challenged = findChallenged(params.mfaToken);
        if (challenged == null) {
            return Responses.status(STATUS_SECOND_FACTOR_EXPIRED, request, "error.secondFactorExpired");
        }
        User user = challenged.user();
        ResponseEntity<Body> locked = rejectIfLocked(user, ip, security, request);
        if (locked != null) {
            return locked;
        }
        // 第二因子校验经 SecondFactorProvider 接缝，默认实现为短信验证码
        if (!secondFactorProvider.validate(user.getMobile(), params.shortMessageId, params.code,
                config.getSms().getCodeExpires())) {
            service.secondFactorFailure(user, user.getUsername(), ip, security);
            // 验证码已失效（过期、尝试次数用尽、ID 不存在）：提示重新获取，而不是让用户对着作废的验证码反复重试
            ShortMessage message = shortMessageService.select(params.shortMessageId);
            if (message == null || message.isUsed()) {
                return Responses.failure(request, "error.mobileMessageExpired");
            }
            return Responses.failure(request, "error.mobileMessageIncorrect");
        }
        return service.completeLogin(user, user.getUsername(), ip, challenged.browser(), security);
    }

    /**
     * 根据挑战令牌取得已通过口令校验的用户。令牌无效或过期、用户已不存在或被禁用、手机号码已被清空，均视为挑战失效。
     */
    @Nullable
    private Challenged findChallenged(String mfaToken) {
        MfaTokenService.Challenge challenge = mfaTokenService.verify(mfaToken);
        if (challenge == null) {
            return null;
        }
        User user = userService.selectByUsername(challenge.username());
        if (user == null || user.isDisabled() || PiiNormalizer.normalizeMobile(user.getMobile()) == null) {
            return null;
        }
        return new Challenged(user, challenge.browser());
    }

    /**
     * IP 或用户登录失败次数超限时拒绝。第二因子的每一步都检查：口令通过后被锁定的账号，不能继续收发、猜测验证码。
     */
    @Nullable
    private ResponseEntity<Body> rejectIfLocked(User user, String ip, Config.Security security,
                                                HttpServletRequest request) {
        if (ipLoginAttemptService.isExcessive(ip, security.getIpMaxAttempts())) {
            loginLogService.loginFailure(user.getId(), user.getUsername(), ip,
                    LoginLog.STATUS_IP_EXCESSIVE_ATTEMPTS);
            return Responses.failure(request, "error.ipExcessiveAttempts");
        }
        if (user.isExcessiveAttempts(security.getUserMaxAttempts(), security.getUserLockMinutes())) {
            loginLogService.loginFailure(user.getId(), user.getUsername(), ip,
                    LoginLog.STATUS_USER_EXCESSIVE_ATTEMPTS);
            return Responses.failure(request, "error.userExcessiveAttempts");
        }
        return null;
    }

    private record Challenged(User user, boolean browser) {
    }

    @Operation(summary = "退出。一般情况下JWT无需服务器端退出。该方法主要用于记录退出日志")
    @PostMapping("/logout")
    public ResponseEntity<Body> logout(@RequestBody RefreshTokenParams params, HttpServletRequest request) {
        try {
            Jwt jwt = refreshTokenDecoder.decode(params.refreshToken);
            // 可以获取 jwtId 从数据库等存储空间中验证 token 是否伪造
            String username = jwt.getSubject();
            User user = userService.selectByUsername(username);
            if (user == null) {
                return Responses.failure("user not found. username: " + username);
            }
            loginLogService.logout(user.getId(), Servlets.getRemoteAddr(request, props.getIpProxyDepth()));
            return Responses.ok();
        } catch (BadJwtException e) {
            // 退出时 refresh token 已过期或签名不匹配（如服务重启后随机秘钥变化）属正常情况：
            // 客户端无论如何都会丢弃本地 token，此处无需记录退出日志，也不打印 token 与堆栈。
            logger.debug("logout with invalid refresh token: {}", e.getMessage());
            return Responses.ok();
        }
    }

    @Operation(summary = "刷新TOKEN")
    @PostMapping("/refresh-token")
    public ResponseEntity<Body> refreshToken(@RequestBody RefreshTokenParams params) {
        try {
            String refreshToken = params.refreshToken;
            Jwt jwt = refreshTokenDecoder.decode(params.refreshToken);
            // 可以获取 jwtId 从数据库等存储空间中验证 token 是否伪造
            String username = jwt.getSubject();
            User user = userService.selectByUsername(username);
            if (user == null || user.isDisabled()) {
                return Responses.failure("user not found or user is disabled. username: " + username);
            }
            Instant now = Instant.now();
            String loginId = jwt.getClaimAsString(CLAIM_LOGIN_ID);
            Instant loginTime = jwt.getClaimAsInstant(CLAIM_LOGIN_TIME);
            Instant authExpiresAt = jwt.getClaimAsInstant(CLAIM_AUTH_EXPIRES_AT);
            boolean remembered = now.compareTo(authExpiresAt) > 0;
            // 生成 Access Token
            String accessToken = service.createAccessToken(loginId, user.getUsername(), now, remembered);
            // 如果 Refresh Token 认证有效期未过期，且小于有效期，则续期 Refresh Token
            long refreshExpiresIn = properties.getRefreshExpiresSeconds() -
                    (now.getEpochSecond() - loginTime.getEpochSecond());
            long refreshAuthExpiresIn = params.browser ?
                    properties.getExpiresSeconds() : properties.getRefreshExpiresSeconds();
            if (refreshAuthExpiresIn > refreshExpiresIn) {
                refreshAuthExpiresIn = refreshExpiresIn;
            }
            Instant expiresAt = Optional.ofNullable(jwt.getExpiresAt()).orElse(now);
            boolean refreshIssued = authExpiresAt.compareTo(now) > 0 && authExpiresAt.compareTo(expiresAt) < 0;
            if (refreshIssued) {
                refreshToken = service.createRefreshToken(loginId, loginTime, user.getUsername(), now, params.browser);
            }
            // 只有 refresh token 刷新了才传递 refreshExpiresIn / refreshAuthExpiresIn
            JwtTokenResult result = new JwtTokenResult(
                    accessToken,
                    properties.getExpiresSeconds(),
                    remembered,
                    refreshToken,
                    refreshIssued ? refreshExpiresIn : null,
                    refreshIssued ? refreshAuthExpiresIn : null,
                    null,
                    null);
            return Responses.ok(result);
        } catch (BadJwtException e) {
            // 验证失败（过期、签名不匹配等）：不打印 token 与堆栈，避免泄露凭证并淹没日志
            logger.debug("refresh token JWT verification failed: {}", e.getMessage());
            return Responses.failure("refresh token JWT verification failed");
        }
    }

    private ConfigService configService;
    private UserService userService;
    private LoginLogService loginLogService;
    private SecondFactorProvider secondFactorProvider;
    private MfaTokenService mfaTokenService;
    private TwoFactorGuard twoFactorGuard;
    private SmsCodeSender smsCodeSender;
    private ShortMessageService shortMessageService;
    private PasswordEncoder passwordEncoder;
    private CaptchaTokenService captchaTokenService;
    private IpLoginAttemptService ipLoginAttemptService;

    @Autowired
    public void setConfigService(ConfigService configService) {
        this.configService = configService;
    }

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Autowired
    public void setLoginLogService(LoginLogService loginLogService) {
        this.loginLogService = loginLogService;
    }

    @Autowired
    public void setSecondFactorProvider(SecondFactorProvider secondFactorProvider) {
        this.secondFactorProvider = secondFactorProvider;
    }

    @Autowired
    public void setMfaTokenService(MfaTokenService mfaTokenService) {
        this.mfaTokenService = mfaTokenService;
    }

    @Autowired
    public void setTwoFactorGuard(TwoFactorGuard twoFactorGuard) {
        this.twoFactorGuard = twoFactorGuard;
    }

    @Autowired
    public void setSmsCodeSender(SmsCodeSender smsCodeSender) {
        this.smsCodeSender = smsCodeSender;
    }

    @Autowired
    public void setShortMessageService(ShortMessageService shortMessageService) {
        this.shortMessageService = shortMessageService;
    }

    @Autowired
    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Autowired
    public void setCaptchaTokenService(CaptchaTokenService captchaTokenService) {
        this.captchaTokenService = captchaTokenService;
    }

    @Autowired
    public void setIpLoginAttemptService(IpLoginAttemptService ipLoginAttemptService) {
        this.ipLoginAttemptService = ipLoginAttemptService;
    }

    @Schema(name = "JwtAuthController.LoginParams", description = "登录参数")
    public static class LoginParams {
        @NotNull
        @Schema(description = "用户名")
        public String username;
        @NotNull
        @Schema(description = "密码")
        public String password;
        @Schema(description = "图形验证码Token")
        public String captchaToken;
        @Schema(description = "图形验证码")
        public String captcha;
        @Schema(description = "是否浏览器访问")
        public boolean browser = true;
    }

    @Schema(name = "JwtAuthController.MfaSendParams", description = "发送双因子登录验证码参数")
    public static class MfaSendParams {
        @NotBlank
        @Schema(description = "双因子挑战令牌。口令校验通过后由登录接口下发")
        public String mfaToken;
    }

    @Schema(name = "JwtAuthController.MfaVerifyParams", description = "校验双因子登录验证码参数")
    public static class MfaVerifyParams {
        @NotBlank
        @Schema(description = "双因子挑战令牌。口令校验通过后由登录接口下发")
        public String mfaToken;
        @NotNull
        @Schema(description = "短信ID")
        public Long shortMessageId;
        @NotBlank
        @Schema(description = "短信验证码")
        public String code;
    }

    /**
     * 双因子挑战。口令校验通过后下发，此时才向用户提示验证码将发往哪个号码（脱敏）——只知道用户名的人看不到该提示。
     */
    @Schema(name = "JwtAuthController.SecondFactorChallenge", description = "双因子挑战")
    public record SecondFactorChallenge(
            @Schema(description = "双因子挑战令牌") String mfaToken,
            @Schema(description = "脱敏的手机号码") String maskedMobile) {
    }

    @Schema(name = "JwtAuthController.MfaSendResult", description = "发送双因子登录验证码响应")
    public record MfaSendResult(
            @Schema(description = "短信ID") Long shortMessageId,
            @Schema(description = "脱敏的手机号码") String maskedMobile,
            @Schema(description = "距下次可发送的剩余秒数") int cooldownSeconds,
            @Schema(description = "验证码长度") int codeLength) {
    }

    @Schema(name = "JwtAuthController.RefreshTokenParams", description = "RefreshToken参数")
    public static class RefreshTokenParams {
        @NotNull
        @Schema(description = "refreshToken")
        public String refreshToken;
        @Schema(description = "是否浏览器访问")
        public boolean browser = true;
    }

    /**
     * JWT 令牌响应体。字段名直接对应 {@link JwtProperties} 中的 JSON 键，保证
     * 与历史 {@code Map} 响应字节级一致。可为空字段（条件下发）使用包装类型 +
     * {@code @JsonInclude(NON_NULL)} 来还原原先 {@code result.put(...)} 仅在
     * 满足条件时塞入键的行为。
     */
    @Schema(name = "JwtAuthController.JwtTokenResult", description = "JWT 令牌响应")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JwtTokenResult(
            @Schema(description = "Access Token") String accessToken,
            @Schema(description = "Access Token 有效期（秒）") long expiresIn,
            @Schema(description = "是否记住登录") boolean remembered,
            @Schema(description = "Refresh Token") String refreshToken,
            @Schema(description = "Refresh Token 有效期（秒）") @Nullable Long refreshExpiresIn,
            @Schema(description = "Refresh Token 认证有效期（秒）") @Nullable Long refreshAuthExpiresIn,
            @Schema(description = "会话超时（秒）") @Nullable Long sessionTimeout,
            @Schema(description = "密码剩余有效天数（即将过期时下发）") @Nullable Integer remainingDays) {
    }
}
