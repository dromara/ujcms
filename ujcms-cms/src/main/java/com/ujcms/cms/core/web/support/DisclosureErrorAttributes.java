package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.support.ErrorDisclosure;
import com.ujcms.cms.core.support.TemplateErrorHandler;
import com.ujcms.cms.core.support.TemplateErrorRecorder;
import com.ujcms.common.web.exception.MessagedException;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * 错误属性（docs/spec/template-error-disclosure D3）：替换 Spring Boot 自动配置的 {@link DefaultErrorAttributes}，
 * 为错误页与 JSON 错误响应补上<b>错误编号</b>（{@code errorId}），并对匿名查看者隐去 5xx 的 {@code message}。
 * <p>
 * {@code server.error.include-message: always} 必须保留——控制台的错误弹窗与 4xx 业务错误（LogicException 等）
 * 依赖它；但 5xx 的 {@code message} 是异常原文（FreeMarker 定位信息、SQL、内部类名），不该给访客看。
 * 隐去的条件：状态码 ≥ 500、确有异常、且异常不是 {@link MessagedException}——站点禁用（503）等有意抛出的异常，
 * 其 message 本就是写给访客的提示。{@code exception}（仅类名）保留：错误页 {@code 5xx.html} 据它分派站点禁用页。
 * <p>
 * 编号：模板错误沿用 {@link TemplateErrorHandler} 写入请求属性的编号（记录器已落日志）；其它 5xx 在此分配并记一条
 * 带编号的日志（Tomcat 已记完整堆栈，此处只记关联行）。
 *
 * @author PONY
 */
@Component
public class DisclosureErrorAttributes extends DefaultErrorAttributes {
    private static final Logger logger = LoggerFactory.getLogger(DisclosureErrorAttributes.class);
    /** 错误属性名：错误编号。 */
    public static final String ERROR_ID = "errorId";
    private static final String MESSAGE = "message";

    private final ErrorDisclosure disclosure;

    public DisclosureErrorAttributes(ErrorDisclosure disclosure) {
        this.disclosure = disclosure;
    }

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);
        // 状态码与路径直接读 Servlet 错误属性：status / path 是否进入响应由 options 决定，这里的判断不能依赖它
        Object status = webRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE, RequestAttributes.SCOPE_REQUEST);
        if (!(status instanceof Integer code) || code < 500) {
            return attributes;
        }
        Throwable error = unwrap(getError(webRequest));
        if (error == null || error instanceof MessagedException) {
            return attributes;
        }
        Object path = webRequest.getAttribute(RequestDispatcher.ERROR_REQUEST_URI, RequestAttributes.SCOPE_REQUEST);
        attributes.put(ERROR_ID, resolveErrorId(webRequest, error, path));
        if (!disclosure.canViewDetails()) {
            attributes.remove(MESSAGE);
        }
        return attributes;
    }

    @Nullable
    private static Throwable unwrap(@Nullable Throwable error) {
        while (error instanceof ServletException && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private static String resolveErrorId(WebRequest webRequest, Throwable error, @Nullable Object path) {
        Object existing = webRequest.getAttribute(TemplateErrorHandler.ERROR_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (existing instanceof String id) {
            return id;
        }
        String id = TemplateErrorRecorder.newId();
        logger.error("Error [{}] path={}: {}", id, path, error.toString());
        return id;
    }
}
