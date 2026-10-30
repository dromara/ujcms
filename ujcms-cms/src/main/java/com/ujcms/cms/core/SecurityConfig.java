package com.ujcms.cms.core;

import static com.ujcms.cms.core.support.UrlConstants.API;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.owasp.html.PolicyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
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
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.ujcms.cms.core.component.CaptchaGuard;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.security.AdminAccessWindowFilter;
import com.ujcms.cms.core.security.AdminIpWhitelistFilter;
import com.ujcms.cms.core.security.EncryptedPasswordLoginConfigurer;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.security.PolicyTier;
import com.ujcms.cms.core.security.PreviewBridgeInjectionFilter;
import com.ujcms.cms.core.security.SecurityHeadersFilter;
import com.ujcms.cms.core.security.AuthenticatedCacheControlFilter;
import com.ujcms.cms.core.security.PreviewAuthFilter;
import com.ujcms.cms.core.security.PreviewTokenService;
import com.ujcms.cms.core.security.UserDetailsServiceImpl;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.InstallService;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.SecurityProperties;
import com.ujcms.cms.core.web.support.ExceptionResolver;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.captcha.IpLoginAttemptService;
import com.ujcms.common.security.Pbkdf2WithHmacSm3PasswordEncoder;
import com.ujcms.common.security.jwt.JwtProperties;
import com.ujcms.common.web.JspDispatcherFilter;

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
    public SecurityFilterChain backendApiFilterChain(HttpSecurity http,
                                                     InstallService installService) throws Exception {
        // 所有 /api 开头的请求都使用JWT权鉴。链级放行（permitAll），/api/backend/** 的登录
        // 校验由 BackendInterceptor + @PreAuthorize 承担。
        // 安装端点（install-flow §3/§5）：仅在安装状态非 INSTALLED 时开放，完成翻转后在
        // 过滤链层原子关闭（控制器内另有运行时双重校验 + 一次性令牌门禁，D12）。
        http.securityMatcher(API + "/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(API + "/install/**")
                        .access((authentication, context) ->
                                new AuthorizationDecision(!installService.isInstalled()))
                        .anyRequest().permitAll())
                // JWT权鉴需关闭CSRF防护
                .csrf(AbstractHttpConfigurer::disable).oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                // JWT权鉴需关闭Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // SecurityHeadersFilter owns Cache-Control on every response (no-store for /api).
                // Disabling Spring Security's default writer prevents two Cache-Control headers
                // racing each other.
                .headers(headers -> headers.cacheControl(cc -> cc.disable()))
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
                                                   IpLoginAttemptService ipLoginAttemptService,
                                                   SecurityProperties securityProperties,
                                                   PreviewTokenService slotPreviewTokenService) throws Exception {
        // 无后缀的请求
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // 插槽预览（Phase 7 / §7.0）：?_preview=draft&_ptoken=<令牌> 时据预览令牌置 SecurityContext，
                // 令 FrontendInterceptor 既有草稿门禁通过。位于授权前、上下文加载之后。
                .addFilterBefore(new PreviewAuthFilter(slotPreviewTokenService), AuthorizationFilter.class)
                // 已认证响应一律 no-store（docs/spec/http-cache/plan.md）：SecurityHeadersFilter 先于安全链给前台页
                // 写入 private, max-age=300，此时会话未解析；在 PreviewAuthFilter 之后按真实登录态（表单登录、
                // 记住我、预览令牌）改写，登录态页面既不进共享缓存，也不留在浏览器缓存。
                .addFilterAfter(new AuthenticatedCacheControlFilter(), PreviewAuthFilter.class)
                // 预览桥注入（visual-builder §3）：模板轨道草稿预览响应在 </body> 前注入
                // /_ujcms/preview-bridge.js（协议 v2）。位于 PreviewAuthFilter 之后——仅在预览令牌
                // 已置身份时缓冲并注入；非预览请求零成本跳过。
                .addFilterAfter(new PreviewBridgeInjectionFilter(), AuthorizationFilter.class)
                // 传输安全（Phase 4 / G6）：开启后记住我 Cookie 置 Secure（SameSite 由 CookieSameSiteSupplier 统一处理）
                .rememberMe(rememberMe -> rememberMe.tokenRepository(persistentTokenRepository(dataSource))
                        .useSecureCookie(securityProperties.isHttpsEnabled()))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // SecurityHeadersFilter owns Cache-Control + Content-Security-Policy on the
                // public site too (browser-private 5-min TTL for anonymous GETs to /, /channel/**,
                // /article/**; shared-cacheable 1 h for sitemap, RSS; no-store everywhere else, and
                // AuthenticatedCacheControlFilter above turns every authenticated response into
                // no-store). Disable Spring's default Cache-Control
                // so the per-route value survives. Likewise disable the frame-options writer:
                // SecurityHeadersFilter sets X-Frame-Options itself (DENY, but SAMEORIGIN for
                // ?_preview=draft so the console live-preview iframe can same-origin-embed the
                // front page). Spring's writer overwrites DENY/SAMEORIGIN unconditionally, so
                // leaving it on would clobber the per-request value back to DENY.
                .headers(headers -> headers.cacheControl(cc -> cc.disable())
                        .frameOptions(frame -> frame.disable()))
                // 使用Exception中的message信息。AccessDeniedHandlerImpl不使用Exception中的message信息会丢失
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, ex) -> response
                        .sendError(HttpStatus.FORBIDDEN.value(), ExceptionResolver.getMessage(ex, request))))
                .with(new EncryptedPasswordLoginConfigurer<>(props, configService, loginLogService,
                        captchaTokenService, ipLoginAttemptService), Customizer.withDefaults());
        return http.build();
    }

    /**
     * Per-route Cache-Control + Content-Security-Policy header writer plus the per-request CSP
     * nonce that FreeMarker templates consume via {@code ${csp_nonce!''}}. Runs before Spring
     * Security's filter chain so the headers it sets are not clobbered. See
     * {@link SecurityHeadersFilter} for the full route → policy mapping.
     */
    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration(
            SecurityProperties securityProperties, ObjectProvider<CaptchaGuard> captchaGuard) {
        // 后台 CSP 只放行当前选中的第三方验证码厂商的域名（docs/spec/captcha/plan.md）；ObjectProvider 延迟取用，避免启动期循环依赖
        FilterRegistrationBean<SecurityHeadersFilter> reg = new FilterRegistrationBean<>(
                new SecurityHeadersFilter(securityProperties.isHttpsEnabled(), () -> {
                    CaptchaGuard guard = captchaGuard.getIfAvailable();
                    return guard == null ? Map.of() : guard.cspSources();
                }));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        reg.addUrlPatterns("/*");
        return reg;
    }

    /**
     * 传输安全加固（等保三级 传输 / G6 应用侧，Phase 4），仅当 {@code ujcms.security.https-enabled=true} 时注册。
     * 对所有响应 Cookie 置 {@code SameSite=Lax}（涵盖会话与记住我 Cookie）。HSTS 由 {@link SecurityHeadersFilter}
     * 发送，会话 Cookie 的 {@code Secure} 由 {@link #secureSessionCookieInitializer()} 设置，记住我 Cookie 的
     * {@code Secure} 在前台过滤链中按开关设置。真正的传输合规由网关 TLS / 国密 SSL 承担。
     */
    @Bean
    @ConditionalOnProperty(prefix = "ujcms.security", name = "https-enabled", havingValue = "true")
    public CookieSameSiteSupplier httpsCookieSameSiteSupplier() {
        return CookieSameSiteSupplier.ofLax();
    }

    /**
     * 仅当 {@code ujcms.security.https-enabled=true} 时注册：将会话 Cookie 置 {@code Secure}。容器无关
     * （{@link jakarta.servlet.SessionCookieConfig}）。⚠️ 仅适用于确经 HTTPS 提供的站点。
     */
    @Bean
    @ConditionalOnProperty(prefix = "ujcms.security", name = "https-enabled", havingValue = "true")
    public ServletContextInitializer secureSessionCookieInitializer() {
        return servletContext -> servletContext.getSessionCookieConfig().setSecure(true);
    }

    /**
     * 管理终端 IP 白名单过滤器（等保三级 入侵防范 / G5，Phase 3B）。基于存在性（presence-based，D2）：
     * {@code ujcms.security.admin-ip-whitelist} 为空则放行全部；非空时对 {@code /cp/**} 与
     * {@code /api/backend/**} 校验客户端真实 IP。配置仅在 yaml / {@code level3} Profile，保证误配可自救。
     * 高优先级运行（早于 Spring Security 过滤链），命中拒绝即短路。
     */
    @Bean
    public FilterRegistrationBean<AdminIpWhitelistFilter> adminIpWhitelistFilterRegistration(
            SecurityProperties securityProperties, Props props) {
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter(
                securityProperties.getAdminIpWhitelist(), props.getIpProxyDepth());
        FilterRegistrationBean<AdminIpWhitelistFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        reg.addUrlPatterns("/cp/*", "/api/backend/*");
        return reg;
    }

    /**
     * 管理终端访问时段限制过滤器（等保三级 访问控制 可选项，Phase 3D）。基于存在性：
     * {@code ujcms.security.admin-access-window} 为空则放行全部；非空时按服务器本地时间对 {@code /cp/**} 与
     * {@code /api/backend/**} 持续校验。仅 yaml 配置，默认关闭（非高风险项），便于误配自救。
     */
    @Bean
    public FilterRegistrationBean<AdminAccessWindowFilter> adminAccessWindowFilterRegistration(
            SecurityProperties securityProperties) {
        AdminAccessWindowFilter filter = new AdminAccessWindowFilter(securityProperties.getAdminAccessWindow());
        FilterRegistrationBean<AdminAccessWindowFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 60);
        reg.addUrlPatterns("/cp/*", "/api/backend/*");
        return reg;
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
     * HTML 过滤器。防止跨站攻击。返回 {@link PolicyTier#RICH}（后台可信编辑者）策略，供注入
     * {@link PolicyFactory} 的既有代码使用。所有分级策略集中维护在 {@link HtmlSanitizeService}。
     */
    @Bean
    public PolicyFactory policyFactory(HtmlSanitizeService htmlSanitizeService) {
        return htmlSanitizeService.getFactory(PolicyTier.RICH);
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
