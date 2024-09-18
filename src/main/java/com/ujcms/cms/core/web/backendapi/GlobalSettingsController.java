package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.ModelService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.commons.file.FilesEx;
import com.ujcms.commons.security.Secures;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.PathResolver;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.exception.Http400Exception;
import org.apache.commons.lang3.StringUtils;
import org.owasp.html.PolicyFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
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
    private final PolicyFactory policyFactory;
    private final ModelService modelService;
    private final ShortMessageService shortMessageService;
    private final ConfigService service;
    private final Props props;

    public GlobalSettingsController(PolicyFactory policyFactory, ModelService modelService,
                                    ShortMessageService shortMessageService, ConfigService service, Props props) {
        this.policyFactory = policyFactory;
        this.modelService = modelService;
        this.shortMessageService = shortMessageService;
        this.service = service;
        this.props = props;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public Config show() {
        return service.getUnique();
    }

    @GetMapping("model")
    @PreAuthorize("hasAnyAuthority('config:show','*')")
    public Model configModel() {
        return modelService.selectConfigModel();
    }

    @PutMapping("base")
    @PreAuthorize("hasAnyAuthority('config:base:update','*')")
    @OperationLog(module = "config", operation = "updateBase", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateBase(@RequestBody @Valid Config bean) {
        Config config = service.getUnique();
        Entities.copy(bean, config, "id", "uploadSettings", "securitySettings", "registerSettings", "emailSettings",
                "uploadStorageSettings", "htmlStorageSettings", "templateStorageSettings", "customsSettings");
        service.update(config);
        return Responses.ok();
    }

    @PutMapping("upload")
    @PreAuthorize("hasAnyAuthority('config:upload:update','*')")
    @OperationLog(module = "config", operation = "updateUpload", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateUpload(@RequestBody @Valid Config.Upload bean) {
        Config config = service.getUnique();
        config.setUpload(bean);
        service.update(config);
        return Responses.ok();
    }

    @PutMapping("register")
    @PreAuthorize("hasAnyAuthority('config:register:update','*')")
    @OperationLog(module = "config", operation = "updateRegister", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateRegister(@RequestBody @Valid Config.Register bean) {
        Config config = service.getUnique();
        config.setRegister(bean);
        service.update(config);
        return Responses.ok();
    }

    @GetMapping("grey")
    @PreAuthorize("hasAnyAuthority('config:grey:show','*')")
    public Config.Grey showGrey() {
        Config config = service.getUnique();
        return config.getGrey();
    }

    @PutMapping("grey")
    @OperationLog(module = "config", operation = "updateGrey", type = OperationType.UPDATE)
    @PreAuthorize("hasAnyAuthority('config:grey:update','*')")
    public ResponseEntity<Body> updateSms(@RequestBody @Valid Config.Grey bean) {
        Config config = service.getUnique();
        config.setGrey(bean);
        service.update(config);
        return Responses.ok();
    }

    @GetMapping("sms")
    @PreAuthorize("hasAnyAuthority('config:sms:show','*')")
    public Config.Sms showSms() {
        Config config = service.getUnique();
        return config.getSms();
    }

    @PutMapping("sms")
    @OperationLog(module = "config", operation = "updateSms", type = OperationType.UPDATE)
    @PreAuthorize("hasAnyAuthority('config:sms:update','*')")
    public ResponseEntity<Body> updateSms(@RequestBody @Valid Config.Sms bean) {
        Config config = service.getUnique();
        config.setSms(bean);
        service.update(config);
        return Responses.ok();
    }

    @PostMapping("sms/send")
    @PreAuthorize("hasAnyAuthority('config:sms:update','*')")
    @OperationLog(module = "config", operation = "sendSms", type = OperationType.CREATE)
    public ResponseEntity<Body> sendSms(@RequestBody @Valid Config.Sms bean, HttpServletRequest request) {
        String ip = Servlets.getRemoteAddr(request);
        String testMobile = bean.getTestMobile();
        if (StringUtils.isBlank(testMobile)) {
            return Responses.badRequest("testMobile cannot be empty");
        }
        String code = Secures.randomNumeric(bean.getCodeLength());
        String error = shortMessageService.sendMobileMessage(testMobile, code, bean);
        if (error != null) {
            return Responses.failure(error);
        }
        shortMessageService.insertMobileMessage(testMobile, code, ip, ShortMessage.USAGE_TEST);
        return Responses.ok();
    }

    @GetMapping("email")
    @PreAuthorize("hasAnyAuthority('config:email:show','*')")
    public Config.Email showEmail() {
        Config config = service.getUnique();
        return config.getEmail();
    }

    @PutMapping("email")
    @PreAuthorize("hasAnyAuthority('config:email:update','*')")
    @OperationLog(module = "config", operation = "updateEmail", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateEmail(@RequestBody @Valid Config.Email bean) {
        Config config = service.getUnique();
        config.setEmail(bean);
        service.update(config);
        return Responses.ok();
    }

    @PostMapping("email/send")
    @PreAuthorize("hasAnyAuthority('config:email:update','*')")
    @OperationLog(module = "config", operation = "sendEmail", type = OperationType.CREATE)
    public ResponseEntity<Body> sendEmail(@RequestBody @Valid Config.Email bean) {
        String code = Secures.randomNumeric(6);
        shortMessageService.sendEmailMessage(bean.getTestTo(), code, bean);
        return Responses.ok();
    }

    @GetMapping("upload-storage")
    @PreAuthorize("hasAnyAuthority('config:uploadStorage:show','*')")
    public Config.Storage showUploadStorage() {
        Config config = service.getUnique();
        return config.getUploadStorage();
    }

    @PutMapping("upload-storage")
    @PreAuthorize("hasAnyAuthority('config:uploadStorage:update','*')")
    @OperationLog(module = "config", operation = "updateUploadStorage", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateUploadStorage(@RequestBody @Valid Config.Storage bean) {
        Config config = service.getUnique();
        config.setUploadStorage(bean);
        validateStoragePath(bean.getPath());
        service.update(config);
        return Responses.ok();
    }

    @GetMapping("html-storage")
    @PreAuthorize("hasAnyAuthority('config:htmlStorage:show','*')")
    public Config.Storage showHtmlStorage() {
        Config config = service.getUnique();
        return config.getHtmlStorage();
    }

    @PutMapping("html-storage")
    @PreAuthorize("hasAnyAuthority('config:htmlStorage:update','*')")
    @OperationLog(module = "config", operation = "updateHtmlStorage", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateHtmlStorage(@RequestBody @Valid Config.Storage bean) {
        Config config = service.getUnique();
        config.setHtmlStorage(bean);
        validateStoragePath(bean.getPath());
        service.update(config);
        return Responses.ok();
    }

    @GetMapping("template-storage")
    @PreAuthorize("hasAnyAuthority('config:templateStorage:show','*')")
    public Config.Storage showTemplateStorage() {
        Config config = service.getUnique();
        return config.getTemplateStorage();
    }

    @PutMapping("template-storage")
    @PreAuthorize("hasAnyAuthority('config:templateStorage:update','*')")
    @OperationLog(module = "config", operation = "updateTemplateStorage", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateTemplateStorage(@RequestBody @Valid Config.Storage bean) {
        Config config = service.getUnique();
        config.setTemplateStorage(bean);
        validateStoragePath(bean.getPath());
        service.update(config);
        return Responses.ok();
    }

    private void validateStoragePath(String path) {
        if (!storagePathAllowed(path)) {
            throw new Http400Exception("Storage path not allowed: " + path);
        }
    }

    @GetMapping("storage-path-allowed")
    public boolean storagePathAllowed(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        path = path.trim();
        if (!path.startsWith(PathResolver.FILE_PREFIX)) {
            return true;
        }
        for (String prefix : props.getStorageFilePrefixList()) {
            if (path.equals(prefix) || path.startsWith(prefix + FilesEx.SLASH)) {
                return true;
            }
        }
        return false;
    }

    @PutMapping("customs")
    @PreAuthorize("hasAnyAuthority('config:customs:update','*')")
    @OperationLog(module = "config", operation = "updateCustoms", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateCustoms(@RequestBody Map<String, Object> customs) {
        Config config = service.getUnique();
        config.setCustoms(customs);
        Model model = config.getModel();
        config.disassembleCustoms(model,policyFactory);
        service.update(config);
        return Responses.ok();
    }
}