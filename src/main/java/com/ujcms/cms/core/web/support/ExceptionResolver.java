package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.commons.web.exception.*;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
            }
        } catch (Exception handlerEx) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failure while trying to resolve exception [" + ex.getClass().getName() + "]", handlerEx);
            }
        }
        return null;
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
