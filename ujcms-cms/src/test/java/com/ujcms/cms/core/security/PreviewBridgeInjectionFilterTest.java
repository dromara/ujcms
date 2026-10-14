package com.ujcms.cms.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预览脚本注入过滤器测试（visual-builder §3；design-review P2-1）：仅「GET + 预览参数 + 预览身份已置
 * + 200 text/html」时注入脚本标签——草稿桥在 {@code </body>} 前（defer），预览上下文脚本（草稿 / 主题
 * 预览都注入；草稿部分由脚本按顶层/内嵌自判）在 {@code <head>} 起始处（阻塞，先于主题脚本安装
 * fetch/XHR 补丁；无 {@code <head>} 退回 {@code </body>} 前）；其余路径响应体保持原样。
 */
class PreviewBridgeInjectionFilterTest {
    private static final String HTML = "<html><head></head><body><p>正文</p></body></html>";
    // 期望标签的 ?v= 指纹按与过滤器相同的算法对同一 classpath 资源现算——脚本每次重建指纹都变，
    // 硬编码哈希会在下次重建时腐坏；现算同时端到端验证「指纹 = 脚本内容」的对应关系
    private static final String SCRIPT_TAG =
            scriptTag(PreviewBridgeInjectionFilter.BRIDGE_SCRIPT_PATH, true);
    private static final String CONTEXT_SCRIPT_TAG =
            scriptTag(PreviewBridgeInjectionFilter.PREVIEW_CONTEXT_SCRIPT_PATH, false);

    private static String scriptTag(String path, boolean defer) {
        return "<script src=\"" + path + "?v=" + scriptVersion(path) + "\"" + (defer ? " defer" : "") + "></script>";
    }

    private static String scriptVersion(String path) {
        try (InputStream in = PreviewBridgeInjectionFilterTest.class.getResourceAsStream("/static" + path)) {
            if (in == null) {
                return "";
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private final PreviewBridgeInjectionFilter filter = new PreviewBridgeInjectionFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, Collections.emptyList()));
    }

    private static MockHttpServletRequest previewRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setParameter("_preview", "draft");
        return request;
    }

    private static FilterChain htmlChain(String body) {
        return htmlChain(body, "text/html;charset=UTF-8", HttpServletResponse.SC_OK);
    }

    private static FilterChain htmlChain(String body, String contentType, int status) {
        return (req, res) -> {
            HttpServletResponse response = (HttpServletResponse) res;
            response.setStatus(status);
            response.setContentType(contentType);
            response.getWriter().write(body);
        };
    }

    private String run(MockHttpServletRequest request, FilterChain chain) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void draftPreviewInjectsBridgeBeforeBodyCloseAndContextScriptAtHeadStart() throws Exception {
        // 桥：</body> 前 defer（构建器 iframe 激活）；上下文脚本：<head> 起始阻塞——顶层「新标签打开」的草稿
        // 整站试看靠它为同源链接/表单/子请求补 _preview=draft&_ptoken（被内嵌时脚本自判不插手）
        authenticate();
        String out = run(previewRequest(), htmlChain(HTML));
        assertThat(out).isEqualTo("<html><head>" + CONTEXT_SCRIPT_TAG + "</head><body><p>正文</p>" + SCRIPT_TAG + "</body></html>");
        assertThat(SCRIPT_TAG).contains(" defer");
        assertThat(CONTEXT_SCRIPT_TAG).doesNotContain("defer");
    }

    @Test
    void injectionHonorsContextPath() throws Exception {
        authenticate();
        MockHttpServletRequest request = previewRequest();
        request.setContextPath("/site");
        String out = run(request, htmlChain(HTML));
        assertThat(out).contains("<script src=\"/site/_ujcms/preview-bridge.js?v=");
    }

    // —— design-review P2-1 / H1：主题预览覆盖（?_theme= → preview-context.js；与草稿桥可叠加）——

    private static MockHttpServletRequest themePreviewRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setParameter("_theme", "candidate");
        return request;
    }

    @Test
    void themePreviewInjectsBlockingScriptAtHeadStartWithoutBridge() throws Exception {
        // 主题脚本以 fetch/XHR 取回的模板片段须同样携带 _theme：补丁必须先于主题自身脚本安装
        // → <head> 起始处、无 defer；非草稿预览不注入桥
        authenticate();
        String out = run(themePreviewRequest(), htmlChain(HTML));
        assertThat(out).isEqualTo("<html><head>" + CONTEXT_SCRIPT_TAG + "</head><body><p>正文</p></body></html>");
        assertThat(CONTEXT_SCRIPT_TAG).doesNotContain("defer");
    }

    @Test
    void themePreviewHeadTagWithAttributesCaseInsensitive() throws Exception {
        authenticate();
        String html = "<!doctype html><HTML><HEAD lang=\"zh\"\n data-x=\"1\"><title>t</title></HEAD><body></body></HTML>";
        String out = run(themePreviewRequest(), htmlChain(html));
        assertThat(out).isEqualTo("<!doctype html><HTML><HEAD lang=\"zh\"\n data-x=\"1\">" + CONTEXT_SCRIPT_TAG
                + "<title>t</title></HEAD><body></body></HTML>");
    }

    @Test
    void themePreviewHeaderElementIsNotHead() throws Exception {
        // <header> 不是 <head>：无 <head> 时退回 </body> 前
        authenticate();
        String html = "<html><body><header>顶栏</header><p>正文</p></body></html>";
        String out = run(themePreviewRequest(), htmlChain(html));
        assertThat(out).isEqualTo("<html><body><header>顶栏</header><p>正文</p>" + CONTEXT_SCRIPT_TAG + "</body></html>");
    }

    @Test
    void themePreviewFragmentWithoutHeadOrBodyIsLeftAlone() throws Exception {
        // /app/user 之类片段响应（本身携带 _theme 的子请求）：无处可注入，原样放行
        authenticate();
        String fragment = "<div class=\"user\">张三</div>";
        String out = run(themePreviewRequest(), htmlChain(fragment));
        assertThat(out).isEqualTo(fragment);
    }

    @Test
    void combinedDraftAndThemePreviewInjectsBothScripts() throws Exception {
        authenticate();
        MockHttpServletRequest request = previewRequest();
        request.setParameter("_theme", "candidate");
        String out = run(request, htmlChain(HTML));
        assertThat(out).isEqualTo(
                "<html><head>" + CONTEXT_SCRIPT_TAG + "</head><body><p>正文</p>" + SCRIPT_TAG + "</body></html>");
    }

    @Test
    void combinedPreviewWithoutHeadPutsBothBeforeBodyClose() throws Exception {
        authenticate();
        MockHttpServletRequest request = previewRequest();
        request.setParameter("_theme", "candidate");
        String html = "<html><body><p>正文</p></body></html>";
        String out = run(request, htmlChain(html));
        assertThat(out).isEqualTo("<html><body><p>正文</p>" + SCRIPT_TAG + CONTEXT_SCRIPT_TAG + "</body></html>");
    }

    @Test
    void draftBridgeStaysBodyOnlyEvenWithHead() throws Exception {
        // 桥脚本依赖已就绪的 DOM（defer、</body> 前）：有 <head> 无 </body> 的响应不注入桥，
        // 上下文脚本仍落 <head>（不依赖 DOM，横幅自行推迟到 DOMContentLoaded）
        authenticate();
        String html = "<html><head></head><p>无 body 闭合</p>";
        String out = run(previewRequest(), htmlChain(html));
        assertThat(out).isEqualTo("<html><head>" + CONTEXT_SCRIPT_TAG + "</head><p>无 body 闭合</p>");
    }

    @Test
    void contextScriptInjectionHonorsContextPath() throws Exception {
        authenticate();
        MockHttpServletRequest request = themePreviewRequest();
        request.setContextPath("/site");
        String out = run(request, htmlChain(HTML));
        assertThat(out).contains("<head><script src=\"/site/_ujcms/preview-context.js?v=");
    }

    @Test
    void anonymousThemePreviewIsNotInjected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setParameter("_theme", "candidate");
        String out = run(request, htmlChain(HTML));
        assertThat(out).isEqualTo(HTML);
    }

    @Test
    void blankThemeParamIsSkippedEntirely() throws Exception {
        authenticate();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setParameter("_theme", " ");
        String out = run(request, htmlChain(HTML));
        assertThat(out).isEqualTo(HTML);
    }

    @Test
    void anonymousDraftPreviewIsNotInjected() throws Exception {
        // 令牌无效 → PreviewAuthFilter 未置身份 → 无草稿可见，不注入
        String out = run(previewRequest(), htmlChain(HTML));
        assertThat(out).isEqualTo(HTML);
    }

    @Test
    void nonPreviewRequestIsSkippedEntirely() throws Exception {
        authenticate();
        String out = run(new MockHttpServletRequest("GET", "/"), htmlChain(HTML));
        assertThat(out).isEqualTo(HTML);
    }

    @Test
    void postPreviewRequestIsSkipped() throws Exception {
        authenticate();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
        request.setParameter("_preview", "draft");
        String out = run(request, htmlChain(HTML));
        assertThat(out).isEqualTo(HTML);
    }

    @Test
    void nonHtmlResponseIsNotInjected() throws Exception {
        authenticate();
        String xml = "<rss><body>x</body></rss>";
        String out = run(previewRequest(), htmlChain(xml, "application/xml;charset=UTF-8", HttpServletResponse.SC_OK));
        assertThat(out).isEqualTo(xml);
    }

    @Test
    void non200ResponseIsNotInjected() throws Exception {
        authenticate();
        String out = run(previewRequest(), htmlChain(HTML, "text/html;charset=UTF-8", HttpServletResponse.SC_NOT_FOUND));
        assertThat(out).isEqualTo(HTML);
    }

    @Test
    void htmlWithoutBodyCloseIsLeftAlone() throws Exception {
        authenticate();
        String fragment = "<div>片段</div>";
        String out = run(previewRequest(), htmlChain(fragment));
        assertThat(out).isEqualTo(fragment);
    }

    @Test
    void injectsBeforeLastBodyCloseCaseInsensitive() throws Exception {
        // 无 <head>：桥与上下文脚本都落最后一个 </body>（大小写不敏感）前
        authenticate();
        String html = "<html><body><pre></body></pre><p>尾</p></BODY></html>";
        String out = run(previewRequest(), htmlChain(html));
        assertThat(out).isEqualTo("<html><body><pre></body></pre><p>尾</p>" + SCRIPT_TAG + CONTEXT_SCRIPT_TAG + "</BODY></html>");
    }

    @Test
    void contentLengthMatchesInjectedBody() throws Exception {
        authenticate();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(previewRequest(), response, htmlChain(HTML));
        assertThat(response.getContentLength())
                .isEqualTo(response.getContentAsString(StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void passThroughChainStillWorks() throws Exception {
        // MockFilterChain 空链：无内容写入也不抛错
        authenticate();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(previewRequest(), response, new MockFilterChain());
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
