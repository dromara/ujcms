package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.ext.component.GeoFileService;
import com.ujcms.cms.ext.component.SearchEnginePushService;
import com.ujcms.cms.ext.component.SearchEnginePushService.PushResult;
import com.ujcms.common.web.exception.Http400Exception;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.ext.component.SearchEnginePushService.ENGINE_BAIDU;
import static com.ujcms.cms.ext.component.SearchEnginePushService.ENGINE_INDEX_NOW;

/**
 * 后台 GEO 辅助接口（站点设置 → GEO设置 页面使用）：robots.txt 预览、搜索引擎推送状态与推送测试。
 * 设置本身仍由核心层 {@code SiteSettingsController#updateGeo} 保存；这里只是 ext 层能力（文件生成、推送）的后台入口
 *
 * @author PONY
 */
@RestController("backendGeoController")
@RequestMapping(BACKEND_API + "/ext/geo")
public class GeoController {
    private final GeoFileService geoFileService;
    private final SearchEnginePushService pushService;

    public GeoController(GeoFileService geoFileService, SearchEnginePushService pushService) {
        this.geoFileService = geoFileService;
        this.pushService = pushService;
    }

    /**
     * 用表单当前值（未保存）生成 robots.txt，与前台实际响应同一实现——控制台预览不再复制生成规则
     */
    @PostMapping("robots-preview")
    @PreAuthorize("hasAnyAuthority('siteSettings:geo:update','*')")
    public PreviewResult robotsPreview(@RequestBody Site.Geo bean) {
        Site site = Contexts.getCurrentSite();
        return new PreviewResult(bean.isRobotsEnabled() ? geoFileService.robotsTxt(site, bean) : "");
    }

    @GetMapping("push-status")
    @PreAuthorize("hasAnyAuthority('siteSettings:geo:update','*')")
    public PushStatusResult pushStatus() {
        Long siteId = Contexts.getCurrentSiteId();
        return new PushStatusResult(pushService.lastResult(siteId, ENGINE_BAIDU),
                pushService.lastResult(siteId, ENGINE_INDEX_NOW));
    }

    /**
     * 推送测试：按已保存的站点设置即时提交站点首页。百度测试同样消耗一条当日配额
     */
    @PostMapping("push-test")
    @PreAuthorize("hasAnyAuthority('siteSettings:geo:update','*')")
    @OperationLog(module = "siteSettings", operation = "pushTest", type = OperationType.OTHER)
    public PushResult pushTest(@RequestParam String engine) {
        Site site = Contexts.getCurrentSite();
        Site.Geo geo = site.getGeo();
        boolean configured = switch (engine) {
            case ENGINE_BAIDU -> geo.isBaiduPushEnabled() && StringUtils.isNotBlank(geo.getBaiduPushUrl());
            case ENGINE_INDEX_NOW -> geo.isIndexNowEnabled() && StringUtils.isNotBlank(geo.getIndexNowKey());
            default -> throw new Http400Exception("Unknown search engine: " + engine);
        };
        if (!configured) {
            throw new Http400Exception("Search engine push not enabled or not configured (save first): " + engine);
        }
        return pushService.pushTest(site, engine);
    }

    public record PreviewResult(String body) {
    }

    public record PushStatusResult(@Nullable PushResult baidu, @Nullable PushResult indexNow) {
    }
}
