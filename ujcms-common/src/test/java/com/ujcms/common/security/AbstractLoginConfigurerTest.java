package com.ujcms.common.security;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.SecurityConfigurer;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 认证结果必须存入会话：不勾选"记住我"的表单登录，下一请求不能回退为匿名。
 * Spring Security 6 下 AbstractAuthenticationProcessingFilter 默认为
 * RequestAttributeSecurityContextRepository（仅请求内有效），故 configure() 必须
 * 显式装配会话后端的 SecurityContextRepository。
 */
class AbstractLoginConfigurerTest {

    static class TestLoginConfigurer
            extends AbstractLoginConfigurer<FakeBuilder, TestLoginConfigurer, UsernamePasswordAuthenticationFilter> {
        TestLoginConfigurer() {
            super(new UsernamePasswordAuthenticationFilter(), "/login");
        }

        UsernamePasswordAuthenticationFilter filter() {
            return getAuthenticationFilter();
        }
    }

    static class FakeBuilder implements HttpSecurityBuilder<FakeBuilder> {
        private final Map<Class<?>, Object> sharedObjects = new HashMap<>();
        private final List<Filter> filters = new ArrayList<>();

        @Override
        public <C extends SecurityConfigurer<DefaultSecurityFilterChain, FakeBuilder>> C getConfigurer(Class<C> clazz) {
            return null;
        }

        @Override
        public <C extends SecurityConfigurer<DefaultSecurityFilterChain, FakeBuilder>> C removeConfigurer(Class<C> clazz) {
            return null;
        }

        @Override
        public <C> void setSharedObject(Class<C> sharedType, C object) {
            sharedObjects.put(sharedType, object);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <C> C getSharedObject(Class<C> sharedType) {
            return (C) sharedObjects.get(sharedType);
        }

        @Override
        public FakeBuilder authenticationProvider(AuthenticationProvider authenticationProvider) {
            return this;
        }

        @Override
        public FakeBuilder userDetailsService(UserDetailsService userDetailsService) {
            return this;
        }

        @Override
        public FakeBuilder addFilterAfter(Filter filter, Class<? extends Filter> afterFilter) {
            filters.add(filter);
            return this;
        }

        @Override
        public FakeBuilder addFilterBefore(Filter filter, Class<? extends Filter> beforeFilter) {
            filters.add(filter);
            return this;
        }

        @Override
        public FakeBuilder addFilter(Filter filter) {
            filters.add(filter);
            return this;
        }

        @Override
        public DefaultSecurityFilterChain build() {
            throw new UnsupportedOperationException();
        }
    }

    private static Object repositoryOf(UsernamePasswordAuthenticationFilter filter) {
        return ReflectionTestUtils.getField(filter, "securityContextRepository");
    }

    private static void apply(TestLoginConfigurer configurer, FakeBuilder http) throws Exception {
        configurer.init(http);
        configurer.configure(http);
    }

    @Test
    void defaultRepositoryIsSessionBacked() throws Exception {
        TestLoginConfigurer configurer = new TestLoginConfigurer();
        apply(configurer, new FakeBuilder());
        assertInstanceOf(DelegatingSecurityContextRepository.class, repositoryOf(configurer.filter()));
    }

    @Test
    void sharedRepositoryIsUsedWhenPresent() throws Exception {
        SecurityContextRepository shared = new HttpSessionSecurityContextRepository();
        FakeBuilder http = new FakeBuilder();
        http.setSharedObject(SecurityContextRepository.class, shared);
        TestLoginConfigurer configurer = new TestLoginConfigurer();
        apply(configurer, http);
        assertSame(shared, repositoryOf(configurer.filter()));
    }

    @Test
    void explicitRepositoryOverridesShared() throws Exception {
        SecurityContextRepository shared = new HttpSessionSecurityContextRepository();
        SecurityContextRepository explicit = new HttpSessionSecurityContextRepository();
        FakeBuilder http = new FakeBuilder();
        http.setSharedObject(SecurityContextRepository.class, shared);
        TestLoginConfigurer configurer = new TestLoginConfigurer();
        configurer.securityContextRepository(explicit);
        apply(configurer, http);
        assertSame(explicit, repositoryOf(configurer.filter()));
    }
}
