package com.ujcms.commons.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.net.HttpHeaders;
import com.ujcms.commons.security.Secures;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.WebUtils;

import javax.servlet.ServletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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
     * Validates if the given string is a valid IP address (IPv4 or IPv6)
     */
    private static boolean isValidIpAddress(String ip) {
        String trimmedIp = ip.trim();

        // Check for IPv4 format
        if (isIpv4Format(trimmedIp)) {
            return isValidIpv4Address(trimmedIp);
        }

        // Check for IPv6 format
        if (isIpv6Format(trimmedIp)) {
            return isValidIpv6Address(trimmedIp);
        }

        return false;
    }

    /**
     * Checks if the string appears to be in IPv4 format (contains exactly 3 dots)
     */
    private static boolean isIpv4Format(String ip) {
        return ip.chars().filter(ch -> ch == '.').count() == 3;
    }

    /**
     * Validates IPv4 address by checking each octet is a valid number (0-255)
     */
    private static boolean isValidIpv4Address(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the string appears to be in IPv6 format (contains colon)
     */
    private static boolean isIpv6Format(String ip) {
        return ip.contains(":");
    }

    /**
     * Validates IPv6 address by checking it has the right number of segments
     */
    private static boolean isValidIpv6Address(String ip) {
        String[] parts = ip.split(":");
        return parts.length >= 3 && parts.length <= 8;
    }

    /**
     * 获得真实 IP 地址。在使用了反向代理时，直接用
     * {@link HttpServletRequest#getRemoteAddr()}无法获取客户真实的IP地址。
     * 使用 depth 参数来防止恶意伪造 X-Forwarded-For 头部信息。
     *
     * @param req   ServletRequest 对象
     * @param depth 信任的代理层数，用于防止 X-Forwarded-For 头部伪造攻击
     * @return 真实的客户端IP地址
     * @see org.apache.catalina.filters.RemoteIpFilter
     */
    public static String getRemoteAddr(ServletRequest req, int depth) {
        String remoteAddress = req.getRemoteAddr();
        if (!(req instanceof HttpServletRequest) || depth < 1) {
            return remoteAddress;
        }

        HttpServletRequest request = (HttpServletRequest) req;
        // 从 X-Forwarded-For 头部获取IP列表，支持多个头部值
        String forwardedFor = getForwardedForHeader(request);

        if (StringUtils.isBlank(forwardedFor)) {
            return remoteAddress;
        }

        String[] proxyIps = commaDelimitedStringsToArray(forwardedFor);
        if (proxyIps.length == 0) {
            return remoteAddress;
        }

        // 使用深度参数来防止伪造攻击
        int trustedCount = Math.min(proxyIps.length, depth);
        // 从右往左取 trustedCount 个IP，然后从左往右遍历，返回第一个有效的IP
        int startIndex = Math.max(0, proxyIps.length - trustedCount);
        for (int i = startIndex; i < proxyIps.length; i++) {
            String ip = proxyIps[i].trim();
            if (isValidIpAddress(ip)) {
                return ip;
            }
        }

        // 如果所有代理IP都无效，返回直接连接的IP
        return remoteAddress;
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
     * 获取相对于 Context Path 的相对路径。参考
     * {@link org.apache.catalina.servlets.DefaultServlet} 的实现方式。
     * <p>
     * Return the relative path associated with this servlet.
     *
     * @param request The servlet request we are processing
     * @return the relative path
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
     * 设置禁止客户端缓存的Header。
     */
    public static void setNoCacheHeader(HttpServletResponse response) {
        // Http 1.0 header
        response.setDateHeader("Expires", 1L);
        response.addHeader("Pragma", "no-cache");
        // Http 1.1 header
        response.setHeader("Cache-Control", "no-cache, no-store, max-age=0");
    }

    /**
     * 设置让浏览器弹出下载对话框的Header.
     *
     * @param filename 下载后的文件名.
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

    /**
     * 获取用于标识是否独立访客的长整型Cookie，如果不存在或不是长整型，则生成并返回一个随机长整型Cookie，并将该Cookie设置到客户端。
     * <p>
     * 使用长整型有利于数据库以更小的存储空间保存改值。
     */
    public static Long identityCookie(String cookieName, HttpServletRequest request, HttpServletResponse response) {
        Long value = null;
        Cookie cookie = WebUtils.getCookie(request, cookieName);
        if (cookie != null && StringUtils.isNotBlank(cookie.getValue())) {
            try {
                value = Long.parseLong(cookie.getValue());
            } catch (NumberFormatException e) {
                // 不合法的cookie
            }
        }
        if (value == null) {
            value = Secures.nextLong();
            cookie = new Cookie(cookieName, value.toString());
            String ctx = request.getContextPath();
            if (StringUtils.isBlank(ctx)) {
                ctx = "/";
            }
            cookie.setPath(ctx);
            cookie.setMaxAge(Integer.MAX_VALUE);
            cookie.setHttpOnly(true);
            cookie.setSecure(request.isSecure());
            response.addCookie(cookie);
        }
        return value;
    }

    private Servlets() {
        throw new IllegalStateException("Utility class");
    }
}
