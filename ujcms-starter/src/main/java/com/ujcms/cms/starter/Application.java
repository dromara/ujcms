package com.ujcms.cms.starter;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerProperties;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.jspecify.annotations.NonNull;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;
import org.springframework.web.servlet.view.freemarker.FreeMarkerViewResolver;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.nimbusds.jose.KeyLengthException;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.InstallService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.SandboxSiteRegistry;
import com.ujcms.cms.core.composition.ThemeSettingsService;
import com.ujcms.cms.core.support.HardenedFreeMarkerView;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.SecurityProperties;
import com.ujcms.cms.core.web.support.BackendInterceptor;
import com.ujcms.cms.core.web.support.ExceptionResolver;
import com.ujcms.cms.core.web.support.FrontendApiInterceptor;
import com.ujcms.cms.core.web.support.FrontendInterceptor;
import com.ujcms.cms.core.web.support.InstallGuardInterceptor;
import com.ujcms.cms.core.web.support.UrlRedirectInterceptor;
import com.ujcms.common.freemarker.OsTemplateLoader;
import com.ujcms.common.image.ImageHandler;
import com.ujcms.common.image.ThumbnailatorHandler;
import com.ujcms.common.misc.LongArrayToStringSerializer;
import com.ujcms.common.security.jwt.HmacSm3JwsSigner;
import com.ujcms.common.security.jwt.HmacSm3JwsVerifier;
import com.ujcms.common.security.jwt.JwtProperties;
import com.ujcms.common.web.DirectoryRedirectInterceptor;
import com.ujcms.common.web.PathResolver;
import com.ujcms.common.web.TimerInterceptor;

import jakarta.servlet.ServletContext;
import ua_parser.Parser;

/**
 * 根据 <a href="https://start.spring.io/">start.spring.io</a> 生成的代码范例
 * <p>
 * SpringBoot的入口和主要配置文件
 *
 * @author PONY
 */
@SpringBootApplication(
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        scanBasePackages = "com.ujcms.cms"
)
public class Application extends SpringBootServletInitializer {
    /**
     * UJCMS 配置
     */
    @Bean
    public Props props() {
        return new Props();
    }

    /**
     * 等保三级 / 密评三级 合规基础设施层开关（{@code ujcms.security.*}，默认全部关闭）
     */
    @Bean
    public SecurityProperties securityProperties() {
        return new SecurityProperties();
    }

    /**
     * 用户代理解析器，用于识别设备类型
     */
    @Bean
    public Parser uaParser() {
        return new Parser();
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
    @EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
    public static class WebConfigurer implements WebMvcConfigurer {
        private final UserService userService;
        private final SiteService siteService;
        private final OrgService orgService;
        private final ConfigService configService;
        private final InstallService installService;
        private final ThemeSettingsService themeSettingsService;
        private final ManifestService manifestService;
        private final SandboxSiteRegistry sandboxSiteRegistry;
        private final Props props;
        private final Parser uaParser;
        private final ResourceLoader resourceLoader;
        private final ServletContext servletContext;

        public WebConfigurer(UserService userService, SiteService siteService, OrgService orgService,
                             ConfigService configService, InstallService installService,
                             ThemeSettingsService themeSettingsService,
                             ManifestService manifestService,
                             ObjectProvider<SandboxSiteRegistry> sandboxSiteRegistry,
                             Props props, Parser uaParser,
                             ResourceLoader resourceLoader, ServletContext servletContext) {
            this.userService = userService;
            this.siteService = siteService;
            this.orgService = orgService;
            this.configService = configService;
            this.installService = installService;
            this.themeSettingsService = themeSettingsService;
            this.manifestService = manifestService;
            // 沙箱成员 seam（sandbox-preview-site S3）：商业 T2 装配注册实现；社区/基础版回落 NONE
            this.sandboxSiteRegistry = sandboxSiteRegistry.getIfAvailable(() -> SandboxSiteRegistry.NONE);
            this.props = props;
            this.uaParser = uaParser;
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
            return new FrontendInterceptor(userService, siteService, configService, themeSettingsService,
                    manifestService, sandboxSiteRegistry, uaParser);
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
         * 前台未安装守卫（install-flow §3）：未安装时前台短路到自包含"系统尚未安装"页，
         * 引导去控制台 {@code /cp/} 完成安装
         */
        @Bean
        public InstallGuardInterceptor installGuardInterceptor() {
            return new InstallGuardInterceptor(installService);
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
            // 前台未安装守卫：挡在 UrlRedirectInterceptor（前台首个 getUnique() 调用点）之前；
            // 控制台 /cp/ 与 API 不经过（安装向导即在其中）
            registry.addInterceptor(installGuardInterceptor())
                    .excludePathPatterns(API + "/**", FRONTEND_API + "/**", "/cp", "/cp/**",
                            ERROR_PATH_PATTERN, EXTENSION_PATH_PATTERN);
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
            // 上传目录以 file: 路径直接提供静态资源服务，绕过 ServletContext 资源查找及 Tomcat 的资源缓存
            // （StandardRoot，默认 cacheTtl 5 秒）。“刚上传的文件要等 1 至 5 秒才能访问”的根因是写入前对目标路径
            // 调用 getRealPath 让 Tomcat 缓存了“不存在”，已在 LocalFileHandler#resolveNewFile 消除；
            // 此处保留为静态资源服务的优化项，并可把上传目录放到程序目录之外（见 ujcms.uploads-location）。
            String uploadsLocation = props.getUploadsLocation();
            if (StringUtils.isNotBlank(uploadsLocation)) {
                registry.addResourceHandler(uploadsLocation + "/**")
                        .addResourceLocations("file:" + servletContext.getRealPath(uploadsLocation + "/"));
            }
            // 构建器桥资产（/_ujcms/**）：URL 固定、默认静态映射无 Cache-Control → 浏览器启发式
            // 缓存让旧桥脚本在升级/热替换后继续服役（画布装饰与后端版本静默漂移）。no-cache =
            // 每次条件请求重新验证（未变则 304，代价极低）；注入 URL 另带内容指纹参数双保险。
            registry.addResourceHandler("/_ujcms/**")
                    .addResourceLocations("classpath:/static/_ujcms/")
                    .setCacheControl(CacheControl.noCache());
        }

        @Override
        public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
            // pdf.js 使用 mjs，需要设置媒体类型为 application/javascript，否则无法访问
            // spring boot 2 需设置，spring boot 3 默认已支持
            configurer.mediaType("mjs", MediaType.valueOf("application/javascript"));
        }

        /**
         * 覆盖 Spring Boot 自动配置的 FreeMarker 视图解析器，改用 {@link HardenedFreeMarkerView}，
         * 以移除模板中实时的 {@code Application}/{@code Session}/{@code Request} 等 Servlet 作用域变量，
         * 防止后台模板编辑功能（SSTI）经其取得 ServletContext/Spring 容器引用提权为 RCE。
         * Bean 名称须为 {@code freeMarkerViewResolver}，以替换自动配置的同名 Bean。
         */
        @Bean
        public FreeMarkerViewResolver freeMarkerViewResolver(FreeMarkerProperties properties) {
            FreeMarkerViewResolver resolver = new FreeMarkerViewResolver();
            properties.applyToMvcViewResolver(resolver);
            resolver.setViewClass(HardenedFreeMarkerView.class);
            return resolver;
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
        return builder.sources(Application.class);
    }

    @SuppressWarnings("java:S1075")
    private static final String ERROR_PATH_PATTERN = "/error/**";
    private static final String EXTENSION_PATH_PATTERN = "/**/*.*";
}
