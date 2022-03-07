package com.ofwise.util.security.csrf;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

import static org.springframework.http.HttpMethod.*;

/**
 * Csrf 令牌
 *
 * @author PONY
 */
public class CsrfToken {
    public static final String PARAMETER_NAME = "_csrf";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";
    public static final String COOKIE_NAME = "XSRF-TOKEN";

    public CsrfToken(String token) {
        this.token = token;
    }

    public static String loadTokenFromCookie(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        if (cookie != null) {
            String token = cookie.getValue();
            if (StringUtils.isNotBlank(token)) {
                return token;
            }
        }
        return null;
    }

    public static void saveToken(String token, HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        String contextPath = request.getContextPath();
        if (StringUtils.isNotBlank(contextPath)) {
            cookie.setPath(contextPath);
        } else {
            cookie.setPath("/");
        }
        if (token == null) {
            cookie.setMaxAge(0);
        } else {
            cookie.setMaxAge(-1);
        }
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);
    }

    public static boolean verify(HttpServletRequest request, HttpServletResponse response) {
        String token = loadTokenFromCookie(request);
        boolean missingToken = token == null;
        if (token == null) {
            token = RandomStringUtils.randomAlphanumeric(32);
            saveToken(token, request, response);
        }
        request.setAttribute(PARAMETER_NAME, new CsrfToken(token));
        if (!Arrays.asList(GET.name(), HEAD.name(), TRACE.name(), OPTIONS.name()).contains(request.getMethod())) {
            String actualToken = request.getHeader(HEADER_NAME);
            if (!token.equals(actualToken)) {
                try {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN,
                            missingToken ? "CSRF Token Missing" : "CSRF Token Invalid");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return false;
            }
        }
        return true;
    }

    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
