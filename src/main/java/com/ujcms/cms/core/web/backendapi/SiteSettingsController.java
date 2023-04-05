package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.SiteCustom;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import org.owasp.html.PolicyFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

import java.util.List;
import java.util.Map;

/**
 * 站点设置 Controller
 *
 * @author PONY
 */
@RestController("backendSiteSettingsController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/site-settings")
public class SiteSettingsController {
    private final PolicyFactory policyFactory;
    private final SiteService service;

    public SiteSettingsController(PolicyFactory policyFactory, SiteService service) {
        this.policyFactory = policyFactory;
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
        service.update(site, null);
        return Responses.ok();
    }

    @PutMapping("watermark")
    @PreAuthorize("hasAnyAuthority('siteSettings:watermark:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateWatermark", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateWatermark(@RequestBody @Valid Site.Watermark bean) {
        Site site = Contexts.getCurrentSite();
        site.setWatermark(bean);
        service.update(site, null);
        return Responses.ok();
    }

    @PutMapping("message-board")
    @PreAuthorize("hasAnyAuthority('siteSettings:messageBoard:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateMessageBoard", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateMessageBoard(@RequestBody @Valid Site.MessageBoard bean) {
        Site site = Contexts.getCurrentSite();
        site.setMessageBoard(bean);
        service.update(site, null);
        return Responses.ok();
    }

    @PutMapping("customs")
    @PreAuthorize("hasAnyAuthority('siteSettings:customs:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateCustoms", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateCustoms(@RequestBody Map<String, Object> customs) {
        Site site = Contexts.getCurrentSite();
        site.getModel().sanitizeCustoms(customs, policyFactory);
        List<SiteCustom> customList = site.disassembleCustoms(customs);
        service.update(site, customList);
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
        service.update(site, null);
        return Responses.ok();
    }
}