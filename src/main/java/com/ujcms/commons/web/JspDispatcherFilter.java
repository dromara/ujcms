package com.ujcms.commons.web;

import org.apache.commons.lang3.StringUtils;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/**
 * JSP过滤器。直接允许访问 JSP 或 JSPX 文件会导致安全隐患，容易被木马攻击。默认不允许访问 JSP 和 JSPX 文件。
 * <p>
 * 对于确实需要访问 jsp 的情况，可以通过指定前缀的方式，只允许访问某个特定目录的 jsp 文件。
 *
 * @author PONY
 */
public class JspDispatcherFilter implements Filter {
    /**
     * 是否允许访问 JSP 或 JSPX 文件。默认 false 。
     */
    private boolean allowed = false;
    /**
     * 请求转发地址前缀。只允许特定目录的 jsp(jspx) 允许被访问。默认为 /jsp 。比如访问 /abc.jsp 通过请求转发实际上访问的文件为 /jsp/abc.jsp 。
     */
    private String prefix = "/jsp";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!allowed) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN, "JSP Access Denied");
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;
        String uri = req.getRequestURI();
        String ctx = req.getContextPath();
        if (StringUtils.isNotBlank(ctx)) {
            uri = uri.substring(ctx.length());
        }
        request.getRequestDispatcher(prefix + uri).forward(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        this.allowed = Boolean.parseBoolean(filterConfig.getInitParameter("allowed"));
        Optional.ofNullable(filterConfig.getInitParameter("prefix"))
                .filter(StringUtils::isNotBlank).ifPresent((it) -> this.prefix = it);
    }

    @Override
    public void destroy() {
    }
}
