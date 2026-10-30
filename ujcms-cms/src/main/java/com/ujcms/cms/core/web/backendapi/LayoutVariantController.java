package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.composition.LayoutVariantCatalog;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.LayoutVariantService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.exception.Http400Exception;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

/**
 * 布局变体 Controller（theme-marketplace R1 统一注册表）。按 surface 返回活动主题清单声明的
 * 启用变体目录（快照直读，无表；标签来自主题）——构建器变体切换器与模板下拉标注的选项来源；
 * 变体经 {@code template} 绑定挂在模板上，活动变体由有效模板反查。
 *
 * @author PONY
 */
@RestController("backendLayoutVariantController")
@RequestMapping(BACKEND_API + "/core/layout-variant")
public class LayoutVariantController {
    private final LayoutVariantService service;

    public LayoutVariantController(LayoutVariantService service) {
        this.service = service;
    }

    /**
     * 后端表单下拉用：按 surface 返回当前站点的启用变体（按声明顺序）。
     */
    @GetMapping("by-surface/{surface}")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public List<VariantResult> bySurface(@PathVariable String surface) {
        if (!LayoutVariantCatalog.SURFACES.contains(surface)) {
            throw new Http400Exception("unknown surface: " + surface);
        }
        Site site = Contexts.getCurrentSite();
        List<LayoutVariantCatalog.Item> items = service.listThemeBySurface(site, surface);
        List<VariantResult> result = new ArrayList<>(items.size());
        for (LayoutVariantCatalog.Item item : items) {
            if (item.isEnabled()) {
                result.add(new VariantResult(surface, item.getValue(), item.displayLabel(), item.getOrder(),
                        item.getTemplate()));
            }
        }
        return result;
    }

    /**
     * 布局变体只读视图。{@code template} 为主题变体的模板绑定（模板下拉以变体标签
     * 标注模板选项，template-track-variants Q3）。
     */
    public record VariantResult(String surface, String value, String label, int order,
                                @Nullable String template) {
    }
}
