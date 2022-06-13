package com.ujcms.cms.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.ujcms.util.security.CredentialsDigest;
import com.ujcms.util.security.Pbkdf2WithHmacSm3Digest;
import com.ujcms.util.web.JspDispatcherFilter;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.security.DbRealm;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.owasp.html.CssSchema;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.examples.EbayPolicyExample;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.servlet.Filter;

/**
 * Shiro及安全配置
 *
 * @author PONY
 */
@Configuration
public class ShiroConfig {
    /**
     * 这个对象在 shiro boot 里面有定义，但为了加上"proxyTargetClass=true"，必须重新定义。
     * 为了方便 service 层直接用类，没有使用接口，所以必须加上 "proxyTargetClass=true"。
     */
    @Bean
    @DependsOn("lifecycleBeanPostProcessor")
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator advisorAutoProxyCreator = new DefaultAdvisorAutoProxyCreator();
        advisorAutoProxyCreator.setProxyTargetClass(true);
        return advisorAutoProxyCreator;
    }

    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        DefaultShiroFilterChainDefinition chainDefinition = new DefaultShiroFilterChainDefinition();
        // jwt 请求不需要 session 信息
        chainDefinition.addPathDefinition(UrlConstants.API + "/**", "noSessionCreation");
        chainDefinition.addPathDefinition("/logout", "logout");
        chainDefinition.addPathDefinition("/my/**", "user");
        chainDefinition.addPathDefinition("/**", "anon");
        return chainDefinition;
    }

    /**
     * 密码加密组件
     */
    @Bean
    public CredentialsDigest credentialsDigest(Props props) {
        return new Pbkdf2WithHmacSm3Digest(props.getPasswordPepper());
    }

    @Bean
    public Realm realm(UserService userService, Props props) {
        return new DbRealm(credentialsDigest(props), userService);
    }

    /**
     * HTML过滤器。防止跨站攻击。
     */
    @Bean
    public PolicyFactory policyFactory() {
        return EbayPolicyExample.POLICY_DEFINITION.and(new HtmlPolicyBuilder()
                // 允许视频元素
                .allowAttributes("controls", "preload", "width", "height", "src").onElements("video")
                .allowAttributes("src", "type").onElements("source")
                .allowElements("video", "source")
                // 允许浮动样式
                .allowStyling(CssSchema.withProperties(ImmutableSet.of("float")))
                // tinymce 编辑器的图片居中要使用到display:block，而display:none可以隐形内容，所以只允许display:block。
                .allowStyling(CssSchema.withProperties(ImmutableMap.of("display",
                        new CssSchema.Property(0, ImmutableSet.of("block"), ImmutableMap.of()))))
                .toFactory());
    }

    /**
     * 禁止 JSP 访问。如果 spring.h2.console 开启，则禁用该过滤器，因为 H2 控制台需要访问jsp。
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.h2.console", name = "enabled", havingValue = "false", matchIfMissing = true)
    public FilterRegistrationBean<Filter> jspDispatcherFilterRegistrationBean(Props props) {
        FilterRegistrationBean<Filter> filterRegistration = new FilterRegistrationBean<>();
        filterRegistration.setFilter(new JspDispatcherFilter());
        filterRegistration.setEnabled(true);
        filterRegistration.addUrlPatterns("*.jsp");
        filterRegistration.addUrlPatterns("*.jspx");
        if (props.isJspAllowed()) {
            filterRegistration.addInitParameter("allowed", "true");
        }
        return filterRegistration;
    }
}
