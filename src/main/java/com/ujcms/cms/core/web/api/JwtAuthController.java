package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.component.JwtAuthService;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.commons.captcha.CaptchaTokenService;
import com.ujcms.commons.captcha.IpLoginAttemptService;
import com.ujcms.commons.security.Secures;
import com.ujcms.commons.security.jwt.JwtProperties;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.Servlets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.commons.security.jwt.JwtProperties.*;
import static com.ujcms.commons.security.jwt.JwtUtils.*;

/**
 * @author PONY
 */
@Tag(name = "JWT认证接口")
@RestController
@RequestMapping(UrlConstants.API + "/auth/jwt")
public class JwtAuthController {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthController.class);

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
        if (security.isTwoFactor()
                && !shortMessageService.validateCode(params.shortMessageId, user.getMobile(),
                params.shortMessageValue, sms.getCodeExpires())) {
            loginLogService.loginFailure(params.username, ip, LoginLog.STATUS_SHORT_MESSAGE_WRONG);
            return Responses.failure(request, "error.mobileMessageIncorrect");
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
        // 生成 Access Token
        Map<String, Object> result = service.createResultMap(user, params.browser, security);
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
            String accessToken = service.createAccessToken(loginId, user.getUsername(), now, remembered);
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
                refreshToken = service.createRefreshToken(loginId, loginTime, user.getUsername(), now, params.browser);
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
        public Long shortMessageId;
        @Schema(description = "短信验证码")
        public String shortMessageValue;
        @Schema(description = "是否浏览器访问")
        public boolean browser = true;
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
