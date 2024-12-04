package com.ujcms.cms;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.nimbusds.jose.KeyLengthException;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.Utils;
import com.ujcms.cms.core.web.support.*;
import com.ujcms.commons.freemarker.OsTemplateLoader;
import com.ujcms.commons.image.ImageHandler;
import com.ujcms.commons.image.ThumbnailatorHandler;
import com.ujcms.commons.misc.LongArrayToStringSerializer;
import com.ujcms.commons.security.jwt.HmacSm3JwsSigner;
import com.ujcms.commons.security.jwt.HmacSm3JwsVerifier;
import com.ujcms.commons.security.jwt.JwtProperties;
import com.ujcms.commons.web.DirectoryRedirectInterceptor;
import com.ujcms.commons.web.PathResolver;
import com.ujcms.commons.web.TimerInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerProperties;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.mobile.device.DeviceResolver;
import org.springframework.mobile.device.LiteDeviceResolver;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import javax.servlet.ServletContext;
import java.util.Properties;

import static com.ujcms.cms.core.support.UrlConstants.*;

/**
 * 根据 https://start.spring.io/ 生成的代码范例。
 * <p>
 * SpringBoot的入口和主要配置文件
 *
 * @author PONY
 */
@SpringBootApplication(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class Application extends SpringBootServletInitializer
        implements WebApplicationInitializer, ApplicationContextAware {
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
    public PathResolver pathResolver(ObjectProvider<ServletContext> servletContextProvider) {
        return new PathResolver(servletContextProvider);
    }

    /**
     * 图片处理组件
     */
    @Bean
    public ImageHandler imageHandler() {
        return new ThumbnailatorHandler();
    }

    /**
     * JSON序列号：long转string
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return jacksonObjectMapperBuilder -> jacksonObjectMapperBuilder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .serializerByType(long[].class, LongArrayToStringSerializer.INSTANCE);
    }

    @Configuration
    public static class WebConfigurer implements WebMvcConfigurer {
        private final UserService userService;
        private final SiteService siteService;
        private final OrgService orgService;
        private final ConfigService configService;
        private final Props props;
        private final DeviceResolver deviceResolver;
        private final ResourceLoader resourceLoader;
        private final ServletContext servletContext;

        public WebConfigurer(UserService userService, SiteService siteService, OrgService orgService,
                             ConfigService configService, Props props, DeviceResolver deviceResolver,
                             ResourceLoader resourceLoader, ServletContext servletContext) {
            this.userService = userService;
            this.siteService = siteService;
            this.orgService = orgService;
            this.configService = configService;
            this.props = props;
            this.deviceResolver = deviceResolver;
            this.resourceLoader = resourceLoader;
            this.servletContext = servletContext;
        }

        /**
         * 异常处理
         */
        @Bean
        public ExceptionResolver exceptionResolver() {
            return new ExceptionResolver(configService);
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
         * JWT 签名。使用国密 HmacSM3。
         */
        @Bean
        public HmacSm3JwsSigner hmacSm3JwsSigner() throws KeyLengthException {
            return new HmacSm3JwsSigner(jwtProperties().getSecret());
        }

        /**
         * JWT 验证。使用国密 HmacSM3。
         */
        @Bean
        public HmacSm3JwsVerifier hmacSm3JwsVerifier() throws KeyLengthException {
            return new HmacSm3JwsVerifier(jwtProperties().getSecret());
        }

        /**
         * 后台拦截器
         */
        @Bean
        public BackendInterceptor backendInterceptor() {
            return new BackendInterceptor(userService, siteService, orgService, props);
        }

        /**
         * 前台拦截器
         */
        @Bean
        public FrontendInterceptor frontendInterceptor() {
            return new FrontendInterceptor(userService, siteService, configService, deviceResolver);
        }

        /**
         * 前台拦截器
         */
        @Bean
        public FrontendApiInterceptor frontendApiInterceptor() {
            return new FrontendApiInterceptor(userService);
        }

        /**
         * URL重写拦截器
         */
        @Bean
        public UrlRedirectInterceptor urlRedirectInterceptor() {
            return new UrlRedirectInterceptor(configService);
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
            registry.addInterceptor(timerInterceptor()).excludePathPatterns(ERROR_PATH_PATTERN, EXTENSION_PATH_PATTERN);
            // 后台拦截器
            registry.addInterceptor(backendInterceptor()).addPathPatterns(BACKEND_API + "/**");
            // 目录重定向拦截器
            if (props.isFileToDir() || props.isDirToFile()) {
                registry.addInterceptor(directoryRedirectInterceptor())
                        .excludePathPatterns(API + "/**", FRONTEND_API + "/**", ERROR_PATH_PATTERN, EXTENSION_PATH_PATTERN);
            }
            // URL重写拦截器
            registry.addInterceptor(urlRedirectInterceptor())
                    .excludePathPatterns(API + "/**", FRONTEND_API + "/**", ERROR_PATH_PATTERN, EXTENSION_PATH_PATTERN);
            // 前台拦截器
            registry.addInterceptor(frontendInterceptor())
                    .excludePathPatterns(API + "/**", FRONTEND_API + "/**", ERROR_PATH_PATTERN, EXTENSION_PATH_PATTERN);
            // 前台API拦截器
            registry.addInterceptor(frontendApiInterceptor())
                    .addPathPatterns(API + "/**", FRONTEND_API + "/**")
                    .excludePathPatterns(BACKEND_API + "/**");
        }

        @Override
        public void addCorsMappings(@NonNull CorsRegistry registry) {
            // 允许 api 跨域
            //registry.addMapping(API + "/**").allowedOrigins("*").allowedMethods("GET", "POST").allowCredentials(true)
        }

        @Override
        public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
            // 在开发环境下，上传的图片无法立即访问，需要等待1至5秒不等。
            // 可能是因为上传后，开发工具需要同步到实际tomcat的运行路径下。
            // 需要将上传文件定位到真实路径。可以通过设置 spring.web.resources.static-locations 实现。
            // 也可考虑判断 profile 进行处理。
            // List<String> profiles = Arrays.asList(applicationContext.getEnvironment().getActiveProfiles())
            String uploadsLocation = props.getUploadsLocation();
            if (StringUtils.isNotBlank(uploadsLocation)) {
                registry.addResourceHandler(uploadsLocation + "/**")
                        .addResourceLocations("file:" + servletContext.getRealPath(uploadsLocation + "/"));
            }
        }

        @Bean
        @ConditionalOnProperty(prefix = "ujcms", name = "template-loader-url")
        public FreeMarkerConfigurer freeMarkerConfigurer(
                FreeMarkerProperties properties,
                @Value("${ujcms.template-loader-url}") String templateLoaderUrl) {
            FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();
            configurer.setPreTemplateLoaders(new OsTemplateLoader(templateLoaderUrl));
            // 使用 URL 加载模板，需关闭此项
            configurer.setPreferFileSystemAccess(false);
            configurer.setDefaultEncoding(properties.getCharsetName());
            Properties settings = new Properties();
            settings.put("recognize_standard_file_extensions", "true");
            settings.putAll(properties.getSettings());
            configurer.setFreemarkerSettings(settings);
            return configurer;
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

    @Nullable
    private static ApplicationContext applicationContext;

    @Override
    @SuppressWarnings("java:S2696")
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Application.applicationContext = applicationContext;
    }

    @Nullable
    public static ApplicationContext getApplicationContext() {
        return Application.applicationContext;
    }

    @SuppressWarnings("java:S1075")
    private static final String ERROR_PATH_PATTERN = "/error/**";
    private static final String EXTENSION_PATH_PATTERN = "/**/*.*";
}
