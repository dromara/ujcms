package com.ofwise.util.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.net.HttpHeaders;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static final Pattern COMMA_PATTERN = Pattern.compile("\\s*,\\s*");
    private static final Pattern INTERNAL_PROXIES_PATTERN = Pattern.compile("10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
            "192\\.168\\.\\d{1,3}\\.\\d{1,3}|" +
            "169\\.254\\.\\d{1,3}\\.\\d{1,3}|" +
            "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.1[6-9]{1}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.2[0-9]{1}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.3[0-1]{1}\\.\\d{1,3}\\.\\d{1,3}|" +
            "0:0:0:0:0:0:0:1|::1");

    private static String[] commaDelimitedStringsToArray(@Nullable String commaDelimitedStrings) {
        return (commaDelimitedStrings == null || commaDelimitedStrings.length() == 0) ?
                new String[0] : COMMA_PATTERN.split(commaDelimitedStrings);
    }

    /**
     * 获得真实 IP 地址。在使用了反向代理时，直接用 {@link HttpServletRequest#getRemoteAddr()}无法获取客户真实的IP地址。
     */
    public static String getRemoteAddr(ServletRequest req) {
        String remoteAddr = req.getRemoteAddr();
        // 如果是内网IP，有可能使用了反向代理，从 X-Forwarded-For 的头信息里面查找真实 IP。
        if (INTERNAL_PROXIES_PATTERN.matcher(remoteAddr).matches()) {
            if (req instanceof HttpServletRequest) {
                HttpServletRequest request = (HttpServletRequest) req;
                StringBuilder proxiesIpsBuilder = new StringBuilder();
                for (Enumeration<String> e = request.getHeaders(REMOTE_IP_HEADER); e.hasMoreElements(); ) {
                    if (proxiesIpsBuilder.length() > 0) {
                        proxiesIpsBuilder.append(", ");
                    }
                    proxiesIpsBuilder.append(e.nextElement());
                }
                String[] proxiesIps = commaDelimitedStringsToArray(proxiesIpsBuilder.toString());
                // 从右往左找第一个公网IP，避免请求中恶意加入的 X-Forwarded-For 头信息
                for (int i = proxiesIps.length - 1; i >= 0; i -= 1) {
                    remoteAddr = proxiesIps[i];
                    if (!INTERNAL_PROXIES_PATTERN.matcher(remoteAddr).matches()) {
                        break;
                    }
                }
            }
        }
        return remoteAddr;
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
     * 获取相对于 Context Path 的相对路径。参考 {@link org.apache.catalina.servlets.DefaultServlet} 的实现方式。
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
        if (requestUri.length() > 0) {
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
    public static void setAttachmentHeader(HttpServletResponse response, HttpServletRequest request, String filename) {
        String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).map(String::toLowerCase).orElse("");
        String msie10 = "trident", msie = "msie";
        if (userAgent.indexOf(msie10) > 0 || userAgent.indexOf(msie) > 0) {
            try {
                filename = URLEncoder.encode(filename, StandardCharsets.UTF_8.displayName());
            } catch (UnsupportedEncodingException e) {
                // never
            }
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
        if (array == null || array.size() == 0) {
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
                try {
                    queryParams.add(URLDecoder.decode(name, "UTF-8"),
                            (value != null ? URLDecoder.decode(value, "UTF-8") : StringUtils.isNotBlank(eq) ? "" : null));
                } catch (UnsupportedEncodingException e) {
                    logger.error("never!", e);
                }
            }
        }
        return queryParams;
    }

    @Nullable
    public static String getParam(MultiValueMap<String, String> paramMap, String name) {
        return paramMap.getFirst(name);
//        return joinByComma(paramMap.get(name));
    }

    @Nullable
    public static String getParam(String queryString, String name) {
        return parseQueryString(queryString).getFirst(name);
//        return joinByComma(getParamValues(queryString, name));
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

    public static Map<String, List<String>> getParamValuesMap(String queryString, @Nullable String prefix, boolean keyWithPrefix) {
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
            value = RandomUtils.nextLong();
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
}
