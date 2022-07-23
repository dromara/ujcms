package com.ujcms.cms.core.web.api;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
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
import com.ujcms.util.security.CredentialsDigest;
import com.ujcms.util.security.Secures;
import com.ujcms.util.security.jwt.JwtProperties;
import com.ujcms.util.security.jwt.JwtUtils;
import com.ujcms.util.sms.SmsTokenService;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.Servlets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ujcms.util.security.jwt.JwtProperties.*;

/**
 * @author PONY
 */
@RestController
@RequestMapping(UrlConstants.API + "/auth/jwt")
public class JwtAuthController {
    private static Logger logger = LoggerFactory.getLogger(JwtAuthController.class);
    private Props props;

    public JwtAuthController(Props props, JwtProperties properties, Algorithm algorithm) {
        this.props = props;
        this.properties = properties;
        this.algorithm = algorithm;
        long leeway = properties.getLeeway();
        this.refreshTokenVerifier = JWT.require(algorithm).withIssuer(properties.getRefreshTokenIssuer())
                .acceptLeeway(leeway).build();
    }

    @PostMapping("/login")
    public ResponseEntity<Body> login(@RequestBody LoginParam params, HttpServletRequest request) {
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
                !captchaTokenService.validateCaptcha(params.captchaToken, params.captchaValue)) {
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
            ShortMessage shortMessage = Optional.ofNullable(smsTokenService.getId(params.shortMessageToken))
                    .map(shortMessageService::select).orElse(null);
            if (shortMessage == null) {
                loginLogService.loginFailure(params.username, ip, LoginLog.STATUS_SHORT_MESSAGE_WRONG);
                return Responses.failure(request, "error.shortMessageIncorrect");
            }
            // 是否过期、是否正确、是否已使用
            if (shortMessage.isExpired(sms.getCodeExpires()) || shortMessage.isUsed()
                    || shortMessage.isWrong(user.getMobile(), params.shortMessageValue)) {
                loginLogService.loginFailure(params.username, ip, LoginLog.STATUS_SHORT_MESSAGE_WRONG);
                shortMessageService.update(shortMessage);
                return Responses.failure(request, "error.shortMessageIncorrect");
            }
            shortMessage.setStatus(ShortMessage.STATUS_CORRECT);
            shortMessageService.update(shortMessage);
        }
        // 前台密码已通过SM2加密，此处进行解密
        String password = Secures.sm2Decrypt(params.password, props.getClientSm2PrivateKey());
        // 密码错误
        if (!credentialsDigest.matches(user.getPassword(), password, user.getSalt())) {
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
        Date now = new Date();
        String loginId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = createAccessToken(loginId, user.getId(), now, false);
        String refreshToken = createRefreshToken(loginId, now, user.getId(), now, params.browser);
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

    @PostMapping("/logout")
    public ResponseEntity<Body> logout(@RequestBody RefreshTokenParam params, HttpServletRequest request) {
        try {
            String refreshTokenEncrypted = params.refreshToken;
            String refreshToken = Secures.sm4Decrypt(refreshTokenEncrypted, properties.getTokenSecret());
            DecodedJWT jwt = refreshTokenVerifier.verify(refreshToken);
            // 可以获取 jwtId 从数据库等存储空间中验证 token 是否伪造
            Integer userId = Integer.valueOf(jwt.getSubject());
            User user = userService.select(userId);
            if (user == null) {
                return Responses.failure("user not found. id: " + userId);
            }
            loginLogService.logout(user.getId(), Servlets.getRemoteAddr(request));
            return Responses.ok();
        } catch (JWTVerificationException | BadPaddingException | IllegalBlockSizeException
                | IllegalArgumentException e) {
            // 验证失败
            String message = "refresh token JWT verification failed: " + params.refreshToken;
            logger.info(message, e);
            return Responses.failure(message);
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<Body> refreshToken(@RequestBody RefreshTokenParam params) {
        try {
            String refreshTokenEncrypted = params.refreshToken;
            String refreshToken = Secures.sm4Decrypt(refreshTokenEncrypted, properties.getTokenSecret());
            DecodedJWT jwt = refreshTokenVerifier.verify(refreshToken);
            // 可以获取 jwtId 从数据库等存储空间中验证 token 是否伪造
            Integer userId = Integer.valueOf(jwt.getSubject());
            User user = userService.select(userId);
            if (user == null || user.isDisabled()) {
                return Responses.failure("user not found or user is disabled. id: " + userId);
            }
            Date now = new Date();
            String loginId = JwtUtils.getLoginIdClaim(jwt);
            Date loginTime = JwtUtils.getLoginTimeClaim(jwt);
            long authExpiresMillisAt = JwtUtils.getAuthExpiresAtClaim(jwt).getTime();
            boolean remembered = now.getTime() > authExpiresMillisAt;
            // 生成 Access Token
            String accessToken = createAccessToken(JwtUtils.getLoginIdClaim(jwt), userId, now, remembered);
            // 如果 Refresh Token 认证有效期未过期，且小于有效期，则续期 Refresh Token
            long refreshExpiresIn = properties.getRefreshExpiresSeconds() -
                    (now.getTime() - loginTime.getTime()) / 1000;
            long refreshAuthExpiresIn = params.browser ?
                    properties.getExpiresSeconds() : properties.getRefreshExpiresSeconds();
            if (refreshAuthExpiresIn > refreshExpiresIn) {
                refreshAuthExpiresIn = refreshExpiresIn;
            }
            Map<String, Object> result = new HashMap<>(6);
            result.put(ACCESS_TOKEN, accessToken);
            result.put(EXPIRES_IN, properties.getExpiresSeconds());
            result.put(REMEMBERED, remembered);
            if (authExpiresMillisAt > now.getTime() && authExpiresMillisAt < jwt.getExpiresAt().getTime()) {
                refreshToken = createRefreshToken(loginId, loginTime, userId, now, params.browser);
                // 只有 refresh token 刷新了才传递以下参数
                result.put(REFRESH_EXPIRES_IN, refreshExpiresIn);
                result.put(REFRESH_AUTH_EXPIRES_IN, refreshAuthExpiresIn);
            }
            result.put(REFRESH_TOKEN, refreshToken);
            return Responses.ok(result);
        } catch (JWTVerificationException | BadPaddingException | IllegalBlockSizeException
                | IllegalArgumentException e) {
            // 验证失败
            String message = "refresh token JWT verification failed: " + params.refreshToken;
            logger.info(message, e);
            return Responses.failure(message);
        }
    }

    private String createAccessToken(String loginId, Integer userId, Date now, boolean remembered) {
        Date expiresAt = new Date(now.getTime() + properties.getExpiresMillis());
        JWTCreator.Builder builder = JWT.create().withSubject(String.valueOf(userId)).withIssuedAt(now)
                .withExpiresAt(expiresAt).withIssuer(properties.getAccessTokenIssuer());
        JwtUtils.withLoginIdClaim(builder, loginId);
        JwtUtils.withRememberedClaim(builder, remembered);
        String sign = builder.sign(algorithm);
        return Secures.sm4Encrypt(sign, properties.getTokenSecret());
    }

    private String createRefreshToken(String loginId, Date loginTime, long userId, Date now, boolean isBrowser) {
        Date expiresAt = new Date(loginTime.getTime() + properties.getRefreshExpiresMillis());
        // Access Token 有效期较短（如30分钟），需要使用 Refresh Token 维持登录状态，因此可以用于记录、监控用户活动状态
        JWTCreator.Builder builder = JWT.create().withSubject(String.valueOf(userId))
                .withIssuer(properties.getRefreshTokenIssuer()).withIssuedAt(now).withExpiresAt(expiresAt);
        // 如果是浏览器访问则 Refresh Token 认证有效期与 Access Token 有效期一样
        Date authExpiresAt = new Date(now.getTime() +
                (isBrowser ? properties.getExpiresMillis() : properties.getRefreshAuthExpiresMillis()));
        if (authExpiresAt.getTime() > expiresAt.getTime()) {
            authExpiresAt = expiresAt;
        }
        JwtUtils.withAuthExpiresAtClaim(builder, authExpiresAt);
        JwtUtils.withLoginIdClaim(builder, loginId);
        JwtUtils.withLoginTimeClaim(builder, loginTime);
        String sign = builder.sign(algorithm);
        return Secures.sm4Encrypt(sign, properties.getTokenSecret());
    }

    private JwtProperties properties;
    private Algorithm algorithm;
    /**
     * 验证 Refresh Token
     */
    private JWTVerifier refreshTokenVerifier;

    private ConfigService configService;
    private UserService userService;
    private LoginLogService loginLogService;
    private ShortMessageService shortMessageService;
    private CredentialsDigest credentialsDigest;
    private CaptchaTokenService captchaTokenService;
    private SmsTokenService smsTokenService;
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
    public void setCredentialsDigest(CredentialsDigest credentialsDigest) {
        this.credentialsDigest = credentialsDigest;
    }

    @Autowired
    public void setCaptchaTokenService(CaptchaTokenService captchaTokenService) {
        this.captchaTokenService = captchaTokenService;
    }

    @Autowired
    public void setSmsTokenService(SmsTokenService smsTokenService) {
        this.smsTokenService = smsTokenService;
    }

    @Autowired
    public void setIpLoginAttemptService(IpLoginAttemptService ipLoginAttemptService) {
        this.ipLoginAttemptService = ipLoginAttemptService;
    }

    public static class LoginParam {
        public String username;
        public String password;
        public String captchaToken;
        public String captchaValue;
        public String shortMessageToken;
        public String shortMessageValue;
        // 是否浏览器访问
        private boolean browser = true;
    }

    public static class RefreshTokenParam {
        public String refreshToken;
        // 是否浏览器访问
        public boolean browser = true;
    }
}
