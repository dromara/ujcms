package com.ujcms.core.web.frontend;

import com.ujcms.core.domain.Site;
import com.ujcms.core.web.support.SiteResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;

/**
 * App Controller
 *
 * @author PONY
 */
@Controller("frontendAppController")
public class AppController {
    private SiteResolver siteResolver;

    public AppController(SiteResolver siteResolver) {
        this.siteResolver = siteResolver;
    }

    @GetMapping({"/app/{name:[\\w-]+}", "/{subDir:[\\w-]+}/app/{name:[\\w-]+}"})
    public String app(@PathVariable String name, @PathVariable(required = false) String subDir,
                      HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate("app_" + name);
    }
}
