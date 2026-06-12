package com.ujcms.cms.core.aop;

import com.ujcms.cms.core.service.OperationLogService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import org.junit.jupiter.api.Test;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 操作日志通知器测试。验证不依赖 aspectjweaver 的情况下，{@code InfrastructureAdvisorAutoProxyCreator}
 * 能正确应用 {@code ROLE_INFRASTRUCTURE} 角色的通知器（与 Spring Boot 无 aspectjweaver 时的
 * {@code AopAutoConfiguration.ClassProxyingConfiguration} 行为一致）。
 *
 * @author PONY
 */
class OperationLogAdvisorTest {

    @Test
    void advisorAppliedByInfrastructureAutoProxyCreator() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // 与 Spring Boot AopAutoConfiguration.ClassProxyingConfiguration 注册逻辑一致
            AopConfigUtils.registerAutoProxyCreatorIfNecessary(context);
            AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(context);
            context.registerBean(UserService.class, () -> mock(UserService.class));
            context.registerBean(OperationLogService.class, () -> mock(OperationLogService.class));
            context.registerBean(Props.class, () -> mock(Props.class));
            context.register(OperationLogAdvisor.class, SampleController.class);
            context.refresh();

            SampleController controller = context.getBean(SampleController.class);
            assertTrue(AopUtils.isCglibProxy(controller), "标注 @OperationLog 方法的 Bean 应被代理");
            // 拦截器在无请求上下文时抛出 IllegalStateException，证明拦截器确实生效
            IllegalStateException e = assertThrows(IllegalStateException.class, controller::logged);
            assertEquals("RequestAttributes not exist!", e.getMessage());
            // 未标注 @OperationLog 的方法不受拦截
            assertEquals("plain", controller.plain());
        }
    }

    static class SampleController {
        @com.ujcms.cms.core.aop.annotations.OperationLog(module = "test", operation = "logged")
        public String logged() {
            return "logged";
        }

        public String plain() {
            return "plain";
        }
    }
}
