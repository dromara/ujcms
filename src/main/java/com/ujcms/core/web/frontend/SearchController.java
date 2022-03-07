package com.ujcms.core.web.frontend;

import com.ujcms.core.domain.Site;
import com.ujcms.core.web.support.SiteResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;

/**
 * 前台搜索 Controller
 *
 * @author PONY
 */
@Controller("frontendSearchController")
public class SearchController {
    private SiteResolver siteResolver;

    public SearchController(SiteResolver siteResolver) {
        this.siteResolver = siteResolver;
    }

    private static final String TEMPLATE = "sys_search";

    @GetMapping({"/search", "/{subDir:[\\w-]+}/search"})
    public String channel(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(TEMPLATE);
    }
}
