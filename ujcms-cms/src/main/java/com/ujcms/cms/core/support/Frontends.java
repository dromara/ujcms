package com.ujcms.cms.core.support;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.common.web.PageUrlResolver;
import freemarker.core.Environment;
import freemarker.ext.jakarta.servlet.FreemarkerServlet;
import freemarker.ext.jakarta.servlet.HttpRequestParametersHashModel;
import freemarker.template.AdapterTemplateModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * 前台工具类
 * <p>
 * org.springframework.web.servlet.view.freemarker.FreeMarkerView#buildTemplateModel(Map, HttpServletRequest, HttpServletResponse)
 * org.springframework.web.servlet.view.AbstractView#createMergedOutputModel(Map, HttpServletRequest, HttpServletResponse)
 *
 * @author PONY
 * @see freemarker.ext.jakarta.servlet.AllHttpScopesHashModel#get(String)
 */
public class Frontends {
    public static final String USER = "user";
    public static final String PARAMS = "Params";
    public static final String CTX = "ctx";
    public static final String DY = "dy";
    public static final String DEF = "def";
    public static final String CONFIG = "config";
    public static final String SITE = "site";
    public static final String DEFAULT_SITE = "defaultSite";
    /**
     * 主题设置模型（页面构成 §8.2）：{@code theme.config.<group>.<name>}。
     * 由 {@code FrontendInterceptor} / {@code HtmlService} 经 {@code ThemeSettingsService} 注入；
     * 无设置结构的主题为 {@code {config:{}}}。
     */
    public static final String THEME = "theme";
    /** 模板资源路径变量。对应主题目录 {@code assets/}。 */
    public static final String ASSETS = "assets";
    public static final String V = "v";
    public static final String PAGE = "page";
    public static final String PAGE_SIZE = "pageSize";
    public static final String URL = "url";
    public static final String API = "api";
    public static final String QUERY_STRING = "queryString";
    public static final String PAGE_URL_RESOLVER = "pageUrlResolver";
    /**
     * 未组装模板名（{@code index}、{@code channel_video}…）。前台控制器在返回
     * {@code site.assembleTemplate(...)} 时同时写入，供插槽标签按
     * {@code (站点, 主题, 模板名)} 解析页面清单（页面构成 §10.2 渲染上下文钩子）。
     */
    public static final String TEMPLATE_NAME = "templateName";
    /**
     * 活动布局变体键（{@code <surface>:<value>}，如 {@code channel:channel_video}）。栏目/文章/专题
     * 控制器在实体布局选择命中活动主题声明的启用变体时写入（manifest 2.0 §4.1）；
     * {@code FrontendInterceptor} 据此叠加变体作用域设置进 {@link #PAGE_CONFIG}。
     */
    public static final String LAYOUT_VARIANT = "layoutVariant";
    /**
     * 页面作用域设置模型（manifest 2.0 §4.2）：{@code pageConfig.<group>.<field>}。
     * 由 {@code FrontendInterceptor} 按渲染上下文（模板名 → 页面清单 + 活动变体）经
     * {@code ThemeSettingsService.pageConfig} 解析注入；无匹配页面/变体设置时为空 Map。
     */
    public static final String PAGE_CONFIG = "pageConfig";

    public static final String TEMPLATE_URL = "templateUrl";

    /** Self-documenting alias for {@link #V} (cache-busting version string). */
    public static final String VERSION = "version";
    /**
     * Structured globals namespace (Shopify-style). Exposes the cryptic single-letter URL prefixes
     * under self-documenting names so theme authors can write {@code {{ routes.api }}/foo} instead
     * of {@code {{ api }}/foo}. The legacy short names ({@code dy}, {@code def}, {@code ctx},
     * {@code api}) stay injected for backward compat — {@code routes.*} is purely additive.
     * <p>Map shape:
     * <pre>
     *   routes.root      = {@code dy}     dynamic-URL prefix for the current site
     *   routes.api       = {@code api}    REST API prefix (e.g. {@code /api})
     *   routes.context   = {@code ctx}    servlet context path (often empty)
     *   routes.default   = {@code def}    default-site URL prefix (for cross-site links)
     * </pre>
     */
    public static final String ROUTES = "routes";

    private static String version = "";

    /**
     * 设置系统版本号。由 {@link Props#setVersion(String)} 在属性绑定时推送。
     * 用于模板中对静态资源 URL 追加 {@code ?v=${v}}，避免升级后浏览器仍加载旧版本的 JS/CSS。
     */
    public static void setVersion(String version) {
        Frontends.version = version;
    }

    public static void setUser(HttpServletRequest request, @Nullable User user) {
        request.setAttribute(USER, user);
    }

    public static void setData(HttpServletRequest request, Site defaultSite) {
        Site site = Contexts.findCurrentSite();
        if (site == null) {
            throw new IllegalStateException("Context site not found. request URI: " + request.getRequestURI());
        }
        // Freemarker 使用 `RequestParameters`(FreemarkerServlet.KEY_REQUEST_PARAMETERS) 作为KEY，太冗长，这里使用`Params`
        // org.springframework.web.servlet.view.freemarker.FreeMarkerView#buildTemplateModel
        // 由于 FreeMarker 的 HttpRequestParametersHashModel 不支持 Jakarta servlet，使用 Map 替代
        request.setAttribute(PARAMS, new HttpRequestParametersHashModel(request));
        request.setAttribute(CTX, request.getContextPath());
        request.setAttribute(DY, site.getDy());
        request.setAttribute(DEF, stripTrailingSlash(defaultSite.getDynamicUrl()));
        request.setAttribute(CONFIG, site.getConfig());
        request.setAttribute(SITE, site);
        request.setAttribute(DEFAULT_SITE, defaultSite);
        request.setAttribute(ASSETS, site.getAssetsPath());
        request.setAttribute(V, version);
        // 根据
        // freemarker.ext.jakarta.servlet.AllHttpScopesHashModel#get
        // org.springframework.web.servlet.view.AbstractView#createMergedOutputModel
        // 获取值的顺序依次为 model、pathVars、requestAttribute
        String pageParam = request.getParameter(PAGE);
        int page = 1;
        if (StringUtils.isNotBlank(pageParam)) {
            page = Constants.validPage(Integer.valueOf(pageParam));
        }
        request.setAttribute(PAGE, page);
        request.setAttribute(URL, request.getRequestURL());
        request.setAttribute(API, site.getApi());
        request.setAttribute(QUERY_STRING, request.getQueryString());
        // Self-documenting aliases (Shopify-style). Theme authors are encouraged to use these
        // for new code; the cryptic single-letter names stay for backward compat.
        request.setAttribute(VERSION, version);
        request.setAttribute(ROUTES, buildRoutes(site, defaultSite, request.getContextPath()));
    }

    public static void setDate(Map<String, Object> dataModel, Site site, Site defaultSite, String url, int page,
                               @Nullable PageUrlResolver pageUrlResolver) {
        dataModel.put(FreemarkerServlet.KEY_APPLICATION, Collections.emptyMap());
        dataModel.put(FreemarkerServlet.KEY_SESSION, Collections.emptyMap());
        dataModel.put(FreemarkerServlet.KEY_REQUEST, Collections.emptyMap());
        dataModel.put(FreemarkerServlet.KEY_REQUEST_PARAMETERS, Collections.emptyMap());
        dataModel.put(PARAMS, Collections.emptyMap());
        dataModel.put(CTX, Optional.ofNullable(site.getConfig().getContextPath()).orElse(""));
        dataModel.put(DY, site.getDy());
        dataModel.put(DEF, stripTrailingSlash(defaultSite.getDynamicUrl()));
        dataModel.put(CONFIG, site.getConfig());
        dataModel.put(SITE, site);
        dataModel.put(DEFAULT_SITE, defaultSite);
        dataModel.put(ASSETS, site.getAssetsPath());
        dataModel.put(V, version);
        dataModel.put(PAGE, page);
        dataModel.put(URL, url);
        dataModel.put(API, site.getApi());
        dataModel.put(QUERY_STRING, null);
        dataModel.put(PAGE_URL_RESOLVER, pageUrlResolver);
        dataModel.put(VERSION, version);
        String contextPath = Optional.ofNullable(site.getConfig().getContextPath()).orElse("");
        dataModel.put(ROUTES, buildRoutes(site, defaultSite, contextPath));
    }

    /**
     * Build the {@code routes} structured-globals map. Self-documenting aliases for the cryptic
     * single-letter URL prefixes ({@code dy}, {@code def}, {@code ctx}, {@code api}). Shopify
     * theme authors recognize this pattern; new themes are encouraged to use it.
     */
    static Map<String, String> buildRoutes(Site site, Site defaultSite, String contextPath) {
        Map<String, String> routes = new java.util.LinkedHashMap<>();
        routes.put("root", site.getDy());
        routes.put("api", site.getApi());
        routes.put("context", contextPath == null ? "" : contextPath);
        routes.put("default", stripTrailingSlash(defaultSite.getDynamicUrl()));
        return routes;
    }

    public static Site getSite(Environment env) throws TemplateModelException {
        TemplateModel model = env.getDataModel().get(SITE);
        if (model instanceof AdapterTemplateModel) {
            return (Site) ((AdapterTemplateModel) model).getAdaptedObject(Site.class);
        } else {
            throw new TemplateModelException("'" + SITE + "' not found in DataModel");
        }
    }

    public static Long getSiteId(Environment env) throws TemplateModelException {
        return getSite(env).getId();
    }


    private static String stripTrailingSlash(String url) {
        // `def` 作为前缀使用（如 `${def}/login`），去掉末尾 `/` 避免拼接出 `//login`
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 工具类不需要实例化
     */
    private Frontends() {
    }
}
