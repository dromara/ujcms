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
import com.ujcms.common.file.FilesEx;
import com.ujcms.common.security.Secures;
import com.ujcms.common.sms.SmsError;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.PathResolver;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.Servlets;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 全局设置 Controller
 *
 * @author PONY
 */
@RestController("backendGlobalSettingsController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/config")
public class GlobalSettingsController {
    private final HtmlSanitizeService sanitizeService;
    private final ModelService modelService;
    private final ShortMessageService shortMessageService;
    private final ConfigService service;
    private final Props props;

    public GlobalSettingsController(HtmlSanitizeService sanitizeService, ModelService modelService,
                                    ShortMessageService shortMessageService, ConfigService service, Props props) {
        this.sanitizeService = sanitizeService;
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
        // Entities.copy 连 null 一并拷贝：各设置块的原始列都要排除。其中凭据类（短信、邮件、存储点、AI、第三方登录）的原始列
        // 与类型化对象均不随 Config 序列化，请求体里必然为空——漏排除即“保存基础设置清空该项设置”
        Entities.copy(bean, config, "id", "uploadSettings", "securitySettings", "registerSettings", "smsSettings",
                "emailSettings", "uploadStorageSettings", "htmlStorageSettings", "templateStorageSettings",
                "packageStorageSettings", "aiSettings", "oauthSettings", "marketplaceSettings", "customsSettings");
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

    @GetMapping("sms")
    @PreAuthorize("hasAnyAuthority('config:sms:show','*')")
    public Config.Sms showSms() {
        Config config = service.getUnique();
        return maskSms(config.getSms());
    }

    @PutMapping("sms")
    @OperationLog(module = "config", operation = "updateSms", type = OperationType.UPDATE)
    @PreAuthorize("hasAnyAuthority('config:sms:update','*')")
    public ResponseEntity<Body> updateSms(@RequestBody @Valid Config.Sms bean) {
        Config config = service.getUnique();
        // 双因子认证依赖短信：开启期间关闭短信服务，所有人都将无法登录
        if (bean.getProvider() == Config.Sms.PROVIDER_NONE && config.getSecurity().isTwoFactor()) {
            throw new LogicException("error.twoFactorRequiresSms");
        }
        // 写时合并：表单留空的密钥沿用库中原值（配合 showSms 的“只写不回显”脱敏）
        mergeSmsSecrets(bean, config.getSms());
        config.setSms(bean);
        service.update(config);
        return Responses.ok();
    }

    @PostMapping("sms/send")
    @PreAuthorize("hasAnyAuthority('config:sms:update','*')")
    @OperationLog(module = "config", operation = "sendSms", type = OperationType.CREATE)
    public ResponseEntity<Body> sendSms(@RequestBody @Valid Config.Sms bean, HttpServletRequest request) {
        // 表单未重新录入密钥时，测试发送沿用库中原值
        mergeSmsSecrets(bean, service.getUnique().getSms());
        String ip = Servlets.getRemoteAddr(request, props.getIpProxyDepth());
        String testMobile = bean.getTestMobile();
        if (StringUtils.isBlank(testMobile)) {
            return Responses.badRequest("testMobile cannot be empty");
        }
        String code = Secures.randomNumeric(bean.getCodeLength());
        SmsError error = shortMessageService.sendMobileMessage(testMobile, code, bean);
        if (error != null) {
            // 后台测试发送：向管理员输出错误来源（短信平台）及其原始错误信息，便于排查配置问题
            String provider = Servlets.getMessage(request, "smsProvider." + error.provider());
            return Responses.failure(request, "error.smsPlatformError", provider, error.detail());
        }
        shortMessageService.insertMobileMessage(testMobile, code, ip, ShortMessage.USAGE_TEST);
        return Responses.ok();
    }

    @GetMapping("ai")
    @PreAuthorize("hasAnyAuthority('config:ai:show','*')")
    public Config.Ai showAi() {
        Config config = service.getUnique();
        return maskAi(config.getAi());
    }

    @PutMapping("ai")
    @OperationLog(module = "config", operation = "updateAi", type = OperationType.UPDATE)
    @PreAuthorize("hasAnyAuthority('config:ai:update','*')")
    public ResponseEntity<Body> updateAi(@RequestBody @Valid Config.Ai bean) {
        Config config = service.getUnique();
        Set<String> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Config.Ai.Provider provider : bean.getProviders()) {
            if (!codes.add(provider.getCode())) {
                throw new Http400Exception("Duplicate provider code: " + provider.getCode());
            }
            if (!names.add(provider.getName())) {
                throw new Http400Exception("Duplicate provider name: " + provider.getName());
            }
            provider.normalizeBaseUrl();
            // 附加参数必须是 JSON 对象，保存时即拒绝，避免每次调用才失败
            try {
                provider.extraParamsObject();
            } catch (IllegalArgumentException e) {
                throw new Http400Exception("Provider '" + provider.getName() + "': " + e.getMessage());
            }
            // 写时合并：表单留空的密钥沿用库中同代码服务商的原值（配合 showAi 的“只写不回显”脱敏）
            config.getAi().fillStoredApiKey(provider);
        }
        // getMt 对显式 null 兜底返回新实例：先回填字段再合并密钥，避免存入 null 丢失已存凭证
        Config.Ai.Mt mtBean = bean.getMt();
        mtBean.fillStoredSecrets(config.getAi().getMt());
        bean.setMt(mtBean);
        validateProviderCode(codes, bean.getDefaultProvider(), "defaultProvider");
        validateProviderCode(codes, bean.getWritingProvider(), "writingProvider");
        validateProviderCode(codes, bean.getModerationProvider(), "moderationProvider");
        // 翻译服务商额外接受机器翻译引擎保留代码（mt:*）
        Set<String> translationCodes = new HashSet<>(codes);
        translationCodes.addAll(Set.of(Config.Ai.MT_BAIDU, Config.Ai.MT_YOUDAO, Config.Ai.MT_DEEPL));
        validateProviderCode(translationCodes, bean.getTranslationProvider(), "translationProvider");
        config.setAi(bean);
        service.update(config);
        return Responses.ok();
    }

    private static void validateProviderCode(Set<String> codes, @Nullable String code, String name) {
        if (StringUtils.isNotBlank(code) && !codes.contains(code)) {
            throw new Http400Exception("Provider code of '" + name + "' not found: " + code);
        }
    }

    @GetMapping("email")
    @PreAuthorize("hasAnyAuthority('config:email:show','*')")
    public Config.Email showEmail() {
        Config config = service.getUnique();
        return maskEmail(config.getEmail());
    }

    @PutMapping("email")
    @PreAuthorize("hasAnyAuthority('config:email:update','*')")
    @OperationLog(module = "config", operation = "updateEmail", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateEmail(@RequestBody @Valid Config.Email bean) {
        Config config = service.getUnique();
        // 写时合并：表单留空的邮箱密码沿用库中原值（配合 showEmail 的“只写不回显”脱敏）
        mergeEmailSecrets(bean, config.getEmail());
        config.setEmail(bean);
        service.update(config);
        return Responses.ok();
    }

    @PostMapping("email/send")
    @PreAuthorize("hasAnyAuthority('config:email:update','*')")
    @OperationLog(module = "config", operation = "sendEmail", type = OperationType.CREATE)
    public ResponseEntity<Body> sendEmail(@RequestBody @Valid Config.Email bean) {
        // 表单未重新录入密码时，测试发送沿用库中原值
        mergeEmailSecrets(bean, service.getUnique().getEmail());
        String code = Secures.randomNumeric(6);
        shortMessageService.sendEmailMessage(bean.getTestTo(), code, bean);
        return Responses.ok();
    }

    @GetMapping("upload-storage")
    @PreAuthorize("hasAnyAuthority('config:uploadStorage:show','*')")
    public Config.Storage showUploadStorage() {
        Config config = service.getUnique();
        return maskStorage(config.getUploadStorage());
    }

    @PutMapping("upload-storage")
    @PreAuthorize("hasAnyAuthority('config:uploadStorage:update','*')")
    @OperationLog(module = "config", operation = "updateUploadStorage", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateUploadStorage(@RequestBody @Valid Config.Storage bean) {
        Config config = service.getUnique();
        mergeStorageSecrets(bean, config.getUploadStorage());
        config.setUploadStorage(bean);
        validateStoragePath(bean.getPath());
        service.update(config);
        return Responses.ok();
    }

    @GetMapping("html-storage")
    @PreAuthorize("hasAnyAuthority('config:htmlStorage:show','*')")
    public Config.Storage showHtmlStorage() {
        Config config = service.getUnique();
        return maskStorage(config.getHtmlStorage());
    }

    @PutMapping("html-storage")
    @PreAuthorize("hasAnyAuthority('config:htmlStorage:update','*')")
    @OperationLog(module = "config", operation = "updateHtmlStorage", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateHtmlStorage(@RequestBody @Valid Config.Storage bean) {
        Config config = service.getUnique();
        mergeStorageSecrets(bean, config.getHtmlStorage());
        config.setHtmlStorage(bean);
        validateStoragePath(bean.getPath());
        service.update(config);
        return Responses.ok();
    }

    @GetMapping("template-storage")
    @PreAuthorize("hasAnyAuthority('config:templateStorage:show','*')")
    public Config.Storage showTemplateStorage() {
        Config config = service.getUnique();
        return maskStorage(config.getTemplateStorage());
    }

    @PutMapping("template-storage")
    @PreAuthorize("hasAnyAuthority('config:templateStorage:update','*')")
    @OperationLog(module = "config", operation = "updateTemplateStorage", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateTemplateStorage(@RequestBody @Valid Config.Storage bean) {
        Config config = service.getUnique();
        mergeStorageSecrets(bean, config.getTemplateStorage());
        config.setTemplateStorage(bean);
        validateStoragePath(bean.getPath());
        service.update(config);
        return Responses.ok();
    }

    @GetMapping("package-storage")
    @PreAuthorize("hasAnyAuthority('config:packageStorage:show','*')")
    public Config.Storage showPackageStorage() {
        Config config = service.getUnique();
        return maskStorage(config.getPackageStorage());
    }

    @PutMapping("package-storage")
    @PreAuthorize("hasAnyAuthority('config:packageStorage:update','*')")
    @OperationLog(module = "config", operation = "updatePackageStorage", type = OperationType.UPDATE)
    public ResponseEntity<Body> updatePackageStorage(@RequestBody @Valid Config.Storage bean) {
        Config config = service.getUnique();
        mergeStorageSecrets(bean, config.getPackageStorage());
        config.setPackageStorage(bean);
        validateStoragePath(bean.getPath());
        service.update(config);
        return Responses.ok();
    }

    // region 凭据“只写不回显”脱敏与写时合并
    // 读取时密钥置空（不回传浏览器），保存/测试发送时若表单留空则沿用库中原值。
    // 标识类字段（accessKeyId/secretId/sdkAppId）非密钥，保留可见，便于管理员确认配置。

    private Config.Sms maskSms(Config.Sms src) {
        Config.Sms masked = new Config.Sms();
        Entities.copy(src, masked);
        masked.setAccessKeySecret("");
        masked.setSecretKey("");
        return masked;
    }

    private void mergeSmsSecrets(Config.Sms bean, Config.Sms current) {
        if (StringUtils.isBlank(bean.getAccessKeySecret())) {
            bean.setAccessKeySecret(current.getAccessKeySecret());
        }
        if (StringUtils.isBlank(bean.getSecretKey())) {
            bean.setSecretKey(current.getSecretKey());
        }
    }

    private Config.Email maskEmail(Config.Email src) {
        Config.Email masked = new Config.Email();
        Entities.copy(src, masked);
        masked.setPassword("");
        return masked;
    }

    private void mergeEmailSecrets(Config.Email bean, Config.Email current) {
        if (StringUtils.isBlank(bean.getPassword())) {
            bean.setPassword(current.getPassword());
        }
    }

    private Config.Storage maskStorage(Config.Storage src) {
        Config.Storage masked = new Config.Storage();
        Entities.copy(src, masked);
        masked.setAccessKey("");
        masked.setSecretKey("");
        masked.setPassword("");
        return masked;
    }

    private void mergeStorageSecrets(Config.Storage bean, Config.Storage current) {
        if (StringUtils.isBlank(bean.getAccessKey())) {
            bean.setAccessKey(current.getAccessKey());
        }
        if (StringUtils.isBlank(bean.getSecretKey())) {
            bean.setSecretKey(current.getSecretKey());
        }
        if (StringUtils.isBlank(bean.getPassword())) {
            bean.setPassword(current.getPassword());
        }
    }

    private Config.Ai maskAi(Config.Ai src) {
        Config.Ai masked = new Config.Ai();
        Entities.copy(src, masked);
        // Entities.copy 只拷贝简单值类型，服务商列表须逐个复制后置空密钥，避免就地修改污染源对象
        masked.setProviders(src.getProviders().stream().map(provider -> {
            Config.Ai.Provider copy = new Config.Ai.Provider();
            Entities.copy(provider, copy);
            copy.setApiKey("");
            return copy;
        }).toList());
        // 机器翻译引擎凭证同样只写不回显（ID类字段回显，便于确认所配账号）
        Config.Ai.Mt mtCopy = new Config.Ai.Mt();
        Entities.copy(src.getMt(), mtCopy);
        mtCopy.setBaiduSecret("");
        mtCopy.setYoudaoSecret("");
        mtCopy.setDeeplAuthKey("");
        masked.setMt(mtCopy);
        return masked;
    }
    // endregion

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
        config.disassembleCustoms(model,sanitizeService);
        service.update(config);
        return Responses.ok();
    }
}