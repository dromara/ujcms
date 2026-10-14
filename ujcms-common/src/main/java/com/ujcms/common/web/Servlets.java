package com.ujcms.common.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.net.HttpHeaders;
import com.ujcms.common.ip.IpAddresses;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet工具类
 *
 * @author PONY
 */
public class Servlets {
    private static final Logger logger = LoggerFactory.getLogger(Servlets.class);

    private static final String REMOTE_IP_HEADER = "X-Forwarded-For";
    private static final String REAL_IP_HEADER = "X-Real-IP";
    private static final String CLIENT_IP_HEADER = "X-Client-IP";
    /**
     * IP 字段长度（VARCHAR(45)）；规范化后的地址最长 39 字符，仅无法解析的直连地址原样返回时需要截断
     */
    private static final int MAX_IP_LENGTH = 45;

    private static String[] commaDelimitedStringsToArray(@Nullable String commaDelimitedStrings) {
        return (commaDelimitedStrings == null || commaDelimitedStrings.isEmpty()) ? new String[0]
                : StringUtils.split(commaDelimitedStrings, ',');
    }

    /**
     * 获取 X-Forwarded-For 头部信息，支持多个头部值
     */
    @Nullable
    private static String getForwardedForHeader(HttpServletRequest request) {
        // 首先尝试 X-Forwarded-For，支持多个头部值
        String forwardedFor = getHeaderValues(request, REMOTE_IP_HEADER);
        if (StringUtils.isNotBlank(forwardedFor)) {
            return forwardedFor;
        }

        // 尝试其他常见的代理头部
        forwardedFor = request.getHeader(REAL_IP_HEADER);
        if (StringUtils.isNotBlank(forwardedFor)) {
            return forwardedFor;
        }

        forwardedFor = request.getHeader(CLIENT_IP_HEADER);
        if (StringUtils.isNotBlank(forwardedFor)) {
            return forwardedFor;
        }

        return null;
    }

    /**
     * 获取头部值，支持多个头部值
     */
    private static String getHeaderValues(HttpServletRequest request, String headerName) {
        try {
            java.util.Enumeration<String> headers = request.getHeaders(headerName);
            if (headers != null && headers.hasMoreElements()) {
                StringBuilder sb = new StringBuilder();
                while (headers.hasMoreElements()) {
                    String header = headers.nextElement();
                    if (StringUtils.isNotBlank(header)) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(header);
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            // 如果获取多个头部值失败，回退到单个头部值
        }
        
        // 回退到单个头部值
        return request.getHeader(headerName);
    }

    /**
     * 获得真实 IP 地址。在使用了反向代理时，直接用
     * {@link HttpServletRequest#getRemoteAddr()}无法获取客户真实的IP地址。
     * 使用 depth 参数来防止恶意伪造 X-Forwarded-For 头部信息。
     * <p>
     * 返回值经 {@link IpAddresses#canonical} 规范化：IPv6 为 RFC 5952 小写压缩形式（{@code 0:0:0:0:0:0:0:1} 记为 {@code ::1}），
     * 请求头中的方括号、端口、{@code %网卡标识} 会被去除，IPv4 映射地址（{@code ::ffff:203.0.113.9}）按 IPv4 返回。
     * 请求头中无法解析为 IP 字面量的条目一律跳过。
     *
     * @param req   ServletRequest 对象
     * @param depth 信任的代理层数，用于防止 X-Forwarded-For 头部伪造攻击
     * @return 真实的客户端IP地址
     * @see org.apache.catalina.filters.RemoteIpFilter
     */
    public static String getRemoteAddr(ServletRequest req, int depth) {
        String remoteAddress = req.getRemoteAddr();
        if (!(req instanceof HttpServletRequest) || depth < 1) {
            return canonicalOrRaw(remoteAddress);
        }

        HttpServletRequest request = (HttpServletRequest) req;
        // 从 X-Forwarded-For 头部获取IP列表，支持多个头部值
        String forwardedFor = getForwardedForHeader(request);

        if (StringUtils.isBlank(forwardedFor)) {
            return canonicalOrRaw(remoteAddress);
        }

        String[] proxyIps = commaDelimitedStringsToArray(forwardedFor);
        if (proxyIps.length == 0) {
            return canonicalOrRaw(remoteAddress);
        }

        // 使用深度参数来防止伪造攻击
        int trustedCount = Math.min(proxyIps.length, depth);
        // 从右往左取 trustedCount 个IP，然后从左往右遍历，返回第一个有效的IP
        int startIndex = Math.max(0, proxyIps.length - trustedCount);
        for (int i = startIndex; i < proxyIps.length; i++) {
            InetAddress address = IpAddresses.parseLiteral(proxyIps[i]);
            if (address != null) {
                return IpAddresses.canonical(address);
            }
        }

        // 如果所有代理IP都无效，返回直接连接的IP
        return canonicalOrRaw(remoteAddress);
    }

    /**
     * 规范化直连地址；无法解析（如容器给出的非 IP 字符串）时原样返回，并截断到 IP 字段长度
     */
    @Nullable
    private static String canonicalOrRaw(@Nullable String remoteAddress) {
        if (remoteAddress == null) {
            return null;
        }
        String canonical = IpAddresses.canonicalize(remoteAddress);
        return canonical != null ? canonical : StringUtils.substring(remoteAddress, 0, MAX_IP_LENGTH);
    }

    public static void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url)
            throws IOException {
        DefaultRedirectStrategy redirect = new DefaultRedirectStrategy();
        redirect.sendRedirect(request, response, url);
    }

    public static void sendRedirectContextRelative(HttpServletRequest request, HttpServletResponse response, String url)
            throws IOException {
        DefaultRedirectStrategy redirect = new DefaultRedirectStrategy();
        redirect.setContextRelative(true);
        redirect.sendRedirect(request, response, url);
    }

    /**
     * 获取国际化信息
     */
    public static String getMessage(HttpServletRequest request, String code, @Nullable Object... args) {
        return Optional.ofNullable(RequestContextUtils.findWebApplicationContext(request))
                .map(context -> context.getMessage(code, args, code, RequestContextUtils.getLocale(request)))
                .orElse(code);
    }

    /**
     * 获取当前请求的URL，包括QueryString。
     */
    public static String getCurrentUrl(HttpServletRequest request) {
        UrlPathHelper urlPathHelper = new UrlPathHelper();
        String uri = urlPathHelper.getOriginatingRequestUri(request);
        String queryString = urlPathHelper.getOriginatingQueryString(request);
        if (StringUtils.isNotBlank(queryString)) {
            uri += "?" + queryString;
        }
        return uri;
    }

    /**
     * 获取相对于 Context Path 的相对路径。
     * <p>
     * 改编自 Apache Tomcat 的 DefaultServlet#getRelativePath（Apache License 2.0，见 NOTICE）。
     *
     * @param request 当前请求
     * @return 相对路径
     */
    public static String getRelativePath(HttpServletRequest request) {
        return getRelativePath(request, false);
    }

    public static String getRelativePath(HttpServletRequest request, boolean allowEmptyPath) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();

        StringBuilder result = new StringBuilder();
        if (!requestUri.isEmpty()) {
            result.append(requestUri.substring(StringUtils.length(contextPath)));
        }
        if (result.length() == 0 && !allowEmptyPath) {
            result.append('/');
        }

        return result.toString();
    }

    /**
     * 让浏览器与代理都不缓存本次响应。
     */
    public static void setNoCacheHeader(HttpServletResponse response) {
        // HTTP/1.0 客户端：Expires 与 Pragma
        response.setDateHeader("Expires", 1L);
        response.addHeader("Pragma", "no-cache");
        // HTTP/1.1 客户端：Cache-Control
        response.setHeader("Cache-Control", "no-cache, no-store, max-age=0");
    }

    /**
     * 以附件方式响应，浏览器按给定文件名保存。
     *
     * @param filename 保存时使用的文件名
     */
    public static void setAttachmentHeader(HttpServletRequest request, HttpServletResponse response, String filename) {
        String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).map(String::toLowerCase).orElse("");
        String msie10 = "trident";
        String msie = "msie";
        if (userAgent.contains(msie10) || userAgent.contains(msie)) {
            filename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        } else {
            filename = new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        }
        response.reset();
        response.setContentType("application/octet-stream");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
    }

    /**
     * 输出 HTML。并禁止客户端缓存。
     * <p>
     * contentType:text/html;charset=utf-8。
     *
     * @param response HttpServletResponse
     * @param s        需要输出的字符串
     */
    public static void writeHtml(HttpServletResponse response, String s) {
        response.setContentType("text/html;charset=utf-8");
        setNoCacheHeader(response);
        try {
            response.getWriter().write(s);
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    /**
     * 输出 JSON。并禁止客户端缓存。
     * <p>
     * contentType:text/html;charset=utf-8。
     *
     * @param response HttpServletResponse
     * @param obj      需要输出的对象
     */
    public static void writeJson(HttpServletResponse response, Object obj) {
        response.setContentType("application/json;charset=utf-8");
        setNoCacheHeader(response);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try {
            mapper.writeValue(response.getWriter(), obj);
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    /**
     * 与 {@link StringUtils#join(Object[], char)} 不同，null 和 空串 当做不存在，不参与 join
     *
     * @param array 需要 join 的数组，可以为 null
     * @return join 后的字符串。可能为 {@code null}
     */
    @Nullable
    private static String joinByComma(@Nullable final List<String> array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        StringBuilder buff = new StringBuilder();
        for (String s : array) {
            if (StringUtils.isNotEmpty(s)) {
                buff.append(s).append(',');
            }
        }
        // 去除最后一个分隔符，空串则返回 null
        if (buff.length() > 1) {
            return buff.substring(0, buff.length() - 1);
        }
        return null;
    }

    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("([^&=]+)(=?)([^&]+)?");

    /**
     * 解析查询字符串。正则与解析方式改编自 Spring Framework 的 UriComponentsBuilder#query（Copyright 2002-present
     * the original author or authors，Apache License 2.0，见 NOTICE）。
     */
    public static MultiValueMap<String, String> parseQueryString(@Nullable String queryString) {
        // 使用 org.apache.http.client.utils.URLEncodedUtils 实现
        // 使用 split 实现，性能最差。
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        if (StringUtils.isNotBlank(queryString)) {
            Matcher matcher = QUERY_PARAM_PATTERN.matcher(queryString);
            while (matcher.find()) {
                String name = matcher.group(1);
                String eq = matcher.group(2);
                String value = matcher.group(3);
                String decodeValue;
                if (value != null) {
                    decodeValue = URLDecoder.decode(value, StandardCharsets.UTF_8);
                } else {
                    decodeValue = StringUtils.isNotBlank(eq) ? "" : null;
                }
                queryParams.add(URLDecoder.decode(name, StandardCharsets.UTF_8), decodeValue);
            }
        }
        return queryParams;
    }

    @Nullable
    public static String getParam(MultiValueMap<String, String> paramMap, String name) {
        return paramMap.getFirst(name);
    }

    @Nullable
    public static String getParam(String queryString, String name) {
        return parseQueryString(queryString).getFirst(name);
    }

    public static List<String> getParamValues(String queryString, String name) {
        return parseQueryString(queryString).get(name);
    }

    public static List<String> getParamPrefix(String queryString, String prefix) {
        Map<String, List<String>> params = getParamValuesMap(queryString, prefix);
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            values.addAll(entry.getValue());
        }
        return values;
    }

    public static Map<String, String> getParamMap(String queryString, String prefix) {
        return getParamMap(queryString, prefix, false);
    }

    public static Map<String, String> getParamMap(String queryString, @Nullable String prefix, boolean keyWithPrefix) {
        Map<String, String> params = new LinkedHashMap<>();
        if (prefix == null) {
            prefix = "";
        }
        MultiValueMap<String, String> paramMap = parseQueryString(queryString);
        for (Map.Entry<String, List<String>> entry : paramMap.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                String name = keyWithPrefix ? key : key.substring(prefix.length());
                params.put(name, joinByComma(entry.getValue()));
            }
        }
        return params;
    }

    public static Map<String, List<String>> getParamValuesMap(String queryString, String prefix) {
        return getParamValuesMap(queryString, prefix, false);
    }

    public static Map<String, List<String>> getParamValuesMap(String queryString, @Nullable String prefix,
                                                              boolean keyWithPrefix) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        if (prefix == null) {
            prefix = "";
        }
        MultiValueMap<String, String> paramMap = parseQueryString(queryString);
        for (Map.Entry<String, List<String>> entry : paramMap.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                String name = keyWithPrefix ? key : key.substring(prefix.length());
                params.put(name, entry.getValue());
            }
        }
        return params;
    }

    private Servlets() {
        throw new IllegalStateException("Utility class");
    }
}
