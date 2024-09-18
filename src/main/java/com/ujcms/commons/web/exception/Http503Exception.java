package com.ujcms.commons.web.exception;

import org.springframework.lang.Nullable;

/**
 * SC_SERVICE_UNAVAILABLE 服务不可用 异常。用于页面暂时不可访问，比如网站临时维护、服务器压力过大等情况。
 * <p>
 * 百度暂时不会删除这个页面，短期内会再次访问；如果长期返回`503`，才会删除这个页面。由此可知，如果临时关闭网站，不要返回`404`，要返回`503`。
 *
 * @author PONY
 * @see javax.servlet.http.HttpServletResponse#SC_SERVICE_UNAVAILABLE
 */
public class Http503Exception extends AbstractMessagedException {
    private static final long serialVersionUID = 1L;

    public Http503Exception(String code, @Nullable String... args) {
        super(code, args);
    }
}