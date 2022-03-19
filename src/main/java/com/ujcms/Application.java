package com.ujcms;

import com.ofwise.util.db.DataScriptInitializer;
import com.ofwise.util.image.ImageHandler;
import com.ofwise.util.image.ThumbnailatorHandler;
import com.ofwise.util.security.csrf.CsrfInterceptor;
import com.ofwise.util.security.jwt.JwtProperties;
import com.ofwise.util.web.DirectoryRedirectInterceptor;
import com.ofwise.util.web.PathResolver;
import com.ofwise.util.web.TimerInterceptor;
import com.ujcms.core.service.GlobalService;
import com.ujcms.core.service.SiteQueryService;
import com.ujcms.core.service.UserService;
import com.ujcms.core.support.Props;
import com.ujcms.core.support.Utils;
import com.ujcms.core.web.support.BackendInterceptor;
import com.ujcms.core.web.support.ExceptionResolver;
import com.ujcms.core.web.support.FrontendInterceptor;
import com.ujcms.core.web.support.JwtInterceptor;
import com.ujcms.core.web.support.UrlRedirectInterceptor;
import no.api.freemarker.java8.Java8ObjectWrapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mobile.device.DeviceResolver;
import org.springframework.mobile.device.LiteDeviceResolver;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.ServletContext;
import javax.sql.DataSource;

import static com.ujcms.core.support.UrlConstants.API;
import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 根据 https://start.spring.io/ 生成的代码范例
 *
 * @author PONY
 */
@SpringBootApplication(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class Application extends SpringBootServletInitializer implements WebApplicationInitializer {
    /**
     * UJCMS 配置
     */
    @Bean
    public Props props() {
        return new Props();
    }

    /**
     * 设备识别器，用于识别是否手机访问
     */
    @Bean
    public LiteDeviceResolver liteDeviceResolver() {
        return new LiteDeviceResolver();
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

    /**
     * 数据初始化
     */
    @Bean
    @DependsOn("liquibase")
    @ConditionalOnProperty(prefix = "ujcms", name = "data-sql-enabled", matchIfMissing = true)
    public DataScriptInitializer databaseInitializer(Props props,
                                                     DataSource dataSource, ResourceLoader resourceLoader) {
        return new DataScriptInitializer(dataSource, resourceLoader, "ujcms_global", props.getDataSqlPlatform());
    }

    @Configuration
    public static class WebConfigurer implements WebMvcConfigurer, InitializingBean {
        private UserService userService;
        private SiteQueryService siteQueryService;
        private GlobalService globalService;
        private Props props;
        private DeviceResolver deviceResolver;
        private ResourceLoader resourceLoader;
        private ServletContext servletContext;
        private freemarker.template.Configuration configuration;

        public WebConfigurer(UserService userService, SiteQueryService siteQueryService, GlobalService globalService,
                             Props props, DeviceResolver deviceResolver, ResourceLoader resourceLoader,
                             ServletContext servletContext, freemarker.template.Configuration configuration) {
            this.userService = userService;
            this.siteQueryService = siteQueryService;
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
            return new JwtInterceptor(jwtProperties(), userService);
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
            return new BackendInterceptor(siteQueryService, props);
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
        public void addCorsMappings(CorsRegistry registry) {
            // 允许 api 跨域
//            registry.addMapping(API + "/**").allowedOrigins("*");
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
        Utils.boot();
        return builder.sources(Application.class);
    }
}
