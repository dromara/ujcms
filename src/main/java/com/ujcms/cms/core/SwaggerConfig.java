package com.ujcms.cms.core;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc Swagger 配置
 * <p>
 * <a href="https://github.com/springdoc/springdoc-openapi-demos/blob/master/springdoc-openapi-spring-boot-2-webmvc/src/main/java/org/springdoc/demo/app2/Application.java">springdoc-openapi Application.java</a>
 *
 * @author PONY
 */
@Configuration
public class SwaggerConfig {
    /**
     * 文档定义
     */
    @Bean
    public OpenAPI openApi(@Value("${ujcms.version}") String version) {
        return new OpenAPI().info(new Info().title("UJCMS API").description("UJCMS 接口文档").version(version));
    }

    /**
     * 前台 API 分组
     *
     * @param version 当前软件版本号
     */
    @Bean
    public GroupedOpenApi frontendGroup(@Value("${ujcms.version}") String version) {
        return GroupedOpenApi.builder().group("frontend").displayName("前台API")
                .addOpenApiCustomiser(openApi -> openApi.info(new Info().title("UJCMS 前台 API").version(version)))
                .packagesToScan("com.ujcms.cms.core.web.api", "com.ujcms.cms.ext.web.api")
                .pathsToMatch("/api/**")
                .build();
    }


    // private static final String BEARER_AUTH = "bearerAuth";
    // /**
    //  * 后台 API 分组
    //  *
    //  * @param version 当前软件版本号
    //  */
    // @Bean
    // public GroupedOpenApi backendGroup(@Value("${ujcms.version}") String version) {
    //     return GroupedOpenApi.builder().group("backend").displayName("后台API")
    //             .addOpenApiCustomiser(openApi -> openApi.info(new Info().title("UJCMS 后台 API").version(version)))
    //             .packagesToScan("com.ujcms.cms.core.web.backendapi")
    //             .pathsToMatch("/api/backend/**")
    //             .addOpenApiCustomiser(openApi -> openApi.components(new Components()
    //                     .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
    //                             .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))))
    //             .addOperationCustomizer((operation, handlerMethod) -> {
    //                 operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    //                 return operation;
    //             })
    //             .build();
    // }
}
