package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.UrlConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * 为 {@link FrontendController} 处理方法的 {@code Site} 参数解析当前站点：从路由匹配结果中取
 * {@code subDir} 路径变量（由 {@link SiteRoutingHandlerMapping} 生成的孪生模式绑定，根站点请求时不存在），
 * 交给 {@link SiteResolver} 按域名和子目录解析。
 *
 * <p>解析发生在处理方法调用时（所有拦截器的 {@code preHandle} 之后），与原先在方法体内调用
 * {@code siteResolver.resolve(request, subDir)} 的时机一致。
 *
 * @author PONY
 */
public class SiteArgumentResolver implements HandlerMethodArgumentResolver {
    private final SiteResolver siteResolver;

    public SiteArgumentResolver(SiteResolver siteResolver) {
        this.siteResolver = siteResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Site.class == parameter.getParameterType()
                && parameter.getParameterAnnotations().length == 0
                && AnnotatedElementUtils.hasAnnotation(parameter.getContainingClass(), FrontendController.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("HttpServletRequest is required to resolve the current site");
        }
        return siteResolver.resolve(request, findSubDir(request));
    }

    @Nullable
    private static String findSubDir(HttpServletRequest request) {
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables
                && variables.get(UrlConstants.SUB_DIR_VARIABLE) instanceof String subDir) {
            return subDir;
        }
        return null;
    }
}
