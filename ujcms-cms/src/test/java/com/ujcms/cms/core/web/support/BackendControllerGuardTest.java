package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.support.UrlConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后台 Controller 授权守卫（backend-authorization §2）：{@code /api/backend/**} 在过滤链整体放行，
 * 动作级授权只靠 {@link PreAuthorize}，漏写即等于无权限判定。每个后台处理方法（含父类继承的）必须在
 * 方法或类上声明 {@code @PreAuthorize}；{@code BackendInterceptor} 在运行时对同一条件 403，本测试在
 * 构建期把它前移。注解查找走完整类型层次（父类、接口），与 Spring Security 一致。无白名单：只读目录型
 * 接口也至少声明 {@code 'backend'}。
 */
class BackendControllerGuardTest {
    private static final String BASE_PACKAGE = "com.ujcms.cms";

    @Test
    void everyBackendHandlerDeclaresPreAuthorize() throws ClassNotFoundException {
        List<String> scanned = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        collect(scanned, violations);
        assertFalse(scanned.isEmpty(), "no backend controllers scanned under " + BASE_PACKAGE);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    private static void collect(List<String> scanned, List<String> violations) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            Map<Method, RequestMapping> handlers = MethodIntrospector.selectMethods(type,
                    (MethodIntrospector.MetadataLookup<RequestMapping>) method ->
                            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class));
            if (!isBackendController(type, handlers)) {
                continue;
            }
            scanned.add(type.getSimpleName());
            if (AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class) != null) {
                continue;
            }
            for (Method method : handlers.keySet()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class) == null) {
                    violations.add(type.getName() + "#" + method.getName() + ": backend handler without @PreAuthorize");
                }
            }
        }
    }

    /**
     * 后台 Controller：类级或方法级映射落在 {@code /api/backend} 下，或位于 {@code web.backendapi} 包。
     */
    private static boolean isBackendController(Class<?> type, Map<Method, RequestMapping> handlers) {
        if (type.getPackageName().endsWith(".web.backendapi")) {
            return true;
        }
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
        if (classMapping != null && startsWithBackendApi(classMapping)) {
            return true;
        }
        return handlers.values().stream().anyMatch(BackendControllerGuardTest::startsWithBackendApi);
    }

    private static boolean startsWithBackendApi(RequestMapping mapping) {
        for (String path : mapping.path()) {
            if (path.startsWith(UrlConstants.BACKEND_API)) {
                return true;
            }
        }
        return false;
    }
}
