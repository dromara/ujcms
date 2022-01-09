package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Site;
import com.ujcms.core.service.SiteQueryService;
import com.ujcms.core.support.Contexts;
import com.ofwise.util.web.Entities;
import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Responses.Body;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 站点设置 Controller
 *
 * @author PONY
 */
@RestController("backendSiteSettingsController")
@RequestMapping(BACKEND_API + "/core/site-settings")
public class SiteSettingsController {
    private SiteQueryService service;

    public SiteSettingsController(SiteQueryService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("siteSettings:show")
    public Object show() {
        return Contexts.getCurrentSite();
    }

    @PutMapping
    @RequiresPermissions("siteSettings:update")
    public ResponseEntity<Body> update(@RequestBody Site bean) {
        Site site = Contexts.getCurrentSite();
        Entities.copy(bean, site, "parentId", "order");
        service.update(site, bean.getCustomList());
        return Responses.ok();
    }
}