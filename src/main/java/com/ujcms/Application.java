package com.ujcms;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.ofwise.util.image.ImageHandler;
import com.ofwise.util.image.ThumbnailatorHandler;
import com.ofwise.util.security.CredentialsDigest;
import com.ofwise.util.security.Pbkdf2WithHmacSha512Digest;
import com.ofwise.util.security.csrf.CsrfInterceptor;
import com.ofwise.util.security.jwt.JwtProperties;
import com.ofwise.util.web.DirectoryRedirectInterceptor;
import com.ofwise.util.web.JspDispatcherFilter;
import com.ofwise.util.web.PathResolver;
import com.ofwise.util.web.TimerInterceptor;
import com.ujcms.core.security.DbRealm;
import com.ujcms.core.service.GlobalService;
import com.ujcms.core.service.SiteQueryService;
import com.ujcms.core.service.UserService;
import com.ujcms.core.support.Props;
import com.ujcms.core.web.support.BackendInterceptor;
import com.ujcms.core.web.support.ExceptionResolver;
import com.ujcms.core.web.support.FrontendInterceptor;
import com.ujcms.core.web.support.JwtInterceptor;
import com.ujcms.core.web.support.SiteResolver;
import com.ujcms.core.web.support.UrlRedirectInterceptor;
import no.api.freemarker.java8.Java8ObjectWrapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.owasp.html.CssSchema;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.examples.EbayPolicyExample;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mobile.device.DeviceResolver;
import org.springframework.mobile.device.LiteDeviceResolver;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.Filter;
import javax.servlet.ServletContext;

import static com.ujcms.core.support.UrlConstants.API;
import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 根据 https://start.spring.io/ 生成的代码范例
 *
 * @author PONY
 */
@SpringBootApplication
public class Application extends SpringBootServletInitializer implements WebApplicationInitializer {
    /**
     * UJCMS 配置
     */
    @Bean
    public Props props() {
        return new Props();
    }

    /**
     * 禁止 JSP 访问
     */
    @Bean
    public FilterRegistrationBean<Filter> jspDispatcherFilterRegistrationBean() {
        Props props = props();
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

    /**
     * 设备识别器，用于识别是否手机访问
     */
    @Bean
    public LiteDeviceResolver liteDeviceResolver() {
        return new LiteDeviceResolver();
    }

    /**
     * 密码加密组件
     */
    @Bean
    public CredentialsDigest credentialsDigest() {
        return new Pbkdf2WithHmacSha512Digest();
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
     * 真实路径获取组件
     */
    @Bean
    public PathResolver pathResolver(ServletContext servletContext) {
        return new PathResolver(servletContext);
    }

    /**
     * 图片处理组件
     */
    @Bean
    public ImageHandler imageHandler() {
        return new ThumbnailatorHandler();
    }

    @Bean
    public Realm realm(UserService userService) {
        return new DbRealm(credentialsDigest(), userService);
    }

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
        chainDefinition.addPathDefinition("/logout", "logout");
        chainDefinition.addPathDefinition("/my/**", "user");
        chainDefinition.addPathDefinition("/**", "anon");
        return chainDefinition;
    }

    @Bean
    public SiteResolver siteResolver(SiteQueryService siteService, GlobalService globalService) {
        return new SiteResolver(siteService, globalService);
    }

    @Configuration
    public static class WebConfigurer implements WebMvcConfigurer, InitializingBean {
        private SiteQueryService siteService;
        private GlobalService globalService;
        private Props props;
        private DeviceResolver deviceResolver;
        private ResourceLoader resourceLoader;
        private ServletContext servletContext;
        private freemarker.template.Configuration configuration;

        public WebConfigurer(SiteQueryService siteService, GlobalService globalService, Props props,
                             DeviceResolver deviceResolver, ResourceLoader resourceLoader,
                             ServletContext servletContext, freemarker.template.Configuration configuration) {
            this.siteService = siteService;
            this.globalService = globalService;
            this.props = props;
            this.deviceResolver = deviceResolver;
            this.resourceLoader = resourceLoader;
            this.servletContext = servletContext;
            this.configuration = configuration;
        }

        /**
         * 异常处理
         */
        @Bean
        public ExceptionResolver exceptionResolver() {
            return new ExceptionResolver();
        }

        /**
         * 计时器拦截器
         */
        @Bean
        public TimerInterceptor timerInterceptor() {
            return new TimerInterceptor();
        }

        /**
         * JWT 配置
         */
        @Bean
        public JwtProperties jwtProperties() {
            return new JwtProperties();
        }

        /**
         * JWT 拦截器
         */
        @Bean
        public JwtInterceptor jwtInterceptor() {
            return new JwtInterceptor(jwtProperties());
        }

        /**
         * CSRF 拦截器
         */
        @Bean
        public CsrfInterceptor csrfInterceptor() {
            return new CsrfInterceptor();
        }

        /**
         * 后台拦截器
         */
        @Bean
        public BackendInterceptor backendInterceptor() {
            return new BackendInterceptor(siteService, props);
        }

        /**
         * 前台拦截器
         */
        @Bean
        public FrontendInterceptor frontendInterceptor() {
            return new FrontendInterceptor(deviceResolver, props);
        }

        /**
         * URL重写拦截器
         */
        @Bean
        public UrlRedirectInterceptor urlRedirectInterceptor() {
            return new UrlRedirectInterceptor(globalService);
        }

        /**
         * 目录重定向拦截器。支持访问
         */
        @Bean
        public DirectoryRedirectInterceptor directoryRedirectInterceptor() {
            return new DirectoryRedirectInterceptor(resourceLoader, props.isFileToDir(), props.isDirToFile());
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            // 错误页面和带点的文件请求 jquery.js bootstrap.min.css 都不经过拦截器
            registry.addInterceptor(timerInterceptor()).excludePathPatterns("/error/**", "/**/*.*");
            // RESTful 有自己的机制防止 CSRF 攻击
            registry.addInterceptor(csrfInterceptor()).excludePathPatterns(API + "/**", "/error/**", "/**/*.*");
            // RESTful 接口地址以 /api 开头
            registry.addInterceptor(jwtInterceptor()).addPathPatterns(API + "/**");
            // 后台拦截器
            registry.addInterceptor(backendInterceptor()).addPathPatterns(BACKEND_API + "/**");
            // 目录重定向拦截器
            if (props.isFileToDir() || props.isDirToFile()) {
                registry.addInterceptor(directoryRedirectInterceptor())
                        .excludePathPatterns(API + "/**", "/error/**", "/**/*.*");
            }
            // 前台拦截器
            registry.addInterceptor(urlRedirectInterceptor()).excludePathPatterns(API + "/**", "/error/**", "/**/*.*");
            registry.addInterceptor(frontendInterceptor()).excludePathPatterns(API + "/**", "/error/**", "/**/*.*");
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            // 在开发环境下，上传的图片无法立即访问，需要等待1至5秒不等。
            // 可能是因为上传后，开发工具需要同步到实际tomcat的运行路径下。
            // 需要将上传文件定位到真实路径。可以通过设置 spring.web.resources.static-locations 实现。
            // 也可考虑判断 profile 进行处理。
            // List<String> profiles = Arrays.asList(applicationContext.getEnvironment().getActiveProfiles());
            String uploadsLocation = props.getUploadsLocation();
            if (StringUtils.isNotBlank(uploadsLocation)) {
                registry.addResourceHandler(uploadsLocation + "/**").
                        addResourceLocations("file:" + servletContext.getRealPath(uploadsLocation + "/"));
            }
        }

        @Override
        public void afterPropertiesSet() {
            configuration.setObjectWrapper(new Java8ObjectWrapper(freemarker.template.Configuration.VERSION_2_3_31));
        }
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return customizerBuilder(builder);
    }

    public static void main(String[] args) {
        customizerBuilder(new SpringApplicationBuilder()).run(args);
    }

    private static SpringApplicationBuilder customizerBuilder(SpringApplicationBuilder builder) {
        return builder.sources(Application.class);
    }
}
