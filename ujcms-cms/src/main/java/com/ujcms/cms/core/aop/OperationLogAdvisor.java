package com.ujcms.cms.core.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.cms.core.domain.OperationLog;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.OperationLogService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.web.Servlets;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

/**
 * 操作日志通知器。使用 Spring 原生 AOP API 实现，不依赖 aspectjweaver（EPL-2.0 协议，与 GPL 协议不兼容）。
 * <p>
 * 须标注为 {@code ROLE_INFRASTRUCTURE}，否则 {@code InfrastructureAdvisorAutoProxyCreator} 不会应用本通知器。
 *
 * @author PONY
 */
@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class OperationLogAdvisor extends DefaultPointcutAdvisor {
    private static final long serialVersionUID = 1L;

    public OperationLogAdvisor(UserService userService, OperationLogService service, Props props) {
        super(AnnotationMatchingPointcut.forMethodAnnotation(com.ujcms.cms.core.aop.annotations.OperationLog.class),
                new OperationLogInterceptor(userService, service, props));
    }

    private static class OperationLogInterceptor implements MethodInterceptor {
        private final UserService userService;
        private final OperationLogService service;
        private final Props props;

        OperationLogInterceptor(UserService userService, OperationLogService service, Props props) {
            this.userService = userService;
            this.service = service;
            this.props = props;
        }

        @Override
        @Nullable
        public Object invoke(@NonNull MethodInvocation invocation) throws Throwable {
            Object result;
            try {
                result = invocation.proceed();
            } catch (Exception e) {
                insertLog(invocation, ExceptionUtils.getStackTrace(e), OperationLog.STATUS_FAILURE);
                throw e;
            }
            insertLog(invocation, Constants.MAPPER.writeValueAsString(result), OperationLog.STATUS_SUCCESS);
            return result;
        }

        private void insertLog(MethodInvocation invocation, String responseEntity, short status)
                throws JsonProcessingException {
            OperationLog bean = operationLog(invocation);
            bean.setResponseEntity(responseEntity);
            bean.setStatus(status);
            service.asyncInsert(bean, bean.getExt());
        }

        private OperationLog operationLog(MethodInvocation invocation) throws JsonProcessingException {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                throw new IllegalStateException("RequestAttributes not exist!");
            }
            HttpServletRequest request = attributes.getRequest();
            User user = Optional.ofNullable(Contexts.findCurrentUser()).orElse(userService.anonymous());

            Method method = invocation.getMethod();
            com.ujcms.cms.core.aop.annotations.OperationLog annotation =
                    method.getAnnotation(com.ujcms.cms.core.aop.annotations.OperationLog.class);

            OperationLog bean = new OperationLog();
            bean.setModule(annotation.module());
            bean.setName(annotation.module() + "." + annotation.operation());
            String deleteSiteName = "site.delete";
            // 删除站点时，必须把日志记录在默认站点上，否则会出现siteId不存在的错误
            if (deleteSiteName.equalsIgnoreCase(bean.getName())) {
                bean.setSiteId(Contexts.getCurrentSite().getConfig().getDefaultSiteId());
            } else {
                bean.setSiteId(Contexts.getCurrentSiteId());
            }
            bean.setType(annotation.type().getType());
            bean.setUserId(user.getId());

            bean.setRequestMethod(request.getMethod());
            bean.setIp(Servlets.getRemoteAddr(request, props.getIpProxyDepth()));
            bean.setAudit(user.isAuditObject());
            bean.setStatus(OperationLog.STATUS_SUCCESS);

            bean.setRequestUrl(request.getRequestURL().toString());

            Object[] args = invocation.getArguments();
            Parameter[] params = method.getParameters();
            for (int i = 0, len = params.length; i < len; i += 1) {
                if (params[i].isAnnotationPresent(RequestBody.class)) {
                    bean.setRequestBody(Constants.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(args[i]));
                    break;
                }
            }
            return bean;
        }
    }
}
