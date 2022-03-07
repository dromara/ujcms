package com.ujcms.core.support;

import com.ofwise.util.web.PageUrlResolver;
import com.ujcms.core.domain.Site;
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
    public static final String PARAMS = "Params";
    public static final String CTX = "ctx";
    public static final String DY = "dy";
    public static final String GLOBAL = "global";
    public static final String SITE = "site";
    public static final String FILES = "files";
    public static final String PAGE = "page";
    public static final String PAGE_SIZE = "pageSize";
    public static final String URL = "url";
    public static final String API = "api";
    public static final String QUERY_STRING = "queryString";
    public static final String PAGE_URL_RESOLVER = "pageUrlResolver";

    public static final String TEMPLATE_URL = "templateUrl";

    public static void setData(HttpServletRequest request) {
        Site site = Contexts.findCurrentSite();
        if (site == null) {
            throw new IllegalStateException("Context site not found. request URI: " + request.getRequestURI());
        }
        // Freemarker 使用 `RequestParameters`(FreemarkerServlet.KEY_REQUEST_PARAMETERS) 作为KEY，太冗长，这里使用`Params`
        // org.springframework.web.servlet.view.freemarker.FreeMarkerView#buildTemplateModel
        request.setAttribute(PARAMS, new HttpRequestParametersHashModel(request));
        request.setAttribute(CTX, request.getContextPath());
        request.setAttribute(DY, site.getDy());
        request.setAttribute(GLOBAL, site.getGlobal());
        request.setAttribute(SITE, site);
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

    public static void setDate(Map<String, Object> dataModel, Site site, String url, int page,
                               @Nullable PageUrlResolver pageUrlResolver) {
        dataModel.put(FreemarkerServlet.KEY_APPLICATION, Collections.EMPTY_MAP);
        dataModel.put(FreemarkerServlet.KEY_SESSION, Collections.EMPTY_MAP);
        dataModel.put(FreemarkerServlet.KEY_REQUEST, Collections.EMPTY_MAP);
        dataModel.put(FreemarkerServlet.KEY_REQUEST_PARAMETERS, Collections.EMPTY_MAP);
        dataModel.put(PARAMS, Collections.EMPTY_MAP);
        dataModel.put(CTX, Optional.ofNullable(site.getGlobal().getContextPath()).orElse(""));
        dataModel.put(DY, site.getDy());
        dataModel.put(GLOBAL, site.getGlobal());
        dataModel.put(SITE, site);
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

    public static int getSiteId(Environment env) throws TemplateModelException {
        return getSite(env).getId();
    }


    /**
     * 工具类不需要实例化
     */
    private Frontends() {
    }
}
