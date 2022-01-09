package com.ujcms.core.web.frontend;

import com.ujcms.core.domain.Site;
import com.ujcms.core.support.Frontends;
import com.ujcms.core.web.support.SiteResolver;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;

/**
 * 前台首页 Controller
 *
 * @author PONY
 */
@Controller("frontendHomeController")
public class HomeController {
    private SiteResolver siteResolver;

    public HomeController(SiteResolver siteResolver) {
        this.siteResolver = siteResolver;
    }

    @GetMapping({"/", "/{subDir:[\\w-]+}"})
    public String home(@PathVariable(required = false) String subDir, HttpServletRequest request, Model modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        modelMap.addAttribute(Frontends.PAGE_SIZE, site.getPageSize());
        modelMap.addAttribute("isHome", true);
        return site.getTemplate();
    }
}
