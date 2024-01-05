package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.commons.security.jwt.JwtProperties;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.exception.Http400Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 将JWT认证重定向至前台
 *
 * @author PONY
 */
@Controller("frontendJwtAuthController")
@RequestMapping
public class JwtAuthController {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthController.class);

    private final JwtDecoder tokenDecoder;

    public JwtAuthController(JwtProperties properties, SecretKey secretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getAccessTokenIssuer()));
        this.tokenDecoder = decoder;
    }

    @GetMapping({"/auth/jwt/login", "/[\\w-]+/auth/jwt/login"})
    public void authorize(String code, String redirectUri, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            Jwt jwt = tokenDecoder.decode(code);
            String username = jwt.getSubject();
            User user = userService.selectByUsername(username);
            if (user == null || user.isDisabled()) {
                throw new Http400Exception("user not found or user is disabled. username: " + username);
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            PreAuthenticatedAuthenticationToken authResult = new PreAuthenticatedAuthenticationToken(
                    user.getUsername(), "JWT", user.getAuthorities());
            context.setAuthentication(authResult);
            SecurityContextHolder.setContext(context);
            Servlets.sendRedirect(request, response, redirectUri);
        } catch (BadJwtException e) {
            // 验证失败
            String message = "access token JWT verification failed: " + code;
            logger.info(message, e);
            throw new Http400Exception(message);
        }
    }

    private UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }
}
