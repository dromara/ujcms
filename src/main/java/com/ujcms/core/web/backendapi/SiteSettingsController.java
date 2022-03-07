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

import javax.validation.Valid;

import java.util.Map;

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

    @GetMapping()
    @RequiresPermissions("siteSettings:show")
    public Object show() {
        return Contexts.getCurrentSite();
    }

    @PutMapping("base")
    @RequiresPermissions("siteSettings:base:update")
    public ResponseEntity<Body> updateBase(@RequestBody Site bean) {
        Site site = Contexts.getCurrentSite();
        Entities.copy(bean, site, "id", "parentId", "order", "watermark", "html", "customs");
        service.update(site, bean.getCustomList());
        return Responses.ok();
    }

    @PutMapping("watermark")
    @RequiresPermissions("siteSettings:watermark:update")
    public ResponseEntity<Body> updateWatermark(@RequestBody Site.Watermark bean) {
        Site site = Contexts.getCurrentSite();
        site.setWatermark(bean);
        service.update(site, null);
        return Responses.ok();
    }

    @PutMapping("customs")
    @RequiresPermissions("siteSettings:customs:update")
    public ResponseEntity<Body> updateCustoms(@RequestBody Map<String, Object> customs) {
        Site site = Contexts.getCurrentSite();
        site.setCustoms(customs);
        service.update(site, site.getCustomList());
        return Responses.ok();
    }

    @GetMapping("html")
    @RequiresPermissions("siteSettings:html:show")
    public Object showHtml() {
        return Contexts.getCurrentSite().getHtml();
    }

    @PutMapping("html")
    @RequiresPermissions("siteSettings:html:update")
    public ResponseEntity<Body> updateHtml(@RequestBody @Valid Site.Html bean) {
        Site site = Contexts.getCurrentSite();
        site.setHtml(bean);
        service.update(site, null);
        return Responses.ok();
    }
}