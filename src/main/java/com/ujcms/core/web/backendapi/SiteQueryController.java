package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Site;
import com.ofwise.util.web.exception.Http404Exception;
import com.ujcms.core.service.SiteQueryService;
import com.ujcms.core.support.Constants;
import com.ujcms.core.support.Contexts;
import com.ujcms.core.support.Props;
import com.ofwise.util.query.QueryUtils;
import com.ofwise.util.web.Responses;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 站点 Controller
 *
 * @author PONY
 */
@RestController("backendSiteQueryController")
@RequestMapping(BACKEND_API + "/core/site")
public class SiteQueryController {
    private SiteQueryService service;
    private ResourceLoader resourceLoader;
    private Props props;

    public SiteQueryController(SiteQueryService service, ResourceLoader resourceLoader, Props props) {
        this.service = service;
        this.resourceLoader = resourceLoader;
        this.props = props;
    }

    @GetMapping
    @RequiresPermissions("site:list")
    public Object list(Integer offset, Integer limit, HttpServletRequest request) {
        Map<String, Object> queryMap = QueryUtils.getQueryMap(request.getQueryString());
        return service.selectList(queryMap, null, offset, limit);
    }

    @GetMapping("{id}")
    @RequiresPermissions("site:show")
    public Object show(@PathVariable Integer id) {
        Site bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Site not found. ID = " + id);
        }
        return bean;
    }

    @GetMapping("/theme")
    @RequiresPermissions("site:list")
    public Object currentTheme() throws IOException {
        return getThemeList(Contexts.getCurrentSite());
    }

    @GetMapping("{id}/theme")
    @RequiresPermissions("site:list")
    public List<String> theme(@PathVariable Integer id) throws IOException {
        Site site = service.select(id);
        if (site == null) {
            throw new Http404Exception("Site not found. ID = " + id);
        }
        return getThemeList(site);
    }

    private List<String> getThemeList(Site site) throws IOException {
        List<String> themeList = new ArrayList<>();
        String globalShare = "/" + Constants.TEMPLATE_SHARE;
        appendTheme(themeList, globalShare);
        String templateBase = site.getBasePath("");
        appendTheme(themeList, templateBase);
        return themeList;
    }

    private void appendTheme(List<String> themeList, String path) throws IOException {
        File file = resourceLoader.getResource(props.getTemplatePath() + path).getFile();
        if (!file.exists()) {
            return;
        }
        File[] themeFiles = file.listFiles(File::isDirectory);
        if (themeFiles == null) {
            return;
        }
        for (File themeFile : themeFiles) {
            themeList.add(path + "/" + themeFile.getName());
        }
    }
}