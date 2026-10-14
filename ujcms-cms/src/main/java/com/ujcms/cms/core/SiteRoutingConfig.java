package com.ujcms.cms.core;

import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.web.support.FrontendRouteWords;
import com.ujcms.cms.core.web.support.SiteArgumentResolver;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.core.web.support.SiteRoutingHandlerMapping;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

/**
 * 站点子目录路由配置：子目录孪生路由的统一生成、当前站点参数解析、路由保留字。
 * 参见 {@link com.ujcms.cms.core.web.support.FrontendController}
 *
 * @author PONY
 */
@Configuration
public class SiteRoutingConfig implements WebMvcConfigurer {
    private final ObjectProvider<SiteResolver> siteResolver;

    public SiteRoutingConfig(ObjectProvider<SiteResolver> siteResolver) {
        this.siteResolver = siteResolver;
    }

    @Bean
    public FrontendRouteWords frontendRouteWords(ConfigService configService) {
        return new FrontendRouteWords(configService);
    }

    @Bean
    public WebMvcRegistrations siteRoutingRegistrations(FrontendRouteWords routeWords) {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new SiteRoutingHandlerMapping(routeWords);
            }
        };
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new SiteArgumentResolver(siteResolver.getObject()));
    }
}
