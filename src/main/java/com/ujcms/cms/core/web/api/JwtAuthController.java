package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.captcha.CaptchaTokenService;
import com.ujcms.util.captcha.IpLoginAttemptService;
import com.ujcms.util.security.Secures;
import com.ujcms.util.security.jwt.JwtProperties;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.Servlets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ujcms.util.security.jwt.JwtProperties.*;
import static com.ujcms.util.security.jwt.JwtUtils.*;

/**
 * @author PONY
 */
@Tag(name = "JwtAuthController", description = "JWT登录接口")
@RestController
@RequestMapping(UrlConstants.API + "/auth/jwt")
public class JwtAuthController {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthController.class);

    private final Props props;

    public JwtAuthController(Props props, JwtProperties properties, SecretKey secretKey, JwtEncoder jwtEncoder) {
        this.props = props;
        this.properties = properties;
        this.jwtEncoder = jwtEncoder;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getRefreshTokenIssuer()));
        this.refreshTokenDecoder = decoder;
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ResponseEntity<Body> login(@RequestBody @Valid LoginParams params, HttpServletRequest request) {
        Config config = configService.getUnique();
        Config.Security security = config.getSecurity();
        Config.Sms sms = config.getSms();
        String ip = Servlets.getRemoteAddr(request);
        // IP登录失败超过限制次数
        if (ipLoginAttemptService.isExcessive(ip, security.getIpMaxAttempts())) {
            loginLogService.loginFailure(params.username, ip, LoginLog.STATUS_IP_EXCESSIVE_ATTEMPTS);
            return Responses.failure(request, "error.ipExcessiveAttempts");
        }
        // 是否需要验证码
        if (!security.isTwoFactor() && ipLoginAttemptService.isExcessive(ip, security.getIpCaptchaAttempts()) &&
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
        // 用户密码是否过期
        if (user.isPasswordExpired(security.getPasswordMaxDays())) {
            loginLogService.loginFailure(user.getId(), params.username, ip, LoginLog.STATUS_PASSWORD_EXPIRED);
            return Responses.failure(request, "error.passwordExpired");
        }
        // 双因子认证是否通过
        if (security.isTwoFactor()) {
            if (!shortMessageService.validateCode(params.shortMessageId, user.getMobile(), params.shortMessageValue,
                    sms.getCodeExpires())) {
                loginLogService.loginFailure(params.username, ip, LoginLog.STATUS_SHORT_MESSAGE_WRONG);
                return Responses.failure(request, "error.mobileMessageIncorrect");
            }
        }
        // 前台密码已通过SM2加密，此处进行解密
        String password = Secures.sm2Decrypt(params.password, props.getClientSm2PrivateKey());
        // 密码错误
        if (!passwordEncoder.matches(password, user.getPassword())) {
            // 记录IP登录错误次数
            ipLoginAttemptService.failure(Servlets.getRemoteAddr(request));
            // 记录用户错误次数
            userService.loginFailure(user.getExt(), security.getUserLockMinutes());
            loginLogService.loginFailure(user.getId(), params.username, ip, LoginLog.STATUS_PASSWORD_WRONG);
            int maxAttempts = security.getUserMaxAttempts();
            if (maxAttempts > 0) {
                return Responses.failure(request, "error.passwordIncorrectAndWarning", maxAttempts - user.getErrorCount());
            }
            return Responses.failure(request, "error.passwordIncorrect");
        }
        // 非正常用户
        if (user.isDisabled()) {
            // 用户未激活
            if (user.isInactivated()) {
                loginLogService.loginFailure(user.getId(), params.username, ip, LoginLog.STATUS_USER_INACTIVATED);
                return Responses.failure(request, "error.userInactivated");
            }
            // 用户已锁定
            if (user.isLocked()) {
                loginLogService.loginFailure(user.getId(), params.username, ip, LoginLog.STATUS_USER_LOCKED);
                return Responses.failure(request, "error.userLocked");
            }
            // 用户已注销
            if (user.isCancelled()) {
                loginLogService.loginFailure(user.getId(), params.username, ip, LoginLog.STATUS_USER_CANCELLED);
                return Responses.failure(request, "error.userCancelled");
            }
            loginLogService.loginFailure(user.getId(), params.username, ip, LoginLog.STATUS_USER_DISABLED);
            return Responses.failure(request, "error.userDisabled");
        }
        // 生成 Access Token
        Instant now = Instant.now();
        String loginId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = createAccessToken(loginId, user.getUsername(), now, false);
        String refreshToken = createRefreshToken(loginId, now, user.getUsername(), now, params.browser);
        Map<String, Object> result = new HashMap<>(6);
        result.put(ACCESS_TOKEN, accessToken);
        result.put(EXPIRES_IN, properties.getExpiresSeconds());
        result.put(REMEMBERED, false);
        result.put(REFRESH_TOKEN, refreshToken);
        result.put(REFRESH_EXPIRES_IN, properties.getRefreshExpires());
        result.put(REFRESH_AUTH_EXPIRES_IN, params.browser ?
                properties.getExpiresSeconds() : properties.getRefreshAuthExpiresSeconds());
        result.put(SESSION_TIMEOUT, properties.getSessionTimeout());
        int warnDays = security.getPasswordWarnDays();
        int maxDays = security.getPasswordMaxDays();
        int remainingDays = user.getPasswordRemainingDays(maxDays);
        if (maxDays > 0 && warnDays > 0 && remainingDays <= warnDays) {
            result.put(REMAINING_DAYS, remainingDays);
        }
        ipLoginAttemptService.success(ip);
        userService.loginSuccess(user.getExt(), Servlets.getRemoteAddr(request));
        loginLogService.loginSuccess(user.getId(), params.username, ip);
        return Responses.ok(result);
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
            loginLogService.logout(user.getId(), Servlets.getRemoteAddr(request));
            return Responses.ok();
        } catch (BadJwtException e) {
            // 验证失败
            String message = "refresh token JWT verification failed: " + params.refreshToken;
            logger.info(message, e);
            return Responses.failure(message);
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
            String accessToken = createAccessToken(loginId, user.getUsername(), now, remembered);
            // 如果 Refresh Token 认证有效期未过期，且小于有效期，则续期 Refresh Token
            long refreshExpiresIn = properties.getRefreshExpiresSeconds() -
                    (now.getEpochSecond() - loginTime.getEpochSecond());
            long refreshAuthExpiresIn = params.browser ?
                    properties.getExpiresSeconds() : properties.getRefreshExpiresSeconds();
            if (refreshAuthExpiresIn > refreshExpiresIn) {
                refreshAuthExpiresIn = refreshExpiresIn;
            }
            Map<String, Object> result = new HashMap<>(6);
            result.put(ACCESS_TOKEN, accessToken);
            result.put(EXPIRES_IN, properties.getExpiresSeconds());
            result.put(REMEMBERED, remembered);
            Instant expiresAt = Optional.ofNullable(jwt.getExpiresAt()).orElse(now);
            if (authExpiresAt.compareTo(now) > 0 && authExpiresAt.compareTo(expiresAt) < 0) {
                refreshToken = createRefreshToken(loginId, loginTime, user.getUsername(), now, params.browser);
                // 只有 refresh token 刷新了才传递以下参数
                result.put(REFRESH_EXPIRES_IN, refreshExpiresIn);
                result.put(REFRESH_AUTH_EXPIRES_IN, refreshAuthExpiresIn);
            }
            result.put(REFRESH_TOKEN, refreshToken);
            return Responses.ok(result);
        } catch (BadJwtException e) {
            // 验证失败
            String message = "refresh token JWT verification failed: " + params.refreshToken;
            logger.info(message, e);
            return Responses.failure(message);
        }
    }

    private String createAccessToken(String loginId, String username, Instant now, boolean remembered) {
        Instant expiresAt = now.plusMillis(properties.getExpiresMillis());
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder().issuer(properties.getAccessTokenIssuer())
                .issuedAt(now).expiresAt(expiresAt).subject(username)
                .claim(CLAIM_LOGIN_ID, loginId).claim(CLAIM_REMEMBERED, remembered)
                .build();
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, jwtClaimsSet));
        return jwt.getTokenValue();
    }

    private String createRefreshToken(String loginId, Instant loginTime, String username, Instant now, boolean isBrowser) {
        Instant expiresAt = loginTime.plusMillis(properties.getRefreshExpiresMillis());
        // Access Token 有效期较短（如30分钟），需要使用 Refresh Token 维持登录状态，因此可以用于记录、监控用户活动状态
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet.Builder jwtClaimsBuilder = JwtClaimsSet.builder().issuer(properties.getRefreshTokenIssuer())
                .issuedAt(now).expiresAt(expiresAt).subject(username);
        // 如果是浏览器访问则 Refresh Token 认证有效期与 Access Token 有效期一样
        Instant authExpiresAt = now.plusMillis(isBrowser ?
                properties.getExpiresMillis() : properties.getRefreshAuthExpiresMillis());
        if (authExpiresAt.compareTo(expiresAt) > 0) {
            authExpiresAt = expiresAt;
        }
        jwtClaimsBuilder.claim(CLAIM_AUTH_EXPIRES_AT, new Date(authExpiresAt.toEpochMilli()));
        jwtClaimsBuilder.claim(CLAIM_LOGIN_ID, loginId);
        jwtClaimsBuilder.claim(CLAIM_LOGIN_TIME, new Date(loginTime.toEpochMilli()));

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, jwtClaimsBuilder.build()));
        return jwt.getTokenValue();
    }

    private JwtProperties properties;
    private JwtEncoder jwtEncoder;
    private JwtDecoder refreshTokenDecoder;

    private ConfigService configService;
    private UserService userService;
    private LoginLogService loginLogService;
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
        @Schema(description = "短信ID")
        public Integer shortMessageId;
        @Schema(description = "短信验证码")
        public String shortMessageValue;
        @Schema(description = "是否浏览器访问")
        private boolean browser = true;
    }

    @Schema(name = "JwtAuthController.RefreshTokenParams", description = "RefreshToken参数")
    public static class RefreshTokenParams {
        @NotNull
        @Schema(description = "refreshToken")
        public String refreshToken;
        @Schema(description = "是否浏览器访问")
        public boolean browser = true;
    }
}
