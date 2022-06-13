package com.ujcms.cms.core.web.api;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.security.CredentialsDigest;
import com.ujcms.util.security.Secures;
import com.ujcms.util.security.jwt.JwtProperties;
import com.ujcms.util.security.jwt.JwtUtils;
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
        User user = userService.selectByUsername(params.getUsername());
        // 用户名不存在
        if (user == null) {
            return Responses.failure(request, "error.usernameNotExist");
        }
        // 前台密码已通过SM2加密，此处进行解密
        String password = Secures.sm2Decrypt(params.getPassword(), props.getClientSm2PrivateKey());
        // 密码错误
        if (!credentialsDigest.matches(user.getPassword(), password, user.getSalt())) {
            return Responses.failure(request, "error.passwordIncorrect");
        }
        // 用户被锁定
        if (user.isLocked()) {
            return Responses.failure(request, "error.userLocked");
        }
        // 非正常用户
        if (user.isDisabled()) {
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
        userService.updateLogin(user.getExt(), Servlets.getRemoteAddr(request));
        return Responses.ok(result);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<Body> refreshToken(@RequestBody RefreshTokenParam params) {
        try {
            String refreshToken = params.getRefreshToken();
            refreshToken = Secures.sm4Decrypt(refreshToken, properties.getTokenSecret());
            DecodedJWT jwt = refreshTokenVerifier.verify(refreshToken);
            // 可以获取 jwtId 从数据库等存储空间中验证 token 是否伪造
            Integer userId = Integer.valueOf(jwt.getSubject());
            User user = userService.select(userId);
            if (user == null || user.isDisabled()) {
                return null;
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
        } catch (JWTVerificationException | BadPaddingException | IllegalBlockSizeException e) {
            // 验证失败
            String message = "refresh token JWT verification failed: " + params.refreshToken;
            logger.debug(message, e);
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

    private UserService userService;
    private CredentialsDigest credentialsDigest;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Autowired
    public void setCredentialsDigest(CredentialsDigest credentialsDigest) {
        this.credentialsDigest = credentialsDigest;
    }

    public static class LoginParam {
        private String username;
        private String password;
        // 是否浏览器访问
        private boolean browser = true;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isBrowser() {
            return browser;
        }

        public void setBrowser(boolean browser) {
            this.browser = browser;
        }
    }

    public static final class RefreshTokenParam {
        private String refreshToken;
        // 是否浏览器访问
        private boolean browser = true;

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public boolean isBrowser() {
            return browser;
        }

        public void setBrowser(boolean browser) {
            this.browser = browser;
        }
    }
}
