package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Global;
import com.ujcms.core.domain.Model;
import com.ujcms.core.service.GlobalService;
import com.ujcms.core.service.ModelService;
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
 * 全局设置 Controller
 *
 * @author PONY
 */
@RestController("backendGlobalController")
@RequestMapping(BACKEND_API + "/core/global-settings")
public class GlobalSettingsController {
    private ModelService modelService;
    private GlobalService service;

    public GlobalSettingsController(ModelService modelService, GlobalService service) {
        this.modelService = modelService;
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("globalSettings:show")
    public Object show() {
        return service.getUnique();
    }

    @GetMapping("model")
    @RequiresPermissions("globalSettings:show")
    public Model globalModel() {
        return modelService.selectGlobalModel();
    }

    @PutMapping("base")
    @RequiresPermissions("globalSettings:base:update")
    public ResponseEntity<Body> updateBase(@RequestBody @Valid Global bean) {
        Global global = service.getUnique();
        Entities.copy(bean, global, "id", "upload", "register", "email", "customs");
        service.update(global);
        return Responses.ok();
    }

    @PutMapping("upload")
    @RequiresPermissions("globalSettings:upload:update")
    public ResponseEntity<Body> updateUpload(@RequestBody @Valid Global.Upload bean) {
        Global global = service.getUnique();
        global.setUpload(bean);
        service.update(global);
        return Responses.ok();
    }

    @PutMapping("customs")
    @RequiresPermissions("globalSettings:customs:update")
    public ResponseEntity<Body> updateCustoms(@RequestBody Map<String, Object> customs) {
        Global global = service.getUnique();
        global.setCustoms(customs);
        service.update(global);
        return Responses.ok();
    }
}