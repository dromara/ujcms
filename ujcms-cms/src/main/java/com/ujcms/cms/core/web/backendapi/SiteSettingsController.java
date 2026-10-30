package com.ujcms.cms.core.web.backendapi;

import java.util.Objects;
import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
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
    private final ConfigService configService;

    public SiteSettingsController(SiteService service, ConfigService configService) {
        this.service = service;
        this.configService = configService;
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
        // 改换自定义设置模型时校验绑定（本站或全局的站点设置模型）；未改时不校验，存量数据照常保存
        if (!Objects.equals(bean.getModelId(), site.getModelId())) {
            service.validateModel(site.getId(), bean.getModelId());
        }
        Entities.copy(bean, site, "id", "parentId", "order", "watermark", "html", "customs");
        service.validateAddress(site, Boolean.TRUE.equals(site.getConfig().getMultiDomain()));
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

    @PutMapping("comment")
    @PreAuthorize("hasAnyAuthority('siteSettings:comment:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateComment", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateComment(@RequestBody @Valid Site.Comment bean) {
        Site site = Contexts.getCurrentSite();
        site.setComment(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("contribute")
    @PreAuthorize("hasAnyAuthority('siteSettings:contribute:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateContribute", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateContribute(@RequestBody @Valid Site.Contribute bean) {
        Site site = Contexts.getCurrentSite();
        site.setContribute(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("disclosure")
    @PreAuthorize("hasAnyAuthority('siteSettings:disclosure:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateDisclosure", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateDisclosure(@RequestBody @Valid Site.Disclosure bean) {
        Site site = Contexts.getCurrentSite();
        site.setDisclosure(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("mailbox")
    @PreAuthorize("hasAnyAuthority('siteSettings:mailbox:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateMailbox", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateMailbox(@RequestBody @Valid Site.Mailbox bean) {
        Site site = Contexts.getCurrentSite();
        site.setMailbox(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("interview")
    @PreAuthorize("hasAnyAuthority('siteSettings:interview:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateInterview", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateInterview(@RequestBody @Valid Site.Interview bean) {
        Site site = Contexts.getCurrentSite();
        site.setInterview(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("accessibility")
    @PreAuthorize("hasAnyAuthority('siteSettings:accessibility:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateAccessibility", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateAccessibility(@RequestBody @Valid Site.Accessibility bean) {
        Site site = Contexts.getCurrentSite();
        site.setAccessibility(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("permalink")
    @PreAuthorize("hasAnyAuthority('siteSettings:permalink:update','*')")
    @OperationLog(module = "siteSettings", operation = "updatePermalink", type = OperationType.UPDATE)
    public ResponseEntity<Body> updatePermalink(@RequestBody @Valid Site.Permalink bean) {
        Site site = Contexts.getCurrentSite();
        site.setPermalink(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("census")
    @PreAuthorize("hasAnyAuthority('siteSettings:census:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateCensus", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateCensus(@RequestBody @Valid Site.Census bean) {
        Site site = Contexts.getCurrentSite();
        site.setCensus(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("ai")
    @PreAuthorize("hasAnyAuthority('siteSettings:ai:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateAi", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateAi(@RequestBody @Valid Site.Ai bean) {
        if (bean.getAutoApproveBelow() >= bean.getAutoBlockAbove()) {
            throw new Http400Exception("autoApproveBelow must be less than autoBlockAbove");
        }
        Site site = Contexts.getCurrentSite();
        site.setAi(bean);
        service.update(site);
        return Responses.ok();
    }

    /**
     * 站点 AI 功能页的全局就绪状态：全局开关与各功能服务商是否可解析，仅布尔值、不含任何凭证。
     * 站点管理员通常没有 config:ai:show 权限、看不到全局设置，本接口让站点页能提示“开关不生效”的原因
     * 并给出处理去向（docs/spec/ai/ai-foundation.md §5、§7）
     */
    @GetMapping("ai/readiness")
    @PreAuthorize("hasAnyAuthority('siteSettings:ai:update','*')")
    public AiReadinessResult aiReadiness() {
        Config.Ai ai = configService.getUnique().getAi();
        return new AiReadinessResult(ai.isEnabled(), ai.resolvesProvider(null),
                ai.resolvesProvider(ai.getWritingProvider()), ai.resolvesProvider(ai.getModerationProvider()),
                ai.resolvesTranslationProvider());
    }

    /**
     * AI 全局就绪状态。服务商字段表示对应功能的服务商是否可解析（含回落到默认服务商）
     *
     * @param enabled             全局是否开启
     * @param defaultProvider     默认服务商是否可解析（未单独指定服务商的功能，如智能SEO）
     * @param writingProvider     写作服务商是否可解析（写作助手、回复拟稿）
     * @param moderationProvider  审核服务商是否可解析（AI审核）
     * @param translationProvider 翻译服务商默认路径是否可用（AI预翻译；机器翻译引擎看凭证是否完整）
     */
    public record AiReadinessResult(boolean enabled, boolean defaultProvider, boolean writingProvider,
                                    boolean moderationProvider, boolean translationProvider) {
    }

    @PutMapping("geo")
    @PreAuthorize("hasAnyAuthority('siteSettings:geo:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateGeo", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateGeo(@RequestBody @Valid Site.Geo bean) {
        Site site = Contexts.getCurrentSite();
        site.setGeo(bean);
        service.update(site);
        return Responses.ok();
    }

    @PutMapping("editor")
    @PreAuthorize("hasAnyAuthority('siteSettings:editor:update','*')")
    @OperationLog(module = "siteSettings", operation = "updateEditor", type = OperationType.UPDATE)
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