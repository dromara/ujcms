package com.ujcms.cms.core.composition;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.composition.CompositionHealthReport.DanglingPanelRef;
import com.ujcms.cms.core.composition.CompositionHealthReport.EmptyRequiredRegion;
import com.ujcms.cms.core.composition.CompositionHealthReport.LiteralSeedToken;
import com.ujcms.cms.core.composition.CompositionHealthReport.OrphanPanel;
import com.ujcms.cms.core.composition.CompositionHealthReport.OrphanPlacement;
import com.ujcms.cms.core.composition.CompositionHealthReport.RegistryStatus;
import com.ujcms.cms.core.composition.CompositionHealthReport.ReloadEntry;
import com.ujcms.cms.core.composition.CompositionHealthReport.RequiredSettingMissing;
import com.ujcms.cms.core.composition.CompositionHealthReport.StalePanelRef;
import com.ujcms.cms.core.composition.CompositionHealthReport.UnresolvableComponent;
import com.ujcms.cms.core.composition.SettingsResolver.StaleRef;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import com.ujcms.cms.core.domain.PageRegion;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.domain.RegistryReload;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.RegistryReloadMapper;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 页面构成健康服务（§11.5）。汇总注册表状态与各类健康发现成 {@link CompositionHealthReport}；
 * 全部计算所得、从不持久化（派生状态不写回授权数据）。面板设置的失效引用检查即
 * {@link SettingsResolver#staleRefs}（component-settings Phase D：取代卡片引用反向索引；
 * theme-marketplace D24 起与种子导入的修复判定同一口径）——设置值已设但引擎值解析为空，
 * 即目标已删除/越站。
 *
 * @author PONY
 */
@Service
public class CompositionHealthService {
    /**
     * 近期重载审计展示条数
     */
    private static final int RECENT_RELOADS = 20;

    private final ManifestService manifestService;
    private final PlacementService placementService;
    private final SettingsResolver settingsResolver;
    private final RegistryReloadMapper registryReloadMapper;
    private final PageRegionMapper pageRegionMapper;

    public CompositionHealthService(ManifestService manifestService, PlacementService placementService,
                                    SettingsResolver settingsResolver,
                                    RegistryReloadMapper registryReloadMapper, PageRegionMapper pageRegionMapper) {
        this.manifestService = manifestService;
        this.placementService = placementService;
        this.settingsResolver = settingsResolver;
        this.registryReloadMapper = registryReloadMapper;
        this.pageRegionMapper = pageRegionMapper;
    }

    /**
     * 计算站点的健康报告。
     *
     * @param siteId          站点ID
     * @param activeThemeKeys 站点的活动主题命名空间（桌面 + 移动，规范化）
     * @return 健康报告
     */
    public CompositionHealthReport report(Long siteId, List<String> activeThemeKeys) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        List<Placement> placements = placementService.listBySiteId(siteId);
        List<PageRegion> regions = pageRegionMapper.listBySiteId(siteId);
        Set<String> declaredSlots = declaredSlots(snapshot, activeThemeKeys);
        List<StalePanelRef> stalePanelRefs = new ArrayList<>();
        List<UnresolvableComponent> unresolvable = new ArrayList<>();
        scanPanels(siteId, placements, snapshot, activeThemeKeys, stalePanelRefs, unresolvable);
        return new CompositionHealthReport(
                registryStatus(snapshot, activeThemeKeys),
                stalePanelRefs,
                literalSeedTokens(placements, snapshot, activeThemeKeys),
                unresolvable,
                orphanedPlacements(placements, declaredSlots, false),
                orphanedPlacements(placements, declaredSlots, true),
                orphanPanels(placements, regions),
                danglingPanelRefs(regions, placements),
                emptyRequiredRegions(snapshot, activeThemeKeys, siteId),
                requiredSettingsMissing(regions, placements, snapshot, activeThemeKeys));
    }

    /**
     * 注册表状态：全局计数之外附当前站点口径——组件为活动主题声明的并集（同名计一次），
     * 页面清单为活动主题声明（按页面名去重），与 组件库 / 页面 视图的站点视角一致。
     */
    private RegistryStatus registryStatus(RegistrySnapshot snapshot, List<String> activeThemeKeys) {
        Set<String> shapeNames = new LinkedHashSet<>();
        Set<String> pageNames = new LinkedHashSet<>();
        for (String themeKey : activeThemeKeys) {
            shapeNames.addAll(snapshot.getThemeShapes().getOrDefault(themeKey, Map.of()).keySet());
            pageNames.addAll(snapshot.getThemeManifests().getOrDefault(themeKey, Map.of()).keySet());
        }
        return new RegistryStatus(snapshot.getContentHash(), snapshot.getLoadedAt(),
                snapshot.getShapeCount(), snapshot.getManifestCount(),
                shapeNames.size(), pageNames.size(), recentReloads());
    }

    /**
     * 近期重载审计（倒序，限 {@link #RECENT_RELOADS} 条）。审计查询失败不应使健康报告失败——
     * 它只是一项展示性附注，异常时降级为空列表。
     */
    private List<ReloadEntry> recentReloads() {
        try {
            List<RegistryReload> rows = PageMethod.offsetPage(0, RECENT_RELOADS, false)
                    .doSelectPage(registryReloadMapper::selectAllOrderByLoadedAtDesc);
            return rows.stream()
                    .map(r -> new ReloadEntry(r.getSource(), r.getTriggeredBy(), r.getResult(),
                            r.getShapesCount(), r.getManifestsCount(), r.getDiffSummaryJson(),
                            r.getErrorDetail(), r.getLoadedAt()))
                    .toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * 扫描全部在用面板（component-settings Phase D）：
     * <ul>
     *   <li><b>失效引用</b>——{@link SettingsResolver#staleRefs}：组件声明的引用型设置有效值
     *       （含缺省值）非空、但经类型化边界解析后引擎值为空（目标已删除/越站）；</li>
     *   <li><b>组件不可解析</b>——{@code panel_shape_} 在命名空间链
     *       （活动主题）解析不到 Shape，渲染为空占位。</li>
     * </ul>
     */
    private void scanPanels(Long siteId, List<Placement> placements, RegistrySnapshot snapshot,
                            List<String> themeKeys, List<StalePanelRef> stalePanelRefs,
                            List<UnresolvableComponent> unresolvable) {
        for (Placement placement : placements) {
            String component = placement.getPanelShape();
            if (component == null || placement.isArchived()) {
                continue;
            }
            Shape shape = resolveShape(snapshot, themeKeys, component);
            if (shape == null) {
                unresolvable.add(new UnresolvableComponent(String.valueOf(placement.getId()),
                        placement.getPageRef(), placement.getSlotName(), component));
                continue;
            }
            for (StaleRef ref : settingsResolver.staleRefs(shape, placement.getSettings(), siteId)) {
                stalePanelRefs.add(new StalePanelRef(String.valueOf(placement.getId()), placement.getPageRef(),
                        placement.getSlotName(), component, ref.setting(), ref.kind(), ref.targetRef(),
                        placement.getOrigin()));
            }
        }
    }

    /**
     * 未展开的种子引用遗留（theme-seed-export R1）：两种词形——
     * <ul>
     *   <li><b>令牌字面量</b>（D9 之前导入）：字段无关的递归清点（{@link SeedTokenExpander}
     *       与导入/修复共用同一遍历口径）——导入对面板设置的一切字符串无条件展开令牌，
     *       残留字面量必为遗留数据；</li>
     *   <li><b>别名映射</b>（令牌文法之前的手写种子形，D20）：字段定义驱动清点
     *       （channelRef/list/object 分派）——组件不可解析时无从判定字段类型，该形跳过。</li>
     * </ul>
     * 与面板扫描同范围（在用面板：{@code panel_shape_} 非空且未归档）。
     */
    private List<LiteralSeedToken> literalSeedTokens(List<Placement> placements, RegistrySnapshot snapshot,
                                                     List<String> themeKeys) {
        List<LiteralSeedToken> result = new ArrayList<>();
        for (Placement placement : placements) {
            if (placement.getPanelShape() == null || placement.isArchived()) {
                continue;
            }
            Map<String, Object> settings = placement.getSettings();
            BiConsumer<String, String> collect = (path, token) ->
                    result.add(new LiteralSeedToken(String.valueOf(placement.getId()), placement.getPageRef(),
                            placement.getSlotName(), placement.getPanelShape(),
                            path, token));
            SeedTokenExpander.scanTokens(settings, "", collect);
            Shape shape = resolveShape(snapshot, themeKeys, placement.getPanelShape());
            if (shape != null) {
                SeedTokenExpander.scanAliasRefs(settings, shape.getSettings(), collect);
            }
        }
        return result;
    }

    /**
     * 孤立投放（{@code archived=false}：在用但页面/区域不再被声明）/ 归档投放（{@code archived=true}：可恢复）。
     */
    private List<OrphanPlacement> orphanedPlacements(List<Placement> placements, Set<String> declaredSlots,
                                                     boolean archived) {
        List<OrphanPlacement> result = new ArrayList<>();
        for (Placement placement : placements) {
            if (archived) {
                if (!placement.isArchived()) {
                    continue;
                }
            } else {
                if (placement.isArchived()
                        || declaredSlots.contains(placement.getPageRef() + "/" + placement.getSlotName())) {
                    continue;
                }
            }
            result.add(new OrphanPlacement(String.valueOf(placement.getId()), placement.getPageRef(),
                    placement.getSlotName(), placement.getPanelShape(), placement.getSettingsTitle()));
        }
        return result;
    }

    // ---------------------------------------------------------------------------------------------
    // 区域健康（page-regions §11）。结构树在每次保存时强校验（引用 ⊆ 区域面板、列数匹配、目录键合法），
    // 故下列发现仅会因带外删除（直接删投放）产生——提示性而非致命。
    // ---------------------------------------------------------------------------------------------

    /**
     * 孤立面板：区域面板投放（{@code panel_shape_} 非空），但其ID既不被该区域已发布树也不被草稿树引用。
     */
    private List<OrphanPanel> orphanPanels(List<Placement> placements, List<PageRegion> regions) {
        Map<String, Set<String>> referenced = new LinkedHashMap<>();
        for (PageRegion region : regions) {
            Set<String> ids = new LinkedHashSet<>(RegionLayout.parse(region.getLayoutJson()).panelIds());
            if (region.isDraftPresent()) {
                ids.addAll(RegionLayout.parse(region.getDraftLayoutJson()).panelIds());
            }
            referenced.put(regionKey(region.getPageRef(), region.getRegionName()), ids);
        }
        List<OrphanPanel> result = new ArrayList<>();
        for (Placement placement : placements) {
            if (placement.getPanelShape() == null) {
                continue;
            }
            Set<String> ids = referenced.get(
                    regionKey(placement.getPageRef(), placement.getSlotName()));
            if (ids == null || !ids.contains(String.valueOf(placement.getId()))) {
                result.add(new OrphanPanel(String.valueOf(placement.getId()), placement.getPageRef(),
                        placement.getSlotName()));
            }
        }
        return result;
    }

    /**
     * 悬空面板引用：结构树（已发布或草稿）引用的面板ID没有对应的存活面板投放。
     */
    private List<DanglingPanelRef> danglingPanelRefs(List<PageRegion> regions, List<Placement> placements) {
        Set<String> livePanelIds = new LinkedHashSet<>();
        for (Placement placement : placements) {
            if (placement.getPanelShape() != null) {
                livePanelIds.add(String.valueOf(placement.getId()));
            }
        }
        List<DanglingPanelRef> result = new ArrayList<>();
        for (PageRegion region : regions) {
            Set<String> refs = new LinkedHashSet<>(RegionLayout.parse(region.getLayoutJson()).panelIds());
            if (region.isDraftPresent()) {
                refs.addAll(RegionLayout.parse(region.getDraftLayoutJson()).panelIds());
            }
            for (String ref : refs) {
                if (!livePanelIds.contains(ref)) {
                    result.add(new DanglingPanelRef(region.getPageRef(), region.getRegionName(), ref));
                }
            }
        }
        return result;
    }

    /**
     * 空的已声明区域（信息性）：清单声明了区域但已发布树无行。
     */
    private List<EmptyRequiredRegion> emptyRequiredRegions(RegistrySnapshot snapshot, List<String> themeKeys,
                                                           Long siteId) {
        Map<String, EmptyRequiredRegion> merged = new LinkedHashMap<>();
        for (String themeKey : themeKeys) {
            snapshot.getThemeManifests().getOrDefault(themeKey, Map.of()).values().forEach(manifest ->
                    manifest.getSlots().forEach((regionName, def) ->
                            addEmptyRegion(merged, siteId, manifest.getName(), regionName, def, themeKey)));
        }
        return new ArrayList<>(merged.values());
    }

    private void addEmptyRegion(Map<String, EmptyRequiredRegion> merged, Long siteId, String pageRef,
                                String regionName, PageManifest.SlotDef def, String themeKey) {
        String key = regionKey(pageRef, regionName);
        EmptyRequiredRegion existing = merged.get(key);
        if (existing != null) {
            List<String> themes = new ArrayList<>(existing.themes());
            themes.add(themeKey);
            merged.put(key, new EmptyRequiredRegion(pageRef, regionName, themes));
            return;
        }
        PageRegion region = pageRegionMapper.selectByUnique(siteId, pageRef, regionName,
                PageRegion.SCOPE_DEFAULT);
        if (region == null && def.getSharedFrom() != null) {
            // 共享区域未分叉（page-regions §15）：有效行是宿主页默认行
            region = pageRegionMapper.selectByUnique(siteId, def.getSharedFrom(), regionName,
                    PageRegion.SCOPE_DEFAULT);
        }
        int publishedRows = region != null ? RegionLayout.parse(region.getLayoutJson()).rows().size() : 0;
        if (publishedRows == 0) {
            merged.put(key, new EmptyRequiredRegion(pageRef, regionName,
                    new ArrayList<>(List.of(themeKey))));
        }
    }

    /**
     * 必填设置为空（design-review P1-4 / B3）：<b>已发布</b>树引用的启用面板，其组件声明
     * {@code required} 的设置值为空（{@link FieldValueValidator#isEmpty} 口径，与发布门一致；
     * 声明 default 亦不豁免）。发布门拦截草稿，此处捕获两条漏网路径：门上线前的存量发布、
     * 组件演化（主题更新为已发布面板新增必填字段）。
     */
    private List<RequiredSettingMissing> requiredSettingsMissing(List<PageRegion> regions,
                                                                 List<Placement> placements,
                                                                 RegistrySnapshot snapshot,
                                                                 List<String> themeKeys) {
        Map<Long, Placement> panelsById = new LinkedHashMap<>();
        for (Placement placement : placements) {
            if (placement.getPanelShape() != null) {
                panelsById.put(placement.getId(), placement);
            }
        }
        List<RequiredSettingMissing> result = new ArrayList<>();
        for (PageRegion region : regions) {
            for (String panelId : new LinkedHashSet<>(RegionLayout.parse(region.getLayoutJson()).panelIds())) {
                Placement panel = panelsById.get(parsePanelId(panelId));
                if (panel == null || Boolean.FALSE.equals(panel.getEnabled())) {
                    continue;
                }
                Shape shape = resolveShape(snapshot, themeKeys, panel.getPanelShape());
                if (shape == null) {
                    continue;
                }
                Map<String, Object> settings = panel.getSettings();
                shape.getSettings().forEach((name, def) -> {
                    if (def.isRequired() && FieldValueValidator.isEmpty(settings.get(name))) {
                        result.add(new RequiredSettingMissing(String.valueOf(panel.getId()), region.getPageRef(),
                                region.getRegionName(), region.getScope(),
                                panel.getPanelShape(), name));
                    }
                });
            }
        }
        return result;
    }

    @Nullable
    private static Long parsePanelId(String panelId) {
        try {
            return Long.parseLong(panelId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String regionKey(String pageRef, String regionName) {
        return pageRef + '/' + regionName;
    }

    private Set<String> declaredSlots(RegistrySnapshot snapshot, List<String> themeKeys) {
        Set<String> slots = new LinkedHashSet<>();
        for (String themeKey : themeKeys) {
            snapshot.getThemeManifests().getOrDefault(themeKey, Map.of()).values().forEach(manifest ->
                    manifest.getSlots().keySet().forEach(slot -> slots.add(manifest.getName() + "/" + slot)));
        }
        return slots;
    }

    /**
     * 在活动主题命名空间解析 Shape（任一活动主题声明即可；无命中 → {@code null}）。
     */
    @Nullable
    private static Shape resolveShape(RegistrySnapshot snapshot, List<String> themeKeys, String shapeName) {
        return snapshot.resolveTrackShape(themeKeys, shapeName);
    }
}
