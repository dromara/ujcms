package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.SiteResolver;
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
    private final SiteResolver siteResolver;

    public SearchController(SiteResolver siteResolver) {
        this.siteResolver = siteResolver;
    }

    private static final String TEMPLATE = "sys_search";

    @GetMapping({"/search", "/{subDir:[\\w-]+}/search"})
    public String search(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(TEMPLATE);
    }
}
