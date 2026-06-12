package com.ujcms.cms.core.support;

/**
 * URL 常量
 *
 * @author PONY
 */
public class UrlConstants {
    /**
     * API地址
     */
    public static final String API = "/api";
    /**
     * 后台API地址
     */
    public static final String BACKEND_API = API + "/backend";
    /**
     * 前台API地址
     */
    public static final String FRONTEND_API = "/frontend";

    /**
     * 登录地址
     */
    public static final String LOGIN_URL = "/login";

    /**
     * 栏目URL
     */
    public static final String CHANNEL = "/channel";
    /**
     * 文章URL
     */
    public static final String ARTICLE = "/article";

    /**
     * 返回URL参数名
     */
    public static final String TARGET_URL_PARAM = "targetUrl";

    private UrlConstants() {
    }
}
