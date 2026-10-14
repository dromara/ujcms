package com.ujcms.cms.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 预览脚本注入过滤器（visual-builder §3；design-review P2-1 扩展）。模板轨道（FreeMarker）预览响应
 * 注入所需脚本——由渲染页面的后端自身送达，结构上不可能与后端版本漂移，
 * 主题作者零协作成本（拒绝了 {@code [@previewBridge/]} 指令方案——每个主题都得记得写）：
 * <ul>
 *   <li><b>草稿预览</b>（{@code ?_preview=draft} + 有效预览令牌，即 {@link PreviewAuthFilter} 已据令牌置
 *       SecurityContext）→ {@code /_ujcms/preview-bridge.js}（协议 v2 IIFE，构建自
 *       {@code ujcms-cms/src/main/frontend/preview-bridge/src/bridge-core.ts}——模板轨道是桥的
 *       唯一消费方，ssr-reduction P3），{@code </body>} 前、{@code defer}。桥自身以「被内嵌 +
 *       {@code _pchannel} 随机数」为激活闸门（构建器 iframe）。</li>
 *   <li><b>预览上下文携带</b>（{@code ?_theme=<候选主题>} 主题预览覆盖，P2-1 / H1；或
 *       {@code ?_preview=draft} 草稿预览）→ {@code /_ujcms/preview-context.js}：固定横幅标示预览态 +
 *       同源链接导航 / GET 表单 / fetch·XHR 子请求携带 {@code _theme} / {@code _preview=draft} /
 *       {@code _ptoken}，让操作员在预览态下浏览整站而不掉出预览。草稿部分仅在<b>顶层文档</b>生效
 *       （脚本自判 {@code window.parent === window}）：被内嵌的构建器画布由桥上报宿主改写导航，
 *       脚本不插手——与桥的激活闸门互补。注入点在 {@code <head>} 起始处、<b>阻塞</b>（无
 *       {@code defer}）——主题脚本以 fetch 取回的模板片段（{@code ${dy}/app/user} 等）同样按主题 /
 *       预览态渲染，补丁必须先于主题自身任何脚本安装，否则子请求静默落回活动主题（候选主题的片段在
 *       活动主题目录缺失即 404 JSON 被塞进页面）。无 {@code <head>} 的页面退回 {@code </body>} 前注入。</li>
 * </ul>
 * 两参数可叠加（草稿 + 候选主题），脚本按各自参数携带。
 *
 * <p>装配于前台安全链 {@code AuthorizationFilter} 之后（{@code PreviewAuthFilter} 已运行，
 * 预览身份已置上下文）。非预览请求（无预览参数或非 GET）经
 * {@link #shouldNotFilter} 直接跳过——零成本；令牌无效（上下文仍匿名）不缓冲不注入，页面按
 * 既有降级逻辑正常渲染。仅当响应为 {@code 200 text/html} 且含 {@code </body>}（预览上下文脚本另可落
 * {@code <head>}）时注入——片段响应（无 {@code <head>} 亦无 {@code </body>}）原样放行；桥脚本
 * 自身再以「被内嵌 + {@code _pchannel} 随机数」为激活闸门（nonce 由控制台每次装载生成，
 * 过滤器无须感知）。
 *
 * @author PONY
 */
public class PreviewBridgeInjectionFilter extends OncePerRequestFilter {
    /** 预览桥脚本的静态资源路径（{@code ujcms-cms} classpath:/static 下，Spring Boot 默认静态映射）。 */
    public static final String BRIDGE_SCRIPT_PATH = "/_ujcms/preview-bridge.js";
    /** 预览上下文携带脚本的静态资源路径（P2-1 主题预览 + 顶层草稿预览：横幅 + 同源链接/表单/子请求携带预览参数）。 */
    public static final String PREVIEW_CONTEXT_SCRIPT_PATH = "/_ujcms/preview-context.js";

    /**
     * 脚本内容指纹（SHA-256 前 12 位 hex），拼进注入 URL 的 {@code ?v=}。脚本 URL 本身无指纹、
     * 响应又无 Cache-Control，浏览器启发式缓存会让旧脚本在升级后继续服役——注入行为与后端版本
     * 静默漂移。指纹一变 URL 即变，缓存天然失效；资源缺失时降级为无参数（行为同旧版）。
     */
    private static final String BRIDGE_SCRIPT_VERSION = computeScriptVersion(BRIDGE_SCRIPT_PATH);
    private static final String PREVIEW_CONTEXT_SCRIPT_VERSION = computeScriptVersion(PREVIEW_CONTEXT_SCRIPT_PATH);

    /** 前台预览参数名（与 {@link PreviewAuthFilter} / {@code FrontendInterceptor} 一致）。 */
    private static final String PREVIEW_PARAM = "_preview";
    private static final String PREVIEW_DRAFT = "draft";
    /** 主题预览覆盖参数名（P2-1，与 {@code Contexts.THEME_PREVIEW_PARAM} 一致）。 */
    private static final String THEME_PARAM = "_theme";
    private static final String BODY_CLOSE = "</body>";
    private static final String HEAD_OPEN = "<head";

    /**
     * 非「GET + 预览参数（{@code _preview=draft} 或 {@code _theme}）」请求完全跳过（不缓冲响应体）。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod())
                || (!isDraftPreview(request) && !isThemePreview(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 令牌无效（PreviewAuthFilter 未置身份）→ 无预览可言，不注入
        if (isAnonymous()) {
            chain.doFilter(request, response);
            return;
        }
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);
        byte[] body = wrapper.getContentAsByteArray();
        String injected = injectable(wrapper, body) ? inject(request, wrapper, body) : null;
        if (injected == null) {
            wrapper.copyBodyToResponse();
            return;
        }
        byte[] bytes = injected.getBytes(charsetOf(wrapper));
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private static boolean isDraftPreview(HttpServletRequest request) {
        return PREVIEW_DRAFT.equals(request.getParameter(PREVIEW_PARAM));
    }

    private static boolean isThemePreview(HttpServletRequest request) {
        String theme = request.getParameter(THEME_PARAM);
        return theme != null && !theme.isBlank();
    }

    private static boolean injectable(ContentCachingResponseWrapper wrapper, byte[] body) {
        if (wrapper.getStatus() != HttpServletResponse.SC_OK || body.length == 0) {
            return false;
        }
        String contentType = wrapper.getContentType();
        return contentType != null && contentType.contains(MediaType.TEXT_HTML_VALUE);
    }

    /**
     * 插入所需脚本标签：草稿预览桥在最后一个 {@code </body>}（大小写不敏感）前、{@code defer}；
     * 预览上下文脚本（草稿或主题预览都注入）在第一个 {@code <head>} 起始标签之后、阻塞执行（先于主题
     * 脚本安装 fetch/XHR 补丁），无 {@code <head>} 时退回 {@code </body>} 前。两者都无处可放的响应
     * （片段/非常规页面）不注入。
     */
    @Nullable
    private static String inject(HttpServletRequest request, ContentCachingResponseWrapper wrapper, byte[] body) {
        String html = new String(body, charsetOf(wrapper));
        int bodyClose = lastIndexOfIgnoreCase(html, BODY_CLOSE);
        int headOpenEnd = indexAfterHeadOpen(html);
        String bodyTags = "";
        String headTags = "";
        if (isDraftPreview(request) && bodyClose >= 0) {
            bodyTags = scriptTag(request, BRIDGE_SCRIPT_PATH, BRIDGE_SCRIPT_VERSION, true);
        }
        // shouldNotFilter 已保证至少一种预览参数在场：上下文脚本总是注入（草稿部分由脚本按顶层/内嵌自判）
        String tag = scriptTag(request, PREVIEW_CONTEXT_SCRIPT_PATH, PREVIEW_CONTEXT_SCRIPT_VERSION, false);
        if (headOpenEnd >= 0) {
            headTags = tag;
        } else if (bodyClose >= 0) {
            bodyTags = bodyTags + tag;
        }
        if (headTags.isEmpty() && bodyTags.isEmpty()) {
            return null;
        }
        if (headTags.isEmpty() || bodyTags.isEmpty()) {
            int at = headTags.isEmpty() ? bodyClose : headOpenEnd;
            return html.substring(0, at) + headTags + bodyTags + html.substring(at);
        }
        // 两处插入点按下标升序拼接（<head> 正常在 </body> 之前；畸形文档反序亦不越界）
        int first = Math.min(headOpenEnd, bodyClose);
        int second = Math.max(headOpenEnd, bodyClose);
        String firstTags = first == headOpenEnd ? headTags : bodyTags;
        String secondTags = first == headOpenEnd ? bodyTags : headTags;
        return html.substring(0, first) + firstTags + html.substring(first, second) + secondTags
                + html.substring(second);
    }

    private static String scriptTag(HttpServletRequest request, String path, String version, boolean defer) {
        String suffix = version.isEmpty() ? "" : "?v=" + version;
        return "<script src=\"" + request.getContextPath() + path + suffix + "\"" + (defer ? " defer" : "")
                + "></script>";
    }

    /**
     * 第一个 {@code <head>} 起始标签（大小写不敏感，允许属性：{@code <head lang="..">}）闭合 {@code >}
     * 之后的下标；无则 -1。须紧跟 {@code >} 或空白——{@code <header>} 不算。
     */
    static int indexAfterHeadOpen(String html) {
        int from = 0;
        while (true) {
            int idx = indexOfIgnoreCase(html, HEAD_OPEN, from);
            if (idx < 0) {
                return -1;
            }
            int next = idx + HEAD_OPEN.length();
            if (next < html.length() && (html.charAt(next) == '>' || Character.isWhitespace(html.charAt(next)))) {
                int close = html.indexOf('>', next);
                return close < 0 ? -1 : close + 1;
            }
            from = next;
        }
    }

    private static int indexOfIgnoreCase(String html, String needle, int from) {
        for (int i = Math.max(0, from); i <= html.length() - needle.length(); i++) {
            if (html.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    private static String computeScriptVersion(String path) {
        try (InputStream in = PreviewBridgeInjectionFilter.class.getResourceAsStream("/static" + path)) {
            if (in == null) {
                return "";
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    /**
     * 大小写不敏感的 lastIndexOf（不经 {@code toLowerCase}——个别 Unicode 字符小写化会改变
     * 字符串长度，导致注入位置偏移）。
     */
    private static int lastIndexOfIgnoreCase(String html, String needle) {
        for (int i = html.length() - needle.length(); i >= 0; i--) {
            if (html.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    private static Charset charsetOf(ContentCachingResponseWrapper wrapper) {
        try {
            String encoding = wrapper.getCharacterEncoding();
            return encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static boolean isAnonymous() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }
}
