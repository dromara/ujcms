package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.service.InstallService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 前台未安装守卫（install-flow §3）：安装状态非 INSTALLED 时，模板渲染的前台请求短路到
 * 一个自包含的"系统尚未安装"页面（链接控制台 {@code /cp/} 完成安装），而不是把
 * {@code ConfigService.getUnique()} 的 {@code IllegalStateException}（乃至异常解析器自身
 * 的二次失败）暴露成容器 500。挡在 {@code UrlRedirectInterceptor}（前台首个
 * {@code getUnique()} 调用点）之前注册；{@code /api/**}、{@code /cp/**}、错误页与静态
 * 资源不经过本守卫。
 *
 * <p>已安装后 {@link InstallService#isInstalled()} 为粘滞内存判定，正常运行零开销。
 *
 * @author PONY
 */
public class InstallGuardInterceptor implements HandlerInterceptor {
    private final InstallService installService;

    public InstallGuardInterceptor(InstallService installService) {
        this.installService = installService;
    }

    /**
     * 失败路径上渲染：样式全部内联，零外部请求，不依赖任何数据行与模板目录。
     */
    private static final String NOT_INSTALLED_HTML = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>系统尚未安装</title>
            <style>
            body{margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;
            font-family:system-ui,-apple-system,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",sans-serif;
            background:#f3f4f6;color:#374151}
            .card{background:#fff;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.1);
            padding:48px 56px;text-align:center;max-width:28rem}
            h1{font-size:1.25rem;margin:0 0 12px;color:#111827}
            p{margin:0 0 24px;line-height:1.6}
            a{display:inline-block;padding:10px 28px;border-radius:6px;background:#2563eb;color:#fff;
            text-decoration:none}
            a:hover{background:#1d4ed8}
            </style>
            </head>
            <body>
            <div class="card">
            <h1>系统尚未安装</h1>
            <p>系统核心已就绪，但安装尚未完成。请进入管理控制台完成安装向导（安装令牌见启动日志）。</p>
            <a href="/cp/">进入控制台安装</a>
            </div>
            </body>
            </html>
            """;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (installService.isInstalled()) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("text/html;charset=UTF-8");
        response.getOutputStream().write(NOT_INSTALLED_HTML.getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
