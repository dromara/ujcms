package com.ujcms.cms.core.support;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.commons.web.PageUrlResolver;
import freemarker.core.Environment;
import freemarker.ext.servlet.FreemarkerServlet;
import freemarker.ext.servlet.HttpRequestParametersHashModel;
import freemarker.template.AdapterTemplateModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import javax.servlet.http.HttpServletRequest;
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
 * @see freemarker.ext.servlet.AllHttpScopesHashModel#get(String)
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
    public static final String FILES = "files";
    public static final String PAGE = "page";
    public static final String PAGE_SIZE = "pageSize";
    public static final String URL = "url";
    public static final String API = "api";
    public static final String QUERY_STRING = "queryString";
    public static final String PAGE_URL_RESOLVER = "pageUrlResolver";

    public static final String TEMPLATE_URL = "templateUrl";

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
        request.setAttribute(PARAMS, new HttpRequestParametersHashModel(request));
        request.setAttribute(CTX, request.getContextPath());
        request.setAttribute(DY, site.getDy());
        request.setAttribute(DEF, defaultSite.getDynamicUrl());
        request.setAttribute(CONFIG, site.getConfig());
        request.setAttribute(SITE, site);
        request.setAttribute(DEFAULT_SITE, defaultSite);
        request.setAttribute(FILES, site.getFilesPath());
        // 根据
        // freemarker.ext.servlet.AllHttpScopesHashModel#get
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
        dataModel.put(DEF, defaultSite.getDynamicUrl());
        dataModel.put(CONFIG, site.getConfig());
        dataModel.put(SITE, site);
        dataModel.put(DEFAULT_SITE, defaultSite);
        dataModel.put(FILES, site.getFilesPath());
        dataModel.put(PAGE, page);
        dataModel.put(URL, url);
        dataModel.put(API, site.getApi());
        dataModel.put(QUERY_STRING, null);
        dataModel.put(PAGE_URL_RESOLVER, pageUrlResolver);
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


    /**
     * 工具类不需要实例化
     */
    private Frontends() {
    }
}
