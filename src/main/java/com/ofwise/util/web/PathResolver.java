package com.ofwise.util.web;

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

    public PathResolver(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public String getRealPath(String uri) {
        return servletContext.getRealPath(uri);
    }

    public String getRealPath(String uri, @Nullable String prefix) {
        if (prefix != null && prefix.startsWith(FILE_PREFIX)) {
            return prefix.substring(FILE_PREFIX.length()) + uri.replace('/', File.separatorChar);
        } else {
            return servletContext.getRealPath(prefix == null ? uri : prefix + uri);
        }
    }

    public String getContextPath() {
        return servletContext.getContextPath();
    }

    private ServletContext servletContext;
}
