package com.ofwise.util.web;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 目录重定向、转发拦截器。只对 webapp 目录下文件有效，对 classpath 下 static 文件无效。
 * <p>
 * 如果静态页存在，则优先访问静态页
 * <p>
 * 如果访问的路径是目录，则重定向至目录路径。例如：访问 /foo 为目录，则重定向至 /foo/
 * <p>
 * 如果目录下 index.html 文件存在，则转发至该文件。例如：访问 /foo/，则转发至 /foo/index.html
 * <p>
 * 如果访问的路径不存在（没有实体文件），则重定向至文件。例如：访问 /foo/ ，则重定向至 /foo
 *
 * @author PONY
 * @see org.apache.catalina.servlets.DefaultServlet#doDirectoryRedirect
 */
public class DirectoryRedirectInterceptor implements HandlerInterceptor {
    private ResourceLoader resourceLoader;
    private boolean isFileToDir;
    private boolean isDirToFile;

    public DirectoryRedirectInterceptor(ResourceLoader resourceLoader, boolean isFileToDir, boolean isDirToFile) {
        this.resourceLoader = resourceLoader;
        this.isFileToDir = isFileToDir;
        this.isDirToFile = isDirToFile;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException, ServletException {
        String path = Servlets.getRelativePath(request);
        boolean isDirPath = path.endsWith("/");
        if (isFileToDir) {
            Resource resource = resourceLoader.getResource(path);
            // 资源存在
            if (resource.exists()) {
                // 资源存在且可读，直接放行
                if (resource.isReadable()) {
                    return true;
                }
                // 资源存在但不可读，为访问目录
                String indexLocation = path + (isDirPath ? "index.html" : "/index.html");
                Resource indexHtml = resourceLoader.getResource(indexLocation);
                // 文件夹下的 index.html 不可读，放行
                if (!indexHtml.isReadable()) {
                    return true;
                }
                // url 为目录形式，转发到 index.html
                if (isDirPath) {
                    request.getRequestDispatcher(indexLocation).forward(request, response);
                    return false;
                }
                // url 为文件形式，重定向到目录形式
                doDirectoryRedirect(request, response);
                return false;
            }
        }
        // 资源不存在，或不以目录的方式访问静态文件。将目录形式的url，则重定向至文件形式。如：/foo/ 重定向至 /foo
        if (isDirToFile && isDirPath && path.length() > 1) {
            doFileRedirect(request, response);
            return false;
        }
        return true;
    }

    private void doDirectoryRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doRedirect(request, response, true);
    }

    private void doFileRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doRedirect(request, response, false);
    }

    /**
     * 此段代码根据 Tomcat 官方代码编写
     *
     * @see org.apache.catalina.servlets.DefaultServlet#doDirectoryRedirect
     */
    @SuppressFBWarnings("HRS_REQUEST_PARAMETER_TO_HTTP_HEADER")
    private void doRedirect(HttpServletRequest request, HttpServletResponse response, boolean toDir)
            throws IOException {
        StringBuilder location = new StringBuilder(request.getRequestURI());
        char slash = '/';
        if (toDir) {
            location.append(slash);
        } else {
            location.deleteCharAt(location.length() - 1);
        }
        if (request.getQueryString() != null) {
            location.append('?');
            location.append(request.getQueryString());
        }
        // Avoid protocol relative redirects
        while (location.length() > 1 && location.charAt(1) == slash) {
            location.deleteCharAt(0);
        }
        response.sendRedirect(response.encodeRedirectURL(location.toString()));
    }
}
