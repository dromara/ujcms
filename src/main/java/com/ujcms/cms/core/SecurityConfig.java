package com.ujcms.cms.core;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.owasp.html.CssSchema;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.examples.EbayPolicyExample;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.security.EncryptedPasswordLoginConfigurer;
import com.ujcms.cms.core.security.UserDetailsServiceImpl;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.web.support.ExceptionResolver;
import com.ujcms.commons.captcha.CaptchaTokenService;
import com.ujcms.commons.captcha.IpLoginAttemptService;
import com.ujcms.commons.security.Pbkdf2WithHmacSm3PasswordEncoder;
import com.ujcms.commons.security.jwt.JwtProperties;
import com.ujcms.commons.web.JspDispatcherFilter;

import jakarta.servlet.Filter;

/**
 * 安全配置
 *
 * @author PONY
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecretKey secretKey(JwtProperties jwtProperties) {
        return new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSM3");
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey(jwtProperties)).build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtProperties.getAccessTokenIssuer()));
        return jwtDecoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
        JWK jwk = new OctetSequenceKey.Builder(secretKey(jwtProperties)).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(UserService userService) {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String username = jwt.getSubject();
            User user = userService.selectByUsername(username);
            // 如果修改了用户名，user可能为null
            if (user == null) {
                return Collections.emptyList();
            }
            return user.getAuthorities();
        });
        return jwtAuthenticationConverter;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain backendApiFilterChain(HttpSecurity http) throws Exception {
        // 所有 /api/backend 开头的请求为后台请求，必须登录才可访问
        http.securityMatcher(BACKEND_API + "/**").authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        // 所有 /api 开头的请求都使用JWT权鉴
        http.securityMatcher(API + "/**")
                // JWT权鉴需关闭CSRF防护
                .csrf(AbstractHttpConfigurer::disable).oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                // JWT权鉴需关闭Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        // .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
                        // 使用Exception中的message信息
                        .accessDeniedHandler((request, response, ex) -> response.sendError(HttpStatus.FORBIDDEN.value(),
                                ExceptionResolver.getMessage(ex, request))));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain frontendFilterChain(HttpSecurity http, DataSource dataSource, Props props,
                                                   ConfigService configService, LoginLogService loginLogService, CaptchaTokenService captchaTokenService,
                                                   IpLoginAttemptService ipLoginAttemptService) throws Exception {
        // 无后缀的请求
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .rememberMe(rememberMe -> rememberMe.tokenRepository(persistentTokenRepository(dataSource)))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // 使用Exception中的message信息。AccessDeniedHandlerImpl不使用Exception中的message信息会丢失
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, ex) -> response
                        .sendError(HttpStatus.FORBIDDEN.value(), ExceptionResolver.getMessage(ex, request))))
                .with(new EncryptedPasswordLoginConfigurer<>(props, configService, loginLogService,
                        captchaTokenService, ipLoginAttemptService), Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(Props props) {
        String pepper = props.getPasswordPepper();
        Pbkdf2WithHmacSm3PasswordEncoder pbkdf2WithHmacSm3PasswordEncoder = StringUtils.isNotBlank(pepper)
                ? new Pbkdf2WithHmacSm3PasswordEncoder(pepper)
                : new Pbkdf2WithHmacSm3PasswordEncoder();
        String defaultEncoder = "pbkdf2";
        Map<String, PasswordEncoder> encoders = new HashMap<>(16);
        encoders.put(defaultEncoder, pbkdf2WithHmacSm3PasswordEncoder);
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        DelegatingPasswordEncoder delegatingPasswordEncoder = new DelegatingPasswordEncoder(defaultEncoder, encoders);
        // 对于没有设置加密标识的密码，使用默认密码处理器。
        // 默认密码处理器是必须的，无密码的情况下（第三方注册或默认无密码时），密码字段为"0"；如果不设置该项会导致报错。
        delegatingPasswordEncoder.setDefaultPasswordEncoderForMatches(pbkdf2WithHmacSm3PasswordEncoder);
        return delegatingPasswordEncoder;
    }

    @Bean
    public UserDetailsServiceImpl userDetailsService(UserService userService) {
        return new UserDetailsServiceImpl(userService);
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl tokenRepository = new JdbcTokenRepositoryImpl();
        tokenRepository.setDataSource(dataSource);
        return tokenRepository;
    }

    /**
     * HTML过滤器。防止跨站攻击。
     */
    @Bean
    public PolicyFactory policyFactory() {
        return EbayPolicyExample.POLICY_DEFINITION.and(new HtmlPolicyBuilder()
                // 允许视频元素
                .allowElements("video").allowStandardUrlProtocols()
                .allowAttributes("controls", "preload", "width", "height", "src").onElements("video")
                .allowElements("audio").allowStandardUrlProtocols()
                .allowAttributes("controls", "preload", "width", "height", "src").onElements("audio")
                .allowElements("source").allowStandardUrlProtocols().allowAttributes("src", "type").onElements("source")
                .allowElements("a").allowAttributes("target").matching(true, "_blank").onElements("a")
                // TuiEditor 自带的，所以需要允许。
                .allowElements("code").allowAttributes("data-backticks").onElements("code").allowElements("img")
                .allowAttributes("contenteditable").onElements("img")

                // 允许浮动样式
                .allowStyling(CssSchema.withProperties(ImmutableSet.of("float")))
                // tinymce 编辑器的图片居中要使用到display:block，而display:none可以隐形内容，所以只允许display:block。
                .allowStyling(CssSchema.withProperties(ImmutableMap.of("display",
                        new CssSchema.Property(0, ImmutableSet.of("block"), ImmutableMap.of()))))
                .skipRelsOnLinks("nofollow").toFactory());
    }

    /**
     * 禁止 JSP 访问。如果 spring.h2.console 开启，则禁用该过滤器，因为 H2 控制台需要访问jsp。
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.h2.console", name = "enabled", havingValue = "false", matchIfMissing = true)
    public FilterRegistrationBean<Filter> jspDispatcherFilterRegistrationBean(Props props) {
        FilterRegistrationBean<Filter> filterRegistration = new FilterRegistrationBean<>(new JspDispatcherFilter());
        filterRegistration.setEnabled(true);
        filterRegistration.addUrlPatterns("*.jsp");
        filterRegistration.addUrlPatterns("*.jspx");
        if (props.isJspAllowed()) {
            filterRegistration.addInitParameter("allowed", "true");
        }
        return filterRegistration;
    }
}
