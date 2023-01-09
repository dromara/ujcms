package com.ujcms.cms.core.web.support;

import com.ujcms.util.web.exception.Http400Exception;
import com.ujcms.util.web.exception.Http404Exception;
import com.ujcms.util.web.exception.LogicException;
import com.ujcms.util.web.exception.MessagedException;
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
            if (ex instanceof Http404Exception) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, getMessage(ex, request));
                return new ModelAndView();
            } else if (ex instanceof LogicException || ex instanceof Http400Exception) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, getMessage(ex, request));
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
