package com.ujcms.cms.core.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.cms.core.domain.OperationLog;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.OperationLogService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.commons.web.Servlets;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

/**
 * 操作日志切面
 *
 * @author PONY
 */
@Aspect
@Component
public class OperationLogAspect {
    private final UserService userService;
    private final OperationLogService service;

    public OperationLogAspect(UserService userService, OperationLogService service) {
        this.userService = userService;
        this.service = service;
    }

    /**
     * 切入点
     */
    @Pointcut("@annotation(com.ujcms.cms.core.aop.annotations.OperationLog)")
    private void pointcut() {
    }


    @AfterReturning(pointcut = "pointcut()", returning = "result")
    public void doAfterReturning(JoinPoint joinPoint, Object result) throws JsonProcessingException {
        OperationLog bean = operationLog(joinPoint);
        bean.setResponseEntity(Constants.MAPPER.writeValueAsString(result));
        bean.setStatus(OperationLog.STATUS_SUCCESS);
        service.asyncInsert(bean, bean.getExt());
    }

    @AfterThrowing(pointcut = "pointcut()", throwing = "exception")
    public void doAfterThrowing(JoinPoint joinPoint, Exception exception) throws JsonProcessingException {
        OperationLog bean = operationLog(joinPoint);
        bean.setResponseEntity(ExceptionUtils.getStackTrace(exception));
        bean.setStatus(OperationLog.STATUS_FAILURE);
        service.asyncInsert(bean, bean.getExt());
    }

    private OperationLog operationLog(JoinPoint joinPoint) throws JsonProcessingException {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("RequestAttributes not exist!");
        }
        HttpServletRequest request = attributes.getRequest();
        User user = Optional.ofNullable(Contexts.findCurrentUser()).orElse(userService.anonymous());

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
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
        bean.setIp(Servlets.getRemoteAddr(request));
        bean.setAudit(user.isAuditObject());
        bean.setStatus(OperationLog.STATUS_SUCCESS);

        bean.setRequestUrl(request.getRequestURL().toString());

        Object[] args = joinPoint.getArgs();
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
