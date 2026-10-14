package com.ujcms.cms.core.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ujcms.common.security.Secures;
import io.minio.ListObjectsArgs;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 常量类
 *
 * @author PONY
 */
public final class Constants {
    /**
     * 共享模板路径
     */
    public static final String TEMPLATE_SHARE = "share";

    //以下非final常量可以被配置文件覆盖

    /**
     * 模板文件后缀。
     */
    public static final String TEMPLATE_SUFFIX = ".html";
    /**
     * 模板资源路径（css、js、img等），对应主题目录 {@code assets/} 与模板变量 {@code ${assets}}。
     */
    public static final String TEMPLATE_ASSETS = "assets";
    /**
     * 默认每页条数
     */
    public static final int DEFAULT_PAGE_SIZE = 10;
    /**
     * 默认每页最大条数。oracle 中 in 的最大个数是 1000。
     * 考虑到有可能使用 lucene 检索出 ID，再到数据库中获取数据，每页最大条数为 1000 是比较合适的。
     * MinIO的listObjects最大也是1000条。{@link ListObjectsArgs.Builder#maxKeys(int)}
     */
    public static final int MAX_PAGE_SIZE = 1000;
    /**
     * 演示用户ID
     */
    public static final int DEMO_USER_ID = 10;

    public static final String IDENTITY_COOKIE_NAME = "ujcms-unique-visitor";

    /**
     * 访客识别 Cookie 有效期：1 年。原值 {@code Integer.MAX_VALUE}（约 68 年）对去重/识别而言过长，
     * 既扩大跟踪足迹也易被漏洞扫描标记。注意：Cookie 去重为尽力而为（可清除/伪造），需要强完整性时
     * 应使用投票/调查的 {@code MODE_USER}（登录）模式。
     */
    public static final int IDENTITY_COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

    public static int validPageSize(@Nullable Integer pageSize) {
        return validPageSize(pageSize, DEFAULT_PAGE_SIZE);
    }

    public static int validPageSize(@Nullable Integer pageSize, int defaultPageSize) {
        if (pageSize == null || pageSize < 1) {
            return defaultPageSize;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            return MAX_PAGE_SIZE;
        }
        return pageSize;
    }

    public static int validPage(@Nullable Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    public static int validOffset(@Nullable Integer offset) {
        if (offset == null || offset < 0) {
            return 0;
        }
        return offset;
    }

    public static int validLimit(@Nullable Integer limit) {
        if (limit == null || limit <= 0 || limit > MAX_PAGE_SIZE) {
            return MAX_PAGE_SIZE;
        }
        return limit;
    }

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .addModule(new JavaTimeModule())
            .build();

    @Nullable
    public static Long getIdentityCookie(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, IDENTITY_COOKIE_NAME);
        if (cookie != null && StringUtils.isNotBlank(cookie.getValue())) {
            String value = cookie.getValue();
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static void setIdentityCookie(long identity, HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie(IDENTITY_COOKIE_NAME, String.valueOf(identity));
        String ctx = request.getContextPath();
        if (StringUtils.isBlank(ctx)) {
            ctx = "/";
        }
        cookie.setPath(ctx);
        cookie.setMaxAge(IDENTITY_COOKIE_MAX_AGE);
        cookie.setSecure(request.isSecure());
        cookie.setHttpOnly(true);
        // 显式声明 SameSite=Lax（Servlet 6 / Tomcat 10.1+ 支持），不再依赖浏览器默认或 https-enabled 全局开关
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    public static Long retrieveIdentityCookie(HttpServletRequest request, HttpServletResponse response) {
        Long identity = getIdentityCookie(request);
        if (identity == null) {
            identity = Secures.nextLong();
            setIdentityCookie(identity, request, response);
        }
        return identity;
    }

    /**
     * 常量类不允许创建对象
     */
    private Constants() {
    }
}
