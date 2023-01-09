package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.SiteResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 前台首页 Controller
 *
 * @author PONY
 */
@Controller("frontendHomepageController")
public class HomepageController {
    private final SiteResolver siteResolver;

    public HomepageController(SiteResolver siteResolver) {
        this.siteResolver = siteResolver;
    }

    @GetMapping({"/", "/{subDir:[\\w-]+}"})
    public String home(@PathVariable(required = false) String subDir,
                       HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        modelMap.put(Frontends.PAGE_SIZE, site.getPageSize());
        modelMap.put("isHome", true);
        return site.getTemplate();
    }
}
