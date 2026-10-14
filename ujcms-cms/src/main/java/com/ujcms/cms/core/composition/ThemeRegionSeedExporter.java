package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.domain.PageRegion;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.PlacementMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 主题构成种子导出器（theme-seed-export §6）——{@link ThemeSeedImporter} 的镜像。把站点的
 * <b>default 作用域</b>页面区域（D8：{@code channel:*}/{@code variant:*} 覆盖行 v1 不导出——
 * 种子文法无作用域概念）连同其面板导出为 {@code seed/regions/<page>.regions.json}：
 * <ul>
 *   <li><b>两阶段</b>：{@link #scan} 先行（解析结构树、按轨道命名空间链解析 Shape、收集
 *       channelRef/articleRef 与数据模板 {@code where.channel} 的引用ID——喂给内容侧的缺省选择，
 *       §3.2）；{@link #emit} 在内容导出填好 originId 映射后发射（反向令牌化消费映射，§6）。</li>
 *   <li><b>反向令牌化</b>：面板设置经 {@link SeedExportValueRewriter}——channelRef → 别名令牌、
 *       articleRef → originId 令牌、媒体/富文本走资源分类、全字符串主题前缀反向。</li>
 *   <li><b>面板 originId</b> 由导入侧按 {@code <page>#<region>#<rowId>#<col>#<panel>} 方案再生，
 *       种子文件不携带。</li>
 * </ul>
 * 全程只读（D2）。
 *
 * @author PONY
 */
@Component
public class ThemeRegionSeedExporter {
    private static final Logger logger = LoggerFactory.getLogger(ThemeRegionSeedExporter.class);

    private static final String SCHEMA_URL = "https://www.ujcms.com/schema/composition/seed-regions.schema.json";
    private static final String CATEGORY_PANEL = "panel";
    private static final String SETTINGS_REF_PREFIX = "$settings.";

    private final PageRegionMapper pageRegionMapper;
    private final PlacementMapper placementMapper;
    private final ManifestService manifestService;

    public ThemeRegionSeedExporter(PageRegionMapper pageRegionMapper, PlacementMapper placementMapper,
                                   ManifestService manifestService) {
        this.pageRegionMapper = pageRegionMapper;
        this.placementMapper = placementMapper;
        this.manifestService = manifestService;
    }

    /**
     * 扫描阶段：解析 default 作用域区域的已发布结构树、解析 Shape，
     * 收集面板设置与数据模板的内容引用ID（缺省选择第 2 项的输入，§3.2）。
     */
    List<RegionExport> scan(SeedExportContext ctx) {
        String themeKey = ThemeSettingsService.themeKey(ctx.siteId, ctx.themeName);
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        Map<Long, Placement> placementById = placementMapper.listBySiteId(ctx.siteId).stream()
                .collect(Collectors.toMap(Placement::getId, Function.identity(), (a, b) -> a));
        List<PageRegion> regions = pageRegionMapper.listBySiteId(ctx.siteId).stream()
                .filter(PageRegion::isDefaultScope)
                .sorted(Comparator.comparing(PageRegion::getPageRef)
                        .thenComparing(PageRegion::getRegionName))
                .toList();
        List<RegionExport> exports = new ArrayList<>(regions.size());
        for (PageRegion region : regions) {
            RegionLayout layout = RegionLayout.parse(region.getLayoutJson());
            if (layout == null || layout.rows().isEmpty()) {
                // 空区域（操作员清空/未编排）无种子意义，静默跳过
                continue;
            }
            String regionRef = region.getPageRef() + "#" + region.getRegionName();
            List<RowExport> rows = new ArrayList<>(layout.rows().size());
            for (RegionLayout.Row row : layout.rows()) {
                List<List<PanelExport>> columns = new ArrayList<>(row.columns().size());
                for (RegionLayout.Column column : row.columns()) {
                    List<PanelExport> panels = new ArrayList<>(column.panels().size());
                    for (String panelId : column.panels()) {
                        PanelExport panel = resolvePanel(ctx, snapshot, themeKey, region, placementById,
                                panelId, regionRef);
                        if (panel != null) {
                            panels.add(panel);
                            collectRefs(ctx, panel);
                        }
                    }
                    columns.add(panels);
                }
                rows.add(new RowExport(row.id(), row.layout(), columns));
            }
            exports.add(new RegionExport(region, layout, rows));
        }
        return exports;
    }

    @Nullable
    private PanelExport resolvePanel(SeedExportContext ctx, RegistrySnapshot snapshot, String themeKey,
                                     PageRegion region, Map<Long, Placement> placementById,
                                     String panelId, String regionRef) {
        Placement placement;
        try {
            placement = placementById.get(Long.parseLong(panelId));
        } catch (NumberFormatException e) {
            placement = null;
        }
        if (placement == null) {
            ctx.issues.add(new ExportPlan.RefIssue(CATEGORY_PANEL, regionRef,
                    "结构树引用的面板投放不存在（ID " + panelId + "），跳过"));
            logger.warn("Export region references dangling panel: {} ({})", panelId, regionRef);
            return null;
        }
        String panelShape = placement.getPanelShape();
        if (panelShape == null || panelShape.isEmpty()) {
            ctx.issues.add(new ExportPlan.RefIssue(CATEGORY_PANEL, regionRef,
                    "面板投放缺少组件（ID " + panelId + "），跳过"));
            return null;
        }
        Shape shape = snapshot.resolveTrackShape(List.of(themeKey), panelShape);
        if (shape == null) {
            ctx.issues.add(new ExportPlan.RefIssue(CATEGORY_PANEL, regionRef + "#" + panelShape,
                    "Shape 未在快照中解析（清单缺失？）——设置仅做主题前缀反向，引用与资源未改写"));
        }
        return new PanelExport(placement, shape);
    }

    /**
     * 收集面板的内容引用（§3.2；E4a 增区块）：设置字段里的 channelRef/articleRef/blockRef，
     * 数据模板 {@code where.channel}/{@code where.parent}/{@code where.blockId}
     * （字面值或 {@code $settings.*} 间接引用）。
     */
    private void collectRefs(SeedExportContext ctx, PanelExport panel) {
        if (panel.shape() == null) {
            return;
        }
        Map<String, Object> settings = panel.placement().getSettings();
        collectFieldRefs(ctx, settings, panel.shape().getSettings());
        Map<String, Object> data = panel.shape().getData();
        if (data != null && data.get("where") instanceof Map<?, ?> where) {
            collectWhereChannel(ctx, where.get("channel"), settings);
            collectWhereChannel(ctx, where.get("parent"), settings);
            collectWhereBlock(ctx, where.get("blockId"), settings);
        }
    }

    private void collectFieldRefs(SeedExportContext ctx, Map<String, Object> values,
                                  Map<String, FieldDef> fieldDefs) {
        values.forEach((key, value) -> {
            FieldDef def = fieldDefs.get(key);
            if (def != null) {
                collectFieldRef(ctx, value, def);
            }
        });
    }

    private void collectFieldRef(SeedExportContext ctx, @Nullable Object value,
                                 FieldDef def) {
        if (value == null) {
            return;
        }
        switch (SeedExportFieldRules.shapeRule(def.getType())) {
            case CHANNEL_REF -> {
                Long id = SeedExportValueRewriter.asRefId(value);
                if (id != null) {
                    ctx.referencedChannelIds.add(id);
                }
            }
            case ARTICLE_REF -> {
                Long id = SeedExportValueRewriter.asRefId(value);
                if (id != null) {
                    ctx.referencedArticleIds.add(id);
                }
            }
            case BLOCK_REF -> {
                // E4a / D27：blockRef 引用的区块定义随包（D6 闭包类比——别名映射遗留形不收集，
                // 迟绑可散装且导出侧另有诊断）
                Long id = SeedExportValueRewriter.asRefId(value);
                if (id != null) {
                    ctx.referencedBlockIds.add(id);
                }
            }
            case VOTE_REF -> {
                // E4b / D30：voteRef 引用的投票定义随包（D6 闭包类比——来源绑定令牌须在种子内可解析）
                Long id = SeedExportValueRewriter.asRefId(value);
                if (id != null) {
                    ctx.referencedVoteIds.add(id);
                }
            }
            case LIST -> {
                FieldDef of = def.getFieldDef("of");
                if (value instanceof List<?> list && of != null) {
                    list.forEach(item -> collectFieldRef(ctx, item, of));
                }
            }
            case OBJECT -> {
                if (value instanceof Map<?, ?> map) {
                    Map<String, Object> cast = new LinkedHashMap<>(map.size());
                    map.forEach((k, v) -> cast.put(String.valueOf(k), v));
                    collectFieldRefs(ctx, cast, def.getFieldDefs("fields"));
                }
            }
            default -> {
                // 其余类型不承载内容引用
            }
        }
    }

    /**
     * {@code where.channel} 引用解析：数字/ID字符串 → 栏目ID；{@code $settings.X} → 设置值间接引用
     * （channelRef 产出为ID、文本设置为别名）；其余字符串按站内别名解析（迟绑——解析不到不算错）。
     */
    private void collectWhereChannel(SeedExportContext ctx, @Nullable Object value,
                                     Map<String, Object> settings) {
        if (value == null) {
            return;
        }
        Object resolved = value;
        if (value instanceof String str && str.startsWith(SETTINGS_REF_PREFIX)) {
            resolved = settings.get(str.substring(SETTINGS_REF_PREFIX.length()));
            if (resolved == null) {
                return;
            }
        }
        Long id = SeedExportValueRewriter.asRefId(resolved);
        if (id == null && resolved instanceof String alias && !alias.isBlank()) {
            id = ctx.channelIdByAlias.get(alias);
        }
        if (id != null) {
            ctx.referencedChannelIds.add(id);
        }
    }

    /**
     * {@code where.blockId} 引用解析（E4a）：数字/ID字符串 → 区块ID；{@code $settings.X} →
     * 设置值间接引用（blockRef 产出为ID）；别名字符串按站点可见别名解析（迟绑——解析不到不算错）。
     */
    private void collectWhereBlock(SeedExportContext ctx, @Nullable Object value,
                                   Map<String, Object> settings) {
        if (value == null) {
            return;
        }
        Object resolved = value;
        if (value instanceof String str && str.startsWith(SETTINGS_REF_PREFIX)) {
            resolved = settings.get(str.substring(SETTINGS_REF_PREFIX.length()));
            if (resolved == null) {
                return;
            }
        }
        Long id = SeedExportValueRewriter.asRefId(resolved);
        if (id == null && resolved instanceof String alias && !alias.isBlank()) {
            id = ctx.blockIdByAlias.get(alias);
        }
        if (id != null && ctx.blockById.containsKey(id)) {
            ctx.referencedBlockIds.add(id);
        }
    }

    // ----- 发射（内容导出之后：originId 映射已就绪）-----

    /**
     * 发射阶段：区域按页面聚合为 {@code regions/<page>.regions.json}，面板设置经值改写器
     * 反向令牌化；区域/面板按来源三元组分类计数（§4）。
     */
    void emit(SeedExportContext ctx, List<RegionExport> exports) {
        Map<String, Map<String, Object>> regionsByPage = new TreeMap<>();
        for (RegionExport export : exports) {
            PageRegion region = export.region();
            String regionRef = region.getPageRef() + "#" + region.getRegionName();
            Map<String, Object> pageRegions = regionsByPage.computeIfAbsent(region.getPageRef(),
                    key -> new TreeMap<>());
            String regionHash = SeedHash.regionHash(export.layout());
            SeedOriginClass regionClass = SeedOriginClass.classify(region.getOrigin(), ctx.origin,
                    region.getOriginHash(), regionHash);
            ctx.regions.count(regionClass);
            if (regionClass == SeedOriginClass.OPERATOR_NEW) {
                // 区域行无 origin_id_ 列（(site,page,region) 即唯一键）；散列取活结构树投影——
                // 与下次导出的分类口径同源（§4.2）
                ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.REGION, region.getId(),
                        null, regionHash));
            }
            List<Map<String, Object>> rows = new ArrayList<>(export.rows().size());
            for (RowExport row : export.rows()) {
                List<Map<String, Object>> columns = new ArrayList<>(row.columns().size());
                for (int ci = 0; ci < row.columns().size(); ci++) {
                    List<PanelExport> column = row.columns().get(ci);
                    List<Map<String, Object>> panels = new ArrayList<>(column.size());
                    for (int pi = 0; pi < column.size(); pi++) {
                        // 面板 originId 按导入侧再生方案以发射后位置合成（悬挂面板已剔除，不占位）——
                        // 认领行与本次导出种子的自导入逐行对应（§4.2）
                        String panelOriginId = regionRef + "#" + row.id() + "#" + ci + "#" + pi;
                        panels.add(panelNode(ctx, column.get(pi), regionRef, row.id(), panelOriginId));
                    }
                    columns.add(Map.of("panels", panels));
                }
                Map<String, Object> rowNode = new LinkedHashMap<>(4);
                rowNode.put("id", row.id());
                rowNode.put("layout", row.layout());
                rowNode.put("columns", columns);
                rows.add(rowNode);
            }
            Map<String, Object> regionNode = new LinkedHashMap<>(4);
            regionNode.put("rows", rows);
            pageRegions.put(region.getRegionName(), regionNode);
        }
        regionsByPage.forEach((page, regions) -> {
            if (regions.isEmpty()) {
                return;
            }
            Map<String, Object> file = new LinkedHashMap<>(4);
            file.put("$schema", SCHEMA_URL);
            file.put("page", page);
            file.put("regions", regions);
            ctx.files.put("regions/" + page + ".regions.json", SeedExportBundle.canonicalJsonBytes(file));
        });
    }

    private Map<String, Object> panelNode(SeedExportContext ctx, PanelExport panel, String regionRef,
                                          String rowId, String panelOriginId) {
        Placement placement = panel.placement();
        String placementHash = SeedHash.placementHash(placement);
        SeedOriginClass panelClass = SeedOriginClass.classify(placement.getOrigin(), ctx.origin,
                placement.getOriginHash(), placementHash);
        ctx.panels.count(panelClass);
        if (panelClass == SeedOriginClass.OPERATOR_NEW) {
            ctx.adoptions.add(new SeedAdoption(SeedAdoption.Category.PANEL, placement.getId(),
                    panelOriginId, placementHash));
        }
        String where = regionRef + "#" + rowId + "#" + placement.getPanelShape();
        Map<String, Object> settings = panel.shape() != null
                ? ctx.rewriter().rewriteSettings(placement.getSettings(), panel.shape().getSettings(), where)
                : ctx.rewriter().themeOnly(placement.getSettings());
        Map<String, Object> node = new LinkedHashMap<>(4);
        node.put("component", placement.getPanelShape());
        node.put("settings", settings);
        if (!Boolean.TRUE.equals(placement.getEnabled())) {
            node.put("enabled", false);
        }
        if (!Placement.VISIBILITY_ALL.equals(placement.getVisibility())) {
            node.put("visibility", placement.getVisibility());
        }
        return node;
    }

    /**
     * 扫描产物：一个 default 作用域区域及其已解析面板（结构树序）
     */
    record RegionExport(PageRegion region, RegionLayout layout, List<RowExport> rows) {
    }

    record RowExport(String id, String layout, List<List<PanelExport>> columns) {
    }

    record PanelExport(Placement placement, @Nullable Shape shape) {
    }
}
