package com.ujcms.core.support;

import com.ujcms.core.domain.Site;
import freemarker.core.Environment;
import freemarker.ext.servlet.HttpRequestParametersHashModel;
import freemarker.template.AdapterTemplateModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 前台工具类
 *
 * @see org.springframework.web.servlet.view.freemarker.FreeMarkerView#buildTemplateModel(Map, HttpServletRequest, HttpServletResponse)
 * @author PONY
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
    public static final String REQUEST_URI = "requestUri";
    public static final String QUERY_STRING = "queryString";

    public static void setData(HttpServletRequest request) {
        Site site = Contexts.findCurrentSite();
        if (site == null) {
            throw new IllegalStateException("Context site not found. request URI: " + request.getRequestURI());
        }
        // Freemarker 使用 `RequestParameters`(FreemarkerServlet.KEY_REQUEST_PARAMETERS) 作为KEY，太冗长，这里使用`Params`
        // org.springframework.web.servlet.view.freemarker.FreeMarkerView#buildTemplateModel
        request.setAttribute(PARAMS, new HttpRequestParametersHashModel(request));
        request.setAttribute(CTX, request.getContextPath());
        // TODO 获取动态地址基础路径
        request.setAttribute(DY, site.getDy());
        request.setAttribute(GLOBAL, site.getGlobal());
        request.setAttribute(SITE, site);
        request.setAttribute(FILES, site.getFilesPath());
        String pageParam = request.getParameter(PAGE);
        int page = 1;
        if (StringUtils.isNotBlank(pageParam)) {
            page = Constants.validPage(Integer.valueOf(pageParam));
        }
        request.setAttribute(PAGE, page);
        request.setAttribute(REQUEST_URI, request.getRequestURI());
        request.setAttribute(QUERY_STRING, request.getQueryString());
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
