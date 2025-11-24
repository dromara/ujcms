package com.ujcms.cms.core.web.frontend;

import java.io.IOException;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.commons.security.jwt.JwtProperties;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.exception.Http400Exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 将JWT认证重定向至前台
 *
 * @author PONY
 * @see org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter#successfulAuthentication
 *      (HttpServletRequest, HttpServletResponse, Authentication)
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

    @GetMapping({ "/auth/jwt/login", "/{subDir:[\\w-]+}/auth/jwt/login" })
    public void authorize(String code, String redirectUri, @PathVariable(required = false) String subDir,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            Jwt jwt = tokenDecoder.decode(code);
            String username = jwt.getSubject();
            User user = userService.selectByUsername(username);
            SecurityContext context = getSecurityContext(user, username);
            contextRepository.saveContext(context, request, response);
            Servlets.sendRedirect(request, response, redirectUri);
        } catch (BadJwtException e) {
            // 验证失败
            String message = "access token JWT verification failed: " + code;
            logger.info(message, e);
            throw new Http400Exception(message);
        }
    }

    @NonNull
    private static SecurityContext getSecurityContext(User user, String username) {
        if (user == null || user.isDisabled()) {
            throw new Http400Exception("user not found or user is disabled. username: " + username);
        }
        PreAuthenticatedAuthenticationToken authResult = new PreAuthenticatedAuthenticationToken(user.getUsername(),
                "JWT", user.getAuthorities());
        SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authResult);
        contextHolderStrategy.setContext(context);
        return context;
    }

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    private UserService userService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }
}
