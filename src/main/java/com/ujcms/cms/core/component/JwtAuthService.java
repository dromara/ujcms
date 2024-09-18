package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.commons.captcha.IpLoginAttemptService;
import com.ujcms.commons.security.jwt.JwtProperties;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Servlets;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.ujcms.commons.security.jwt.JwtProperties.*;
import static com.ujcms.commons.security.jwt.JwtUtils.*;

/**
 * JWT认证 Service
 *
 * @author PONY
 */
@Component
public class JwtAuthService {
    private final JwtProperties properties;
    private final JwtEncoder jwtEncoder;
    private final LoginLogService loginLogService;
    private final UserService userService;
    private final IpLoginAttemptService ipLoginAttemptService;

    public JwtAuthService(JwtProperties properties, JwtEncoder jwtEncoder, LoginLogService loginLogService,
                          UserService userService, IpLoginAttemptService ipLoginAttemptService) {
        this.properties = properties;
        this.jwtEncoder = jwtEncoder;
        this.loginLogService = loginLogService;
        this.userService = userService;
        this.ipLoginAttemptService = ipLoginAttemptService;
    }

    public ResponseEntity<Responses.Body> incorrectPassword(User user, String username, String ip, Config.Security security, HttpServletRequest request) {
        // 记录IP登录错误次数
        ipLoginAttemptService.failure(Servlets.getRemoteAddr(request));
        // 记录用户错误次数
        userService.loginFailure(user.getExt(), security.getUserLockMinutes());
        loginLogService.loginFailure(user.getId(), username, ip, LoginLog.STATUS_PASSWORD_WRONG);
        int maxAttempts = security.getUserMaxAttempts();
        if (maxAttempts > 0) {
            return Responses.failure(request, "error.passwordIncorrectAndWarning", maxAttempts - user.getErrorCount());
        }
        return Responses.failure(request, "error.passwordIncorrect");
    }

    public ResponseEntity<Responses.Body> disabledUser(
            User user, String username, String ip, HttpServletRequest request) {
        // 用户未激活
        if (user.isUnactivated()) {
            loginLogService.loginFailure(user.getId(), username, ip, LoginLog.STATUS_USER_INACTIVATED);
            return Responses.failure(request, "error.userUnactivated");
        }
        // 用户已锁定
        if (user.isLocked()) {
            loginLogService.loginFailure(user.getId(), username, ip, LoginLog.STATUS_USER_LOCKED);
            return Responses.failure(request, "error.userLocked");
        }
        // 用户已注销
        if (user.isCancelled()) {
            loginLogService.loginFailure(user.getId(), username, ip, LoginLog.STATUS_USER_CANCELLED);
            return Responses.failure(request, "error.userCancelled");
        }
        loginLogService.loginFailure(user.getId(), username, ip, LoginLog.STATUS_USER_DISABLED);
        return Responses.failure(request, "error.userDisabled");
    }

    public Map<String, Object> createResultMap(User user, boolean isBrowser) {
        Instant now = Instant.now();
        String loginId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = createAccessToken(loginId, user.getUsername(), now, false);
        String refreshToken = createRefreshToken(loginId, now, user.getUsername(), now, isBrowser);
        Map<String, Object> result = new HashMap<>(6);
        result.put(ACCESS_TOKEN, accessToken);
        result.put(EXPIRES_IN, properties.getExpiresSeconds());
        result.put(REMEMBERED, false);
        result.put(REFRESH_TOKEN, refreshToken);
        result.put(REFRESH_EXPIRES_IN, properties.getRefreshExpires());
        result.put(REFRESH_AUTH_EXPIRES_IN, isBrowser ?
                properties.getExpiresSeconds() : properties.getRefreshAuthExpiresSeconds());
        result.put(SESSION_TIMEOUT, properties.getSessionTimeout());
        return result;
    }

    public Map<String, Object> createResultMap(User user, boolean isBrowser, Config.Security security) {
        // 生成 Access Token
        Map<String, Object> result = createResultMap(user, isBrowser);
        int warnDays = security.getPasswordWarnDays();
        int maxDays = security.getPasswordMaxDays();
        int remainingDays = user.getPasswordRemainingDays(maxDays);
        if (maxDays > 0 && warnDays > 0 && remainingDays <= warnDays) {
            result.put(REMAINING_DAYS, remainingDays);
        }
        return result;
    }

    public String createAccessToken(String loginId, String username, Instant now, boolean remembered) {
        Instant expiresAt = now.plusMillis(properties.getExpiresMillis());
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder().issuer(properties.getAccessTokenIssuer())
                .issuedAt(now).expiresAt(expiresAt).subject(username)
                .claim(CLAIM_LOGIN_ID, loginId).claim(CLAIM_REMEMBERED, remembered)
                .build();
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, jwtClaimsSet));
        return jwt.getTokenValue();
    }

    public String createRefreshToken(String loginId, Instant loginTime, String username, Instant now, boolean isBrowser) {
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
}
