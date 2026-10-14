package com.ujcms.cms.core.web.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.util.DigestUtils;
import org.springframework.web.context.request.ServletWebRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/**
 * 前台基础设施文件（sitemap、robots.txt、llms.txt）的条件响应：弱 ETag + Last-Modified，
 * 经 {@link ServletWebRequest#checkNotModified(String, long)} 回答 If-None-Match / If-Modified-Since（304）。
 * <p>
 * ETag 取弱形式 {@code W/"md5"}：Tomcat 10 不压缩带强 ETag 的响应，弱 ETag 才能与 {@code server.compression} 配合
 * 输出 gzip（docs/spec/sitemap/plan.md D9）。正文与 ETag 一起缓存在 {@link Body} 中，避免每次请求重算摘要。
 *
 * @author PONY
 */
public final class ConditionalResponses {
    private ConditionalResponses() {
    }

    /**
     * 已编码的响应正文及其校验信息
     *
     * @param bytes        UTF-8 正文
     * @param etag         弱 ETag（含 {@code W/} 与引号）
     * @param lastModified 最后修改时间（epoch 毫秒）；无依据时为 -1，不输出 Last-Modified
     */
    public record Body(byte[] bytes, String etag, long lastModified) {
    }

    public static Body body(String text, @Nullable OffsetDateTime lastModified) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new Body(bytes, weakEtag(bytes), lastModified != null ? lastModified.toInstant().toEpochMilli() : -1);
    }

    /**
     * 写出正文，或在客户端缓存仍有效时回 304
     *
     * @return 已写出正文为 {@code true}；304 为 {@code false}
     */
    public static boolean write(HttpServletRequest request, HttpServletResponse response, Body body,
                                String contentType) throws IOException {
        // checkNotModified 自行写入 ETag 与 Last-Modified 响应头（304 与 200 均写）
        if (new ServletWebRequest(request, response).checkNotModified(body.etag(), body.lastModified())) {
            return false;
        }
        response.setContentType(contentType);
        response.getOutputStream().write(body.bytes());
        return true;
    }

    // 改编自 Spring Framework 的 ShallowEtagHeaderFilter#generateETagHeaderValue（Apache License 2.0，见 NOTICE），2026-09-09
    static String weakEtag(byte[] bytes) {
        // W/ + " + 32 位 md5 + "
        StringBuilder builder = new StringBuilder(37);
        builder.append("W/\"");
        DigestUtils.appendMd5DigestAsHex(bytes, builder);
        builder.append('"');
        return builder.toString();
    }
}
