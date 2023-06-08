package com.ujcms.commons.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;

import javax.servlet.ServletContext;
import java.io.File;

/**
 * 使用ServletContext获取真实路径。支持 file: 前缀获取绝对路径。
 *
 * @author liufang
 */
public class PathResolver {
    /**
     * 文件地址前缀
     */
    public static final String FILE_PREFIX = "file:";

    public PathResolver(ObjectProvider<ServletContext> servletContextProvider) {
        this.servletContextProvider = servletContextProvider;
    }


    public String getContextPath() {
        return getServletContext().getContextPath();
    }

    public String getRealPath(String uri) {
        return getServletContext().getRealPath(uri);
    }

    public String getRealPath(String uri, @Nullable String prefix) {
        if (prefix != null && prefix.startsWith(FILE_PREFIX)) {
            return prefix.substring(FILE_PREFIX.length()) + uri.replace('/', File.separatorChar);
        } else {
            return getServletContext().getRealPath(prefix == null ? uri : prefix + uri);
        }
    }

    private ObjectProvider<ServletContext> servletContextProvider;

    public ServletContext getServletContext() {
        return servletContextProvider.getObject();
    }

    // @Nullable
    // private ServletContext servletContext;
    //
    // public ServletContext getServletContext() {
    //     if (servletContext == null) {
    //         throw new IllegalStateException("servletContext cannot be null");
    //     }
    //     return servletContext;
    // }
    //
    // @Override
    // public void setServletContext(ServletContext servletContext) {
    //     this.servletContext = servletContext;
    // }
}
