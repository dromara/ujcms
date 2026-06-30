package com.ujcms.common.freemarker;

import freemarker.ext.beans.ClassMemberAccessPolicy;
import freemarker.ext.beans.DefaultMemberAccessPolicy;
import freemarker.ext.beans.MemberAccessPolicy;
import freemarker.template.Version;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * FreeMarker 成员访问策略。在官方 {@link DefaultMemberAccessPolicy} 的基础上，额外禁止访问一批
 * “能力型”类型（如 {@code ServletContext}、Spring {@code ApplicationContext}、{@code ClassLoader}、
 * Tomcat {@code InstanceManager} 等）的<strong>全部</strong>成员。
 *
 * <p>用于防御模板注入（SSTI）提权为远程命令执行（RCE）。后台模板编辑功能允许管理员写入任意 FreeMarker
 * 模板，攻击者可借助 {@code Application}/{@code ServletContext} 取得 Spring 容器与 Tomcat
 * {@code InstanceManager} 的实时引用，进而绕过 {@code ?new}/{@code ?api} 限制实例化任意类执行命令，例如：
 * <pre>
 * [#assign ctx=Application["org.springframework.web.context.WebApplicationContext.ROOT"] /]
 * [#assign sc=ctx.getBean("pathResolver").getServletContext() /]
 * [#assign p=sc.getAttribute("org.apache.tomcat.InstanceManager")
 *         .newInstance("org.springframework.expression.spel.standard.SpelExpressionParser") /]
 * ${p.parseExpression("...").getValue()}
 * </pre>
 * 该利用链上的 {@code getBean}、{@code getServletContext}、{@code getAttribute}、{@code newInstance}、
 * {@code parseExpression}、{@code getValue} 都是普通方法调用，官方策略与 {@code allows_nothing} 均无法拦截。
 * 只要被访问对象是黑名单类型的实例（含子类型），即禁止其一切成员，即可在多个环节阻断该链条。
 *
 * <p>该策略覆盖所有渲染路径（动态渲染、静态化生成及未来新增路径），是阻断 RCE 的核心防线。
 *
 * @author PONY
 */
public class SecureMemberAccessPolicy implements MemberAccessPolicy {
    /**
     * 黑名单类型。只要被访问对象 {@code instanceof} 其中任一类型，即禁止其全部成员。
     * 通过类名加载，类在当前运行环境不存在时（不同 Servlet 容器、可选依赖未引入）自动跳过。
     */
    private static final String[] BLACKLISTED_TYPE_NAMES = {
            // 反射、类加载与进程执行
            "java.lang.Class",
            "java.lang.ClassLoader",
            "java.lang.Runtime",
            "java.lang.Process",
            "java.lang.ProcessBuilder",
            "java.lang.System",
            "java.lang.Thread",
            "java.lang.ThreadGroup",
            "java.lang.reflect.AccessibleObject",
            "java.lang.reflect.Member",
            "java.lang.invoke.MethodHandle",
            "java.lang.invoke.MethodHandles",
            // 脚本引擎与 JNDI
            "javax.script.ScriptEngine",
            "javax.script.ScriptEngineManager",
            "javax.naming.Context",
            // Servlet 容器（可经此取得 ServletContext 属性，如 Tomcat InstanceManager）
            "jakarta.servlet.ServletContext",
            "jakarta.servlet.ServletRequest",
            "jakarta.servlet.ServletResponse",
            "jakarta.servlet.http.HttpSession",
            "org.apache.tomcat.InstanceManager",
            // Spring 容器与资源/表达式（可经此取得任意 Bean、读取资源、执行 SpEL）
            "org.springframework.beans.factory.BeanFactory",
            "org.springframework.context.ApplicationContext",
            "org.springframework.core.io.ResourceLoader",
            "org.springframework.expression.ExpressionParser",
            "org.springframework.expression.Expression",
            // 数据源（可经此取得数据库连接）
            "javax.sql.DataSource",
            "java.sql.Connection",
    };

    /** 拒绝目标对象一切成员访问的策略。 */
    private static final ClassMemberAccessPolicy DENY_ALL = new ClassMemberAccessPolicy() {
        @Override
        public boolean isMethodExposed(Method method) {
            return false;
        }

        @Override
        public boolean isConstructorExposed(Constructor<?> constructor) {
            return false;
        }

        @Override
        public boolean isFieldExposed(Field field) {
            return false;
        }
    };

    private final DefaultMemberAccessPolicy defaultPolicy;
    private final List<Class<?>> blacklist;

    public SecureMemberAccessPolicy(Version version) {
        this.defaultPolicy = DefaultMemberAccessPolicy.getInstance(version);
        List<Class<?>> classes = new ArrayList<>(BLACKLISTED_TYPE_NAMES.length);
        ClassLoader classLoader = SecureMemberAccessPolicy.class.getClassLoader();
        for (String name : BLACKLISTED_TYPE_NAMES) {
            try {
                classes.add(Class.forName(name, false, classLoader));
            } catch (ClassNotFoundException | LinkageError e) {
                // 该类型在当前运行环境不存在，无需拦截，跳过即可。
            }
        }
        this.blacklist = classes;
    }

    @Override
    public ClassMemberAccessPolicy forClass(Class<?> contextClass) {
        for (Class<?> blocked : blacklist) {
            if (blocked.isAssignableFrom(contextClass)) {
                return DENY_ALL;
            }
        }
        // 非黑名单类型，沿用官方默认策略（仍会拦截 Runtime.exec、getClassLoader 等经典反射利用）。
        return defaultPolicy.forClass(contextClass);
    }

    @Override
    public boolean isToStringAlwaysExposed() {
        return defaultPolicy.isToStringAlwaysExposed();
    }
}
