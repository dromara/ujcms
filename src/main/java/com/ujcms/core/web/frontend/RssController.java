package com.ujcms.core.web.frontend;

import com.ujcms.core.domain.Site;
import com.ujcms.core.web.support.SiteResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 前台首页 Controller
 *
 * @author PONY
 */
@Controller("frontendRssController")
public class RssController {
    private SiteResolver siteResolver;

    public RssController(SiteResolver siteResolver) {
        this.siteResolver = siteResolver;
    }

    private static final String TEMPLATE = "sys_rss";

    @GetMapping(value = {"/rss", "/{subDir:[\\w-]+}/rss"})
    public String rss(@PathVariable(required = false) String subDir, HttpServletRequest request,
                      HttpServletResponse response) {
        response.setContentType("application/xml;charset=UTF-8");
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(TEMPLATE);
    }
}
