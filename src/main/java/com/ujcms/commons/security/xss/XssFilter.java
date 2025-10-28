package com.ujcms.commons.security.xss;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * XSS 过滤器
 *
 * @author PONY
 */
public class XssFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        XssHttpServletRequestWrapper xssRequestWrapper = new XssHttpServletRequestWrapper((HttpServletRequest) request);
        chain.doFilter(xssRequestWrapper, response);
    }
}