package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.common.web.exception.*;
import org.springframework.core.Ordered;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;

/**
 * CMS异常处理
 *
 * @author PONY
 */
public class ExceptionResolver extends AbstractHandlerExceptionResolver {
    private final ConfigService configService;

    public ExceptionResolver(ConfigService configService) {
        this.configService = configService;
        setOrder(Ordered.LOWEST_PRECEDENCE);
        setWarnLogCategory(getClass().getName());
    }

    @Nullable
    @Override
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response,
                                              @Nullable Object handler, Exception ex) {
        Config config = configService.getUnique();
        request.setAttribute(Frontends.TEMPLATE_URL, config.getTemplateStorage().getUrl());
        Optional.ofNullable(Contexts.findCurrentSite())
                .ifPresent(it -> request.setAttribute(Frontends.SITE, it));
        try {
            if (ex instanceof Http404Exception) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, getMessage(ex, request));
                return new ModelAndView();
            } else if (ex instanceof LogicException || ex instanceof Http400Exception) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, getMessage(ex, request));
                return new ModelAndView();
            } else if (ex instanceof Http409Exception) {
                response.sendError(HttpServletResponse.SC_CONFLICT, getMessage(ex, request));
                return new ModelAndView();
            } else if (ex instanceof Http410Exception) {
                response.sendError(HttpServletResponse.SC_GONE, getMessage(ex, request));
                return new ModelAndView();
            } else if (ex instanceof Http503Exception) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, getMessage(ex, request));
                return new ModelAndView();
            } else if (ex instanceof PessimisticLockingFailureException) {
                // 死锁、等锁超时：两个操作撞在一起，重试即可（docs/spec/article-data-permission/plan.md D25）
                response.sendError(HttpServletResponse.SC_CONFLICT, getMessage(CONFLICT_LOCK, request));
                return new ModelAndView();
            } else if (ex instanceof DuplicateKeyException) {
                // 唯一键冲突：数据已存在，不把 SQL 错误交给调用方（D25）
                response.sendError(HttpServletResponse.SC_CONFLICT, getMessage(CONFLICT_DUPLICATE, request));
                return new ModelAndView();
            }
        } catch (Exception handlerEx) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failure while trying to resolve exception [" + ex.getClass().getName() + "]", handlerEx);
            }
        }
        return null;
    }

    private static final String CONFLICT_LOCK = "error.conflict.lock";
    private static final String CONFLICT_DUPLICATE = "error.conflict.duplicate";

    private static String getMessage(String code, HttpServletRequest request) {
        WebApplicationContext context = RequestContextUtils.findWebApplicationContext(request);
        return context != null ? context.getMessage(code, null, code, RequestContextUtils.getLocale(request)) : code;
    }

    @Nullable
    public static String getMessage(Exception ex, HttpServletRequest request) {
        if (ex instanceof MessagedException) {
            MessagedException messagedException = (MessagedException) ex;
            WebApplicationContext context = RequestContextUtils.findWebApplicationContext(request);
            String code = messagedException.getCode();
            if (context != null && code != null) {
                code = context.getMessage(code, messagedException.getArgs(), code, RequestContextUtils.getLocale(request));
            }
            return code;
        }
        return ex.getMessage();
    }
}
