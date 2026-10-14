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
     * 专题URL
     */
    public static final String TOPIC = "/topic";

    /**
     * 站点子目录路径变量名
     */
    public static final String SUB_DIR_VARIABLE = "subDir";
    /**
     * 站点子目录路径段正则（不含前导斜杠）。与 {@code Site.SUB_DIR_PATTERN} 的字符集一致
     */
    public static final String SUB_DIR_SEGMENT_REGEX = "[\\w-]+";
    /**
     * 站点子目录路由前缀。前台路由的子目录孪生模式由 {@code SiteRoutingHandlerMapping} 统一生成，
     * Controller 不要手写此前缀
     */
    public static final String SUB_DIR = "/{" + SUB_DIR_VARIABLE + ":" + SUB_DIR_SEGMENT_REGEX + "}";

    /**
     * 返回URL参数名
     */
    public static final String TARGET_URL_PARAM = "targetUrl";

    private UrlConstants() {
    }
}
