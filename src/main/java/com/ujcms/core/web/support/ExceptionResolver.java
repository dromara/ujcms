package com.ujcms.core.web.support;

import com.ofwise.util.web.exception.Http400Exception;
import com.ofwise.util.web.exception.Http401Exception;
import com.ofwise.util.web.exception.Http403Exception;
import com.ofwise.util.web.exception.Http404Exception;
import com.ofwise.util.web.exception.LogicException;
import com.ofwise.util.web.exception.MessagedException;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.UnauthenticatedException;
import org.apache.shiro.authz.UnauthorizedException;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * CMS异常处理
 *
 * @author PONY
 */
public class ExceptionResolver extends AbstractHandlerExceptionResolver {
    public ExceptionResolver() {
        setOrder(Ordered.LOWEST_PRECEDENCE);
        setWarnLogCategory(getClass().getName());
    }

    @Nullable
    @Override
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response,
                                              @Nullable Object handler, Exception ex) {
        try {
            if (ex instanceof UnauthenticatedException || ex instanceof Http401Exception) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return new ModelAndView();
            } else if (ex instanceof UnauthorizedException) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return new ModelAndView();
            } else if (ex instanceof AuthorizationException) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return new ModelAndView();
            } else if (ex instanceof Http403Exception) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, getMessage((MessagedException) ex, request));
                return new ModelAndView();
            } else if (ex instanceof Http404Exception) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, getMessage((MessagedException) ex, request));
                return new ModelAndView();
            } else if (ex instanceof LogicException || ex instanceof Http400Exception) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, getMessage((MessagedException) ex, request));
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
    private String getMessage(MessagedException ex, HttpServletRequest request) {
        WebApplicationContext context = RequestContextUtils.findWebApplicationContext(request);
        String message = ex.getMessage();
        if (context != null) {
            message = context.getMessage(message, ex.getArgs(), message, RequestContextUtils.getLocale(request));
        }
        return message;
    }
}
