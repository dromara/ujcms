package com.ujcms.cms.core.web.support;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 前台重定向工具。
 *
 * @author PONY
 */
public final class Redirects {
    private Redirects() {
    }

    /**
     * 301 至规范 URL，并禁止浏览器缓存该重定向：固定链接形态切换后，旧规范形会反向 301 到新规范形，
     * 若浏览器缓存了先前的 301，两条缓存记录会互相跳转形成环。Location 原样写入（{@code getUrl()} 已含上下文路径），
     * 不经 {@code redirect:} 前缀的上下文相对处理。
     * Cache-Control 直接写在 Servlet 响应上（setHeader 覆盖）：SecurityHeadersFilter 在进入控制器前已给前台页写入
     * {@code public, max-age=300}，而 ResponseEntity 的头以 addHeader 追加，会得到两条 Cache-Control
     */
    public static ResponseEntity<Void> permanent(HttpServletResponse response, String url) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noCache().getHeaderValue());
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }
}
