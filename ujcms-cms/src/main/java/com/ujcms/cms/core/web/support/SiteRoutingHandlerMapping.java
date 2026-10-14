package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.support.UrlConstants;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 站点子目录路由。为 {@link FrontendController} 的每个路由模式自动生成 {@code /{subDir}} 孪生模式：
 * {@code /article/{key}} → {@code /article/{key}} + {@code /{subDir:[\w-]+}/article/{key}}。
 *
 * <p>匹配语义与手写孪生模式完全一致：字面路径段优先于路径变量，{@code /search} 命中搜索页，
 * {@code /en/search} 命中子目录站点的搜索页。因此站点子目录不能与路由首段同名，所有路由的首段
 * 登记到 {@link FrontendRouteWords}，供站点保存时校验。
 *
 * @author PONY
 */
public class SiteRoutingHandlerMapping extends RequestMappingHandlerMapping {
    private static final String SUB_DIR_PREFIX = "/{" + UrlConstants.SUB_DIR_VARIABLE;
    private static final String ROOT = "/";

    private final FrontendRouteWords routeWords;

    public SiteRoutingHandlerMapping(FrontendRouteWords routeWords) {
        this.routeWords = routeWords;
    }

    @Nullable
    @Override
    protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
        RequestMappingInfo info = super.getMappingForMethod(method, handlerType);
        if (info == null) {
            return null;
        }
        Set<String> patterns = info.getPatternValues();
        patterns.forEach(routeWords::register);
        FrontendController frontend = AnnotatedElementUtils.findMergedAnnotation(handlerType, FrontendController.class);
        if (frontend == null || !frontend.subDir()) {
            return info;
        }
        Set<String> all = new LinkedHashSet<>(patterns);
        for (String pattern : patterns) {
            // 已带子目录前缀的模式不再叠加（同名路径变量重复会导致模式解析失败）
            if (!pattern.startsWith(SUB_DIR_PREFIX)) {
                // 首页的孪生模式是 /{subDir}，不带结尾斜杠
                all.add(pattern.isEmpty() || ROOT.equals(pattern)
                        ? UrlConstants.SUB_DIR : UrlConstants.SUB_DIR + pattern);
            }
        }
        return info.mutate().paths(all.toArray(String[]::new)).build();
    }
}
