package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.web.api.JwtAuthController.JwtTokenResult;
import com.ujcms.common.captcha.IpLoginAttemptService;
import com.ujcms.common.security.jwt.JwtProperties;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Servlets;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.ujcms.common.security.jwt.JwtUtils.*;

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
    private final Props props;

    public JwtAuthService(JwtProperties properties, JwtEncoder jwtEncoder, LoginLogService loginLogService,
                          UserService userService, IpLoginAttemptService ipLoginAttemptService, Props props) {
        this.properties = properties;
        this.jwtEncoder = jwtEncoder;
        this.loginLogService = loginLogService;
        this.userService = userService;
        this.ipLoginAttemptService = ipLoginAttemptService;
        this.props = props;
    }

    public ResponseEntity<Responses.Body> incorrectPassword(User user, String username, String ip, Config.Security security, HttpServletRequest request) {
        // 记录IP登录错误次数
        ipLoginAttemptService.failure(Servlets.getRemoteAddr(request, props.getIpProxyDepth()));
        // 记录用户错误次数
        userService.loginFailure(user.getExt(), security.getUserLockMinutes());
        loginLogService.loginFailure(user.getId(), username, ip, LoginLog.STATUS_PASSWORD_WRONG);
        int maxAttempts = security.getUserMaxAttempts();
        if (maxAttempts > 0) {
            return Responses.failure(request, "error.passwordIncorrectAndWarning", maxAttempts - user.getErrorCount());
        }
        return Responses.failure(request, "error.passwordIncorrect");
    }

    /**
     * 第二因子（短信验证码）错误。与口令错误一样计入 IP 与用户的登录失败次数：否则攻击者拿到口令后，
     * 可以不受锁定约束地反复猜测验证码。
     */
    public void secondFactorFailure(User user, String username, String ip, Config.Security security) {
        ipLoginAttemptService.failure(ip);
        userService.loginFailure(user.getExt(), security.getUserLockMinutes());
        loginLogService.loginFailure(user.getId(), username, ip, LoginLog.STATUS_SHORT_MESSAGE_WRONG);
    }

    /**
     * 完成登录：签发令牌、清零登录失败计数、记录登录成功。必须在<b>全部</b>认证因子通过之后调用。
     * <p>
     * 双因子登录的第一步（口令已通过、等待第二因子）不得调用：清零失败计数会让持有口令者在两次验证码猜测之间重置锁定计数。
     */
    public ResponseEntity<Responses.Body> completeLogin(User user, String username, String ip, boolean isBrowser,
                                                        Config.Security security) {
        JwtTokenResult result = createTokenResult(user, isBrowser, security);
        ipLoginAttemptService.success(ip);
        userService.loginSuccess(user.getExt(), ip);
        loginLogService.loginSuccess(user.getId(), username, ip);
        return Responses.ok(result);
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

    public JwtTokenResult createTokenResult(User user, boolean isBrowser) {
        return createTokenResult(user, isBrowser, null);
    }

    public JwtTokenResult createTokenResult(User user, boolean isBrowser, Config.@Nullable Security security) {
        Instant now = Instant.now();
        String loginId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = createAccessToken(loginId, user.getUsername(), now, false);
        String refreshToken = createRefreshToken(loginId, now, user.getUsername(), now, isBrowser);
        long refreshAuthExpiresIn = isBrowser
                ? properties.getExpiresSeconds()
                : properties.getRefreshAuthExpiresSeconds();
        Integer remainingDays = null;
        if (security != null) {
            int warnDays = security.getPasswordWarnDays();
            int maxDays = security.getPasswordMaxDays();
            int remaining = user.getPasswordRemainingDays(maxDays);
            if (maxDays > 0 && warnDays > 0 && remaining <= warnDays) {
                remainingDays = remaining;
            }
        }
        return new JwtTokenResult(
                accessToken,
                properties.getExpiresSeconds(),
                false,
                refreshToken,
                (long) properties.getRefreshExpires(),
                refreshAuthExpiresIn,
                (long) properties.getSessionTimeout(),
                remainingDays);
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
