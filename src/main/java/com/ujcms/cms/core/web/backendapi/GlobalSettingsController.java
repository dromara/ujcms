package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.owasp.html.PolicyFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

/**
 * 全局设置 Controller
 *
 * @author PONY
 */
@RestController("backendGlobalSettingsController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/config")
public class GlobalSettingsController {
    private PolicyFactory policyFactory;
    private ModelService modelService;
    private ConfigService service;

    public GlobalSettingsController(PolicyFactory policyFactory, ModelService modelService, ConfigService service) {
        this.policyFactory = policyFactory;
        this.modelService = modelService;
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions(Role.PERMISSION_BACKEND)
    public Config show() {
        return service.getUnique();
    }

    @GetMapping("model")
    @RequiresPermissions("config:show")
    public Model configModel() {
        return modelService.selectConfigModel();
    }

    @PutMapping("base")
    @RequiresPermissions("config:base:update")
    public ResponseEntity<Body> updateBase(@RequestBody @Valid Config bean) {
        Config config = service.getUnique();
        Entities.copy(bean, config, "id", "upload", "register", "email", "customs");
        service.update(config);
        return Responses.ok();
    }

    @PutMapping("upload")
    @RequiresPermissions("config:upload:update")
    public ResponseEntity<Body> updateUpload(@RequestBody @Valid Config.Upload bean) {
        Config config = service.getUnique();
        config.setUpload(bean);
        service.update(config);
        return Responses.ok();
    }

    @GetMapping("upload-storage")
    @RequiresPermissions("config:uploadStorage:show")
    public Config.Storage showUploadStorage() {
        Config config = service.getUnique();
        return config.getUploadStorage();
    }

    @PutMapping("upload-storage")
    @RequiresPermissions("config:uploadStorage:update")
    public ResponseEntity<Body> updateUploadStorage(@RequestBody @Valid Config.Storage bean) {
        Config config = service.getUnique();
        config.setUploadStorage(bean);
        service.update(config);
        return Responses.ok();
    }

    @GetMapping("html-storage")
    @RequiresPermissions("config:htmlStorage:show")
    public Config.Storage showHtmlStorage() {
        Config config = service.getUnique();
        return config.getHtmlStorage();
    }

    @PutMapping("html-storage")
    @RequiresPermissions("config:htmlStorage:update")
    public ResponseEntity<Body> updateHtmlStorage(@RequestBody @Valid Config.Storage bean) {
        Config config = service.getUnique();
        config.setHtmlStorage(bean);
        service.update(config);
        return Responses.ok();
    }

    @GetMapping("template-storage")
    @RequiresPermissions("config:templateStorage:show")
    public Config.Storage showTemplateStorage() {
        Config config = service.getUnique();
        return config.getTemplateStorage();
    }

    @PutMapping("template-storage")
    @RequiresPermissions("config:templateStorage:update")
    public ResponseEntity<Body> updateTemplateStorage(@RequestBody @Valid Config.Storage bean) {
        Config config = service.getUnique();
        config.setTemplateStorage(bean);
        service.update(config);
        return Responses.ok();
    }

    @PutMapping("customs")
    @RequiresPermissions("config:customs:update")
    public ResponseEntity<Body> updateCustoms(@RequestBody Map<String, Object> customs) {
        Config config = service.getUnique();
        config.getModel().sanitizeCustoms(customs, policyFactory);
        config.setCustoms(customs);
        service.update(config);
        return Responses.ok();
    }
}