package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.ErrorDisclosure;
import com.ujcms.cms.core.support.TemplateErrorHandler;
import com.ujcms.common.web.exception.Http503Exception;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 错误属性（docs/spec/template-error-disclosure D3）：5xx 的 {@code message} 只对后台操作员保留，匿名查看者得到
 * 错误编号；有意抛出的 {@code MessagedException}（站点禁用等）与 4xx 原样保留；模板错误沿用处理器挂在请求上的编号。
 */
class DisclosureErrorAttributesTest {
    private final UserService userService = mock(UserService.class);
    private final DisclosureErrorAttributes attributes = new DisclosureErrorAttributes(new ErrorDisclosure(userService));

    @AfterEach
    void clear() {
        Contexts.clearCurrentUser();
    }

    @Test
    void anonymousFiveHundredHidesTheMessageAndCarriesAnId() {
        Map<String, Object> map = attributesOf(500, new IllegalStateException("boom"), "boom", r -> { });

        assertFalse(map.containsKey("message"), map.toString());
        assertEquals("java.lang.IllegalStateException", map.get("exception"), "class name stays: 5xx.html dispatches on it");
        assertTrue(String.valueOf(map.get("errorId")).matches("[0-9a-f]{8}"), map.toString());
    }

    @Test
    void operatorKeepsTheMessage() {
        User admin = new User();
        admin.setType(User.TYPE_NORMAL);
        Contexts.setCurrentUser(admin);

        Map<String, Object> map = attributesOf(500, new IllegalStateException("boom"), "boom", r -> { });

        assertEquals("boom", map.get("message"));
        assertTrue(map.containsKey("errorId"));
    }

    @Test
    void templateErrorReusesTheRecordedId() {
        Map<String, Object> map = attributesOf(500, new IllegalStateException("boom"), "boom",
                r -> r.setAttribute(TemplateErrorHandler.ERROR_ID_ATTRIBUTE, "deadbeef"));

        assertEquals("deadbeef", map.get("errorId"));
    }

    @Test
    void deliberateFiveXxKeepsItsVisitorFacingMessage() {
        Map<String, Object> map = attributesOf(503, new Http503Exception("error.siteDisabled"), "站点已禁用", r -> { });

        assertEquals("站点已禁用", map.get("message"));
        assertFalse(map.containsKey("errorId"));
    }

    @Test
    void wrappedMessagedExceptionIsRecognised() {
        ServletException wrapped = new ServletException("Request processing failed",
                new Http503Exception("error.siteDisabled"));
        Map<String, Object> map = attributesOf(503, wrapped, "站点已禁用", r -> { });

        assertEquals("站点已禁用", map.get("message"));
    }

    @Test
    void fourXxIsUntouched() {
        Map<String, Object> map = attributesOf(404, null, "not found", r -> { });

        assertEquals("not found", map.get("message"));
        assertFalse(map.containsKey("errorId"));
    }

    private Map<String, Object> attributesOf(int status, @Nullable Throwable error, String message,
                                             Consumer<MockHttpServletRequest> customizer) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/channel/news");
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
        if (error != null) {
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, error);
        }
        customizer.accept(request);
        return attributes.getErrorAttributes(new ServletWebRequest(request),
                ErrorAttributeOptions.of(Include.MESSAGE, Include.EXCEPTION));
    }
}
