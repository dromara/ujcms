package com.ujcms.cms.core.web.support;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Controller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 前台（模板渲染）Controller。
 *
 * <p>标注此注解的 Controller 只需声明不带站点子目录的路由（如 {@code /article/{key}}）：
 * <ul>
 * <li>{@link SiteRoutingHandlerMapping} 为每个路由自动生成 {@code /{subDir}} 孪生模式，
 * 使子目录站点可经 {@code /{subDir}/article/{key}} 访问。不要手写 {@code /{subDir:[\w-]+}} 前缀；</li>
 * <li>处理方法声明 {@code Site site} 参数即可获得当前站点，由 {@link SiteArgumentResolver} 经
 * {@link SiteResolver} 按域名和子目录解析（同时设置 {@code Contexts.currentSite}）。</li>
 * </ul>
 * 不属于任何子目录站点的全局流程（如第三方登录：回调地址在第三方平台登记、固定不带子目录，流程中途的页面只会被
 * 服务端重定向到不带子目录的地址）设置 {@link #subDir()} 为 {@code false}，不生成永远不会被访问的孪生路由。
 *
 * <p>{@code web.frontend} 包下的 Controller 必须使用此注解（{@code FrontendControllerGuardTest} 强制）。
 *
 * @author PONY
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Controller
public @interface FrontendController {
    /**
     * Bean 名称。前台 Controller 与 API Controller 常常同名，需显式指定以便区分
     */
    @AliasFor(annotation = Controller.class)
    String value() default "";

    /**
     * 是否生成 {@code /{subDir}} 孪生路由。为 {@code false} 时只有不带子目录的路由，{@code Site} 参数按域名解析
     */
    boolean subDir() default true;
}
