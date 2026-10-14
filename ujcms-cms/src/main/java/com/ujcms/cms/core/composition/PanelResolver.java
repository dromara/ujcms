package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.sources.DataTemplateResolver;
import com.ujcms.cms.core.composition.sources.ResolvedItem;
import com.ujcms.cms.core.domain.Placement;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 面板解析器 v2（component-settings §3 / §4.2）。面板 = 组件实例：按面板的组件名解析 Shape v2，
 * 经 {@link SettingsResolver} 得到类型化设置（引擎值 + 契约值），经 {@link DataTemplateResolver}
 * 求值数据模板得到条目，最后按 item 字段结构做读取期装饰（themeColor 调色板解析、引用字段展开）
 * 并平铺为契约条目。
 *
 * <p><b>缓存</b>：{@link PanelResolveCache}（§19.1，键的插槽名分量带
 * {@code #panel:<id>} 后缀）；面板保存/删除已 {@code invalidateAll}；预览从不缓存。
 *
 * @author PONY
 */
@Component
public class PanelResolver {
    /**
     * §19.2 解析耗时指标名（标签：{@code sourceKind} 恒为 {@code settings}）。
     * 只计真实解析（缓存未命中路径）；命中近乎零成本，命中率见缓存指标（{@code cache.*}）。
     */
    public static final String METRIC_RESOLVE = "ujcms.panel.resolve.duration";
    /**
     * 解析耗时指标的来源标签值（v2 设置驱动面板）
     */
    public static final String SOURCE_TAG = "settings";

    private final ManifestService manifestService;
    private final SettingsResolver settingsResolver;
    private final DataTemplateResolver dataTemplateResolver;
    private final ThemeSettingsService themeSettingsService;
    private final RefResolver refResolver;
    private final PanelResolveCache resolveCache;
    private final MeterRegistry meterRegistry;

    public PanelResolver(ManifestService manifestService, SettingsResolver settingsResolver,
                         DataTemplateResolver dataTemplateResolver, ThemeSettingsService themeSettingsService,
                         RefResolver refResolver, PanelResolveCache resolveCache, MeterRegistry meterRegistry) {
        this.manifestService = manifestService;
        this.settingsResolver = settingsResolver;
        this.dataTemplateResolver = dataTemplateResolver;
        this.themeSettingsService = themeSettingsService;
        this.refResolver = refResolver;
        this.resolveCache = resolveCache;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 解析单个区域面板（渲染期）。
     *
     * @param placement 面板投放行（{@code panel_shape_} / {@code settings_json_} 已加载）
     * @param ctx       解析上下文（主题命名空间、预览标记、{@code $ctx.*}）
     * @return 已解析面板（组件、契约设置、条目）
     */
    public ResolvedRegion.Panel resolve(Placement placement, ResolveContext ctx) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        if (ctx.preview()) {
            return doResolve(placement, ctx, snapshot);
        }
        String key = PanelResolveCache.key(placement.getSiteId(), placement.getPageRef(),
                placement.getSlotName() + "#panel:" + placement.getId(), ctx.themeKey(),
                ctx.ctxKey(), snapshot.getContentHash());
        return resolveCache.getPanel(key, () -> doResolve(placement, ctx, snapshot));
    }

    private ResolvedRegion.Panel doResolve(Placement placement, ResolveContext ctx, RegistrySnapshot snapshot) {
        long start = System.nanoTime();
        try {
            String component = placement.getPanelShape() != null ? placement.getPanelShape() : "";
            boolean enabled = placement.getEnabled() == null || placement.getEnabled();
            String visibility = placement.getVisibility();
            Shape shape = resolveShape(snapshot, ctx, component);
            if (shape == null) {
                // 组件在当前轨道命名空间不可解析（§5.4）：空面板，渲染器按未知组件占位
                return new ResolvedRegion.Panel(String.valueOf(placement.getId()), component, "",
                        Map.of(), List.of(), enabled, visibility);
            }
            SettingsResolver.Resolved settings =
                    settingsResolver.resolve(shape, placement.getSettings(), placement.getSiteId());
            List<ResolvedItem> entries = dataTemplateResolver.resolve(shape, settings.values(),
                    placement.getSiteId(), ctx);
            // 读取期装饰（§9.2 步骤 5 的口径）：themeColor 按调色板解析；引用字段展开为摘要
            Map<String, Object> palette = themeSettingsService.paletteByThemeKey(ctx.themeKey());
            List<Map<String, Object>> items = new ArrayList<>(entries.size());
            for (ResolvedItem entry : entries) {
                Map<String, Object> data = entry.data();
                if (palette != null && !palette.isEmpty()) {
                    data = ThemeColorResolver.resolve(shape.getFields(), data, palette);
                }
                items.add(refResolver.resolve(shape.getFields(), data));
            }
            return new ResolvedRegion.Panel(String.valueOf(placement.getId()), component, shape.getVersion(),
                    settings.contract(), items, enabled, visibility);
        } finally {
            // §19.2：真实解析耗时（缓存未命中 / 预览），v2 面板以固定来源标签打点
            meterRegistry.timer(METRIC_RESOLVE, "sourceKind", SOURCE_TAG)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 按命名空间链解析组件：活动主题 ∪ core。
     */
    @Nullable
    private static Shape resolveShape(RegistrySnapshot snapshot, ResolveContext ctx, String component) {
        return snapshot.resolveShape(ctx.themeKey(), component);
    }
}
