package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.support.UrlConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前台 Controller 约定守卫：{@code web.frontend} 包下的 Controller 必须标注
 * {@link FrontendController}（否则子目录站点访问不到其页面，且没有任何报错），并且不得手写
 * {@code /{subDir}} 路由前缀（孪生模式由 {@code SiteRoutingHandlerMapping} 统一生成）。
 */
class FrontendControllerGuardTest {
    private static final String BASE_PACKAGE = "com.ujcms.cms";
    private static final String FRONTEND_PACKAGE = ".web.frontend";
    private static final String SUB_DIR_PREFIX = "{" + UrlConstants.SUB_DIR_VARIABLE;

    @Test
    void frontendControllersFollowTheSiteRoutingConvention() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        List<String> scanned = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (!type.getPackageName().endsWith(FRONTEND_PACKAGE)) {
                continue;
            }
            scanned.add(type.getSimpleName());
            if (!AnnotatedElementUtils.hasAnnotation(type, FrontendController.class)) {
                violations.add(type.getName() + ": use @FrontendController instead of @Controller");
            }
            collectHandWrittenSubDir(type, type, violations);
            for (Method method : type.getDeclaredMethods()) {
                collectHandWrittenSubDir(type, method, violations);
            }
        }
        assertFalse(scanned.isEmpty(), "no frontend controllers scanned under " + BASE_PACKAGE);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    /**
     * {@code @FrontendController("name")} 的名称须经 {@code @AliasFor} 传递到 {@code @Component.value}，Bean 名称才不变
     */
    @Test
    void frontendControllerValueBecomesTheBeanName() throws ClassNotFoundException {
        Class<?> type = Class.forName("com.ujcms.cms.core.web.frontend.ArticleController");
        Component component = AnnotatedElementUtils.findMergedAnnotation(type, Component.class);
        assertTrue(component != null && "frontendArticleController".equals(component.value()),
                "bean name not propagated: " + component);
    }

    private static void collectHandWrittenSubDir(Class<?> type, AnnotatedElement element, List<String> violations) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping.class);
        if (mapping == null) {
            return;
        }
        for (String path : mapping.path()) {
            if (path.contains(SUB_DIR_PREFIX)) {
                violations.add(type.getName() + ": hand-written sub-dir pattern " + path);
            }
        }
    }
}
