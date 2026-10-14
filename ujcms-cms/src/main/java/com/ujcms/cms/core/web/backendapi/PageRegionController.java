package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.composition.PlacementService;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

/**
 * 页面区域 Controller（core 保留部分）。区域编辑器的全部编辑端点自 2026-09-08 起归商业版 T3
 * {@code PageRegionT3Controller}（页面可视化设计，URL 与权限码不变）；本类仅保留主题详情
 * （{@code theme:list} 页面）也要调用的面板反向查询，使社区版的主题详情“来源面板”展开不因端点缺失而报错。
 *
 * @author PONY
 */
@RestController("backendPageRegionController")
@RequestMapping(BACKEND_API + "/core/page-region")
public class PageRegionController {
    private final PlacementService placementService;

    public PageRegionController(PlacementService placementService) {
        this.placementService = placementService;
    }

    /**
     * 面板反向查询（theme-marketplace §7.2 来源可见性）：当前站点来源标记为 {@code origin} 的
     * 面板投放行（主题详情/页面目录的来源筛选片用）。主题详情为 core 页面，故 {@code theme:list} 同样放行。
     */
    @GetMapping("panel")
    @PreAuthorize("hasAnyAuthority('theme:list','pageRegion:show','*')")
    public List<PanelRow> listPanelsByOrigin(@RequestParam("origin") String origin) {
        return placementService.listBySiteId(getCurrentSiteId()).stream()
                .filter(p -> origin.equals(p.getOrigin()))
                .map(p -> new PanelRow(String.valueOf(p.getId()), p.getPageRef(), p.getSlotName(),
                        p.getPanelShape(), p.getOrigin(), p.getOriginId(), p.isArchived()))
                .toList();
    }

    /**
     * 面板反向查询行
     *
     * @param id        面板投放ID
     * @param pageRef   页面名
     * @param region    区域名（{@code slot_name_}）
     * @param component 组件名
     * @param origin    来源标记
     * @param originId  来源ID
     * @param archived  是否已归档
     */
    public record PanelRow(String id, String pageRef, String region, @Nullable String component,
                           @Nullable String origin, @Nullable String originId, boolean archived) {
    }
}
