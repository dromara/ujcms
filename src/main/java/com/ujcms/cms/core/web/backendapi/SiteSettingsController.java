package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * 站点设置 Controller
 *
 * @author PONY
 */
@RestController("backendSiteSettingsController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/site-settings")
public class SiteSettingsController {
    private final SiteService service;

    public SiteSettingsController(SiteService service) {
        this.service = service;
    }

    @GetMapping()
    @PreAuthorize("hasAnyAuthority('siteSettings:show','*')")
    public Site show() {
        return Contexts.getCurrentSite();
    }

    @PutMapping("base")
    @PreAuthorize("hasAnyAuthority('siteSettings:base:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateBase", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateBase(@RequestBody @Valid Site bean) {
        Site site = Contexts.getCurrentSite();
        Entities.copy(bean, site, "id", "parentId", "order", "watermark", "html", "customs");
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("watermark")
    @PreAuthorize("hasAnyAuthority('siteSettings:watermark:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateWatermark", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateWatermark(@RequestBody @Valid Site.Watermark bean) {
        Site site = Contexts.getCurrentSite();
        site.setWatermark(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("message-board")
    @PreAuthorize("hasAnyAuthority('siteSettings:messageBoard:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateMessageBoard", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateMessageBoard(@RequestBody @Valid Site.MessageBoard bean) {
        Site site = Contexts.getCurrentSite();
        site.setMessageBoard(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("editor")
    @PreAuthorize("hasAnyAuthority('siteSettings:editor:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateMessageBoard", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateEditor(@RequestBody Map<String, Object> bean) {
        Site site = Contexts.getCurrentSite();
        site.setEditor(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("customs")
    @PreAuthorize("hasAnyAuthority('siteSettings:customs:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateCustoms", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateCustoms(@RequestBody Map<String, Object> customs) {
        Site site = Contexts.getCurrentSite();
        site.setCustoms(customs);
        service.update(site);
        return Responses.ok();
    }

    @GetMapping("html")
    @PreAuthorize("hasAnyAuthority('siteSettings:html:show','*')")
    public Site.Html showHtml() {
        return Contexts.getCurrentSite().getHtml();
    }

    @PutMapping("html")
    @PreAuthorize("hasAnyAuthority('siteSettings:html:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateHtml", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateHtml(@RequestBody @Valid Site.Html bean) {
        Site site = Contexts.getCurrentSite();
        site.setHtml(bean);
        service.update(site);
        return Responses.ok();
    }
}