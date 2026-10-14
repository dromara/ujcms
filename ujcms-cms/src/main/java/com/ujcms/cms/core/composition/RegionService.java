package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.core.JacksonException;
import com.ujcms.cms.core.composition.PageManifest.SlotDef;
import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldTypeRegistry;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import com.ujcms.cms.core.domain.PageRegion;
import com.ujcms.cms.core.domain.PageRegionSnapshot;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.PageRegionSnapshotMapper;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http409Exception;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 页面区域服务（page-regions §7.1）。渲染期 {@link #resolve}（结构树 → 逐面板经投放管线解析）与后台的
 * 草稿保存 / 发布 / 丢弃 / 详情。区域结构是 layout-as-data（{@link RegionLayout}）；面板内容仍是投放
 * （Panel = Placement），由 {@link PlacementService} 解析与持久化，故复用整条来源 / 生命周期 / 缓存管线。
 *
 * <p>缓存：<b>面板</b>解析经 {@link PlacementService#resolvePanel} 走 {@code PanelResolveCache}
 * （§19.1；Phase-1 的「未缓存」偏差随模板渲染器落地而失效——每次页面渲染 N 个面板
 * N 次来源执行正是最热路径）；区域<b>树</b>本身（单行点查）不缓存，发布/丢弃即时可见。
 *
 * <p>范围覆盖（visual-builder §9 / D6）：渲染期按候选范围回退解析——当前频道（{@code channel:<id>}）→
 * 活动布局变体（{@code variant:<surface>:<value>}）→ 页面默认（{@code default}），首个存在的行完全遮蔽
 * 其后候选（含空树）。各范围行的面板共用 {@code (site, page, region)} 投放池（面板行不带范围），
 * 树各自引用互不相交的子集：创建覆盖时首次保存剥离面板ID（复制默认树的面板为新投放，默认行永不被
 * 覆盖编辑改写）；孤立面板清理取<b>全范围行</b>已发布 ∪ 草稿引用的并集，跨范围互不误删。
 *
 * @author PONY
 */
@Service
public class RegionService {
    private static final Logger logger = LoggerFactory.getLogger(RegionService.class);

    /**
     * 发布快照保留上限（每坐标 {@code (site, page, region, scope)}；region-history §8）。
     * 失败代价不对称：留少了丢掉好状态（特性失效），留多了只是几 KB 级行——取 50 不取 20。
     */
    public static final int HISTORY_RETENTION = 50;

    private final PageRegionMapper mapper;
    private final PageRegionSnapshotMapper snapshotMapper;
    private final PlacementService placementService;
    private final PanelResolver panelResolver;
    private final ManifestService manifestService;
    private final CompositionSchemas schemas;
    private final SnowflakeSequence snowflakeSequence;
    private final CompositionCacheSync cacheSync;
    private final ConfigService configService;
    private final FieldValueValidator fieldValueValidator;

    public RegionService(PageRegionMapper mapper, PageRegionSnapshotMapper snapshotMapper,
                         PlacementService placementService, PanelResolver panelResolver,
                         ManifestService manifestService, CompositionSchemas schemas,
                         SnowflakeSequence snowflakeSequence, CompositionCacheSync cacheSync,
                         ConfigService configService, FieldTypeRegistry fieldTypeRegistry) {
        this.mapper = mapper;
        this.snapshotMapper = snapshotMapper;
        this.placementService = placementService;
        this.panelResolver = panelResolver;
        this.manifestService = manifestService;
        this.schemas = schemas;
        this.snowflakeSequence = snowflakeSequence;
        this.cacheSync = cacheSync;
        this.configService = configService;
        this.fieldValueValidator = new FieldValueValidator(fieldTypeRegistry);
    }

    // ---------------------------------------------------------------------------------------------
    // 渲染期解析
    // ---------------------------------------------------------------------------------------------

    /**
     * 渲染期解析区域（§7.1，无布局变体上下文）。
     */
    public ResolvedRegion resolve(Long siteId, String pageRef, String regionName, ResolveContext ctx,
                                  boolean preview) {
        return resolve(siteId, pageRef, regionName, ctx, preview, null);
    }

    /**
     * 渲染期解析区域（§7.1）。失败任意环节都返回空区域而非异常——渲染期永不破坏页面。
     *
     * <p>范围回退（visual-builder §9）：候选范围按 当前频道（{@code ctxParams.channelId} →
     * {@code channel:<id>}）→ 活动布局变体（{@code variant} 参数 → {@code variant:<surface>:<value>}）→
     * 页面默认 的次序逐一点查，首个存在的行完全遮蔽其后候选（含空树——「该范围刻意为空」）。
     * 共享区域（page-regions §15）再多一步：本页各范围均无行 → 宿主页默认行。
     *
     * @param siteId     站点ID
     * @param pageRef    页面清单名
     * @param regionName 区域插槽名
     * @param ctx        解析上下文（主题命名空间、预览标记、$ctx 参数）
     * @param preview    预览模式（草稿可见：用 draft 树并放行草稿卡片）
     * @param variant    活动布局变体（{@code <surface>:<value>}，渲染上下文携带；无变体页面为 {@code null}）
     * @return 已解析区域
     */
    public ResolvedRegion resolve(Long siteId, String pageRef, String regionName, ResolveContext ctx,
                                  boolean preview, @Nullable String variant) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        SlotDef regionSlotDef = findRegionSlotDef(snapshot, pageRef, regionName, ctx.themeKey());
        if (regionSlotDef == null) {
            logger.debug("Region resolve: region not declared. siteId={}, page={}, region={}",
                    siteId, pageRef, regionName);
            return ResolvedRegion.empty(regionName);
        }
        PageRegion region = resolveScopedRow(siteId, pageRef, regionName, ctx, variant);
        if (region == null && regionSlotDef.getSharedFrom() != null) {
            region = ownerDefaultRow(siteId, regionName, regionSlotDef);
        }
        if (region == null) {
            return ResolvedRegion.empty(regionName);
        }
        boolean useDraft = preview && region.isDraftPresent();
        RegionLayout layout;
        try {
            layout = RegionLayout.parse(useDraft ? region.getDraftLayoutJson() : region.getLayoutJson());
        } catch (RuntimeException e) {
            logger.warn("Region resolve: invalid layout json. regionId={}: {}", region.getId(), e.getMessage());
            return ResolvedRegion.empty(regionName);
        }
        Map<Long, Placement> panelsById = loadPanels(siteId, layout);
        List<ResolvedRegion.Row> rows = new ArrayList<>(layout.rows().size());
        for (RegionLayout.Row row : layout.rows()) {
            List<ResolvedRegion.Column> columns = new ArrayList<>(row.columns().size());
            for (RegionLayout.Column column : row.columns()) {
                List<ResolvedRegion.Panel> panels = new ArrayList<>(column.panels().size());
                for (String panelId : column.panels()) {
                    Long pid = parseId(panelId);
                    Placement placement = pid != null ? panelsById.get(pid) : null;
                    if (placement == null) {
                        logger.debug("Region resolve: dangling panel ref {} in region {}", panelId, region.getId());
                        continue;
                    }
                    try {
                        panels.add(resolvePanel(placement, ctx, snapshot));
                    } catch (RuntimeException e) {
                        // 单个面板失败只跳过该面板，不空整个区域（§9.3 渲染期永不破坏页面）
                        logger.warn("Region resolve: panel {} failed, skipped. region={}: {}",
                                placement.getId(), region.getId(), e.getMessage());
                    }
                }
                columns.add(new ResolvedRegion.Column(panels));
            }
            rows.add(new ResolvedRegion.Row(row.id(), row.layout(), columns));
        }
        return new ResolvedRegion(regionName, region.getScope(), useDraft,
                new ResolvedRegion.Layout(rows));
    }

    /**
     * 按候选范围次序点查区域行（visual-builder §9）：频道覆盖 → 变体覆盖 → 页面默认。
     * 无范围上下文的页面只发一次默认行点查（与 P3 之前的行为等价）。
     */
    @Nullable
    private PageRegion resolveScopedRow(Long siteId, String pageRef, String regionName, ResolveContext ctx,
                                        @Nullable String variant) {
        for (String scope : candidateScopes(ctx, variant)) {
            PageRegion region = mapper.selectByUnique(siteId, pageRef, regionName, scope);
            if (region != null) {
                return region;
            }
        }
        return null;
    }

    /**
     * 渲染上下文的候选范围（特异性降序）：{@code channel:<id>}（当前频道）→
     * {@code variant:<surface>:<value>}（活动布局变体）→ {@code default}。
     */
    private static List<String> candidateScopes(ResolveContext ctx, @Nullable String variant) {
        Object channelId = ctx.ctx(CtxParams.CHANNEL_ID);
        if (channelId == null && variant == null) {
            return List.of(PageRegion.SCOPE_DEFAULT);
        }
        List<String> scopes = new ArrayList<>(3);
        if (channelId != null) {
            scopes.add(PageRegion.SCOPE_PREFIX_CHANNEL + channelId);
        }
        if (variant != null) {
            scopes.add(PageRegion.variantScope(variant));
        }
        scopes.add(PageRegion.SCOPE_DEFAULT);
        return scopes;
    }

    private Map<Long, Placement> loadPanels(Long siteId, RegionLayout layout) {
        Map<Long, Placement> result = new HashMap<>();
        for (String panelId : layout.panelIds()) {
            Long pid = parseId(panelId);
            if (pid == null || result.containsKey(pid)) {
                continue;
            }
            Placement placement = placementService.select(pid);
            if (placement != null && Objects.equals(placement.getSiteId(), siteId)) {
                result.put(pid, placement);
            }
        }
        return result;
    }

    private ResolvedRegion.Panel resolvePanel(Placement placement, ResolveContext ctx,
                                              RegistrySnapshot snapshot) {
        String panelShape = placement.getPanelShape() != null ? placement.getPanelShape() : "";
        boolean active = placement.getEnabled() == null || placement.getEnabled();
        if (!active && !ctx.preview()) {
            // 停用面板生产路径不解析设置/数据（渲染器跳过；预览走完整解析供构建器显示并重新启用）
            Shape shape = snapshot.resolveShape(ctx.themeKey(), panelShape);
            return new ResolvedRegion.Panel(String.valueOf(placement.getId()), panelShape,
                    shape != null ? shape.getVersion() : "", Map.of(), List.of(), false,
                    placement.getVisibility());
        }
        return panelResolver.resolve(placement, ctx);
    }

    // ---------------------------------------------------------------------------------------------
    // 后台：详情 / 保存草稿 / 发布 / 丢弃
    // ---------------------------------------------------------------------------------------------

    /**
     * 列出当前站点已声明的全部区域（页面目录入口用，§9）：活动主题清单的区域。
     * 为每个区域附已发布行数与是否有草稿，供 {@code PageList.vue} 渲染「编辑布局」入口与状态。
     */
    public List<RegionSummary> listDeclaredRegions(Long siteId, Collection<String> themeKeys) {
        return listDeclaredRegions(siteId, null, themeKeys);
    }

    /**
     * 同 {@link #listDeclaredRegions(Long, Collection)}，可按页面过滤（页面聚合详情用，
     * visual-builder §7.1——避免为单页装载查询全部页面的区域行）。
     *
     * @param pageRef 页面清单名过滤（{@code null} 不过滤）
     */
    public List<RegionSummary> listDeclaredRegions(Long siteId, @Nullable String pageRef,
                                                   Collection<String> themeKeys) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        Map<String, RegionSummary> result = new LinkedHashMap<>();
        for (String themeKey : themeKeys) {
            Map<String, PageManifest> manifests = snapshot.getThemeManifests().get(themeKey);
            if (manifests == null) {
                continue;
            }
            manifests.forEach((page, manifest) -> {
                if (pageRef == null || pageRef.equals(page)) {
                    collectRegions(result, siteId, manifest, page);
                }
            });
        }
        return List.copyOf(result.values());
    }

    private void collectRegions(Map<String, RegionSummary> out, Long siteId, PageManifest manifest,
                                String pageRef) {
        manifest.getSlots().forEach((regionName, def) -> {
            String key = pageRef + '\u0000' + regionName;
            if (out.containsKey(key)) {
                return;
            }
            // 目录状态反映页面默认行（覆盖行的状态在构建器切到对应范围后按范围详情呈现）；
            // 共享区域未分叉时反映宿主页默认行（§15）
            PageRegion pageRow = mapper.selectByUnique(siteId, pageRef, regionName, PageRegion.SCOPE_DEFAULT);
            PageRegion region = pageRow == null && def.getSharedFrom() != null
                    ? ownerDefaultRow(siteId, regionName, def) : pageRow;
            int publishedRows = region != null ? RegionLayout.parse(region.getLayoutJson()).rows().size() : 0;
            boolean hasDraft = region != null && region.isDraftPresent();
            out.put(key, new RegionSummary(pageRef, regionName, manifest.getLabel(),
                    def.getLabel(), def.getDescription(),
                    def.getAllowedShapes(), publishedRows, hasDraft,
                    def.getSharedFrom(), def.getSharedFrom() != null && pageRow != null));
        });
    }

    /**
     * 区域详情（§7.3）：已发布树 + 草稿树 + 本区域全部面板（投放，含配置与卡片，供编辑器）。
     *
     * <p>范围详情（visual-builder §9）：{@code scope} 指定覆盖范围时读取覆盖行；覆盖行尚不存在 →
     * {@code overrideExists=false}、{@code version=0}，并以<b>页面默认行的已发布树</b>作 {@code published}
     * 返回（创建覆盖的复制种子——构建器据此显示回退内容，首次保存时服务端剥离面板ID复制为新投放）。
     * 面板列表始终为本区域投放池全量（含各范围行引用的面板，按ID索引）。
     *
     * <p>共享区域（page-regions §15）：跟随页未分叉且请求默认范围 → 返回<b>宿主页</b>的详情
     * （{@code page} 为宿主页名，版本/树/草稿/面板均为宿主行；控制台此后直接对该坐标读写，
     * 写端点不做别名转换——一行一个坐标）。请求覆盖范围而覆盖行不存在时，复制种子取有效默认行
     * （本页默认行，无则宿主行）。{@code forked} = 本页存在自有默认行。
     */
    public RegionDetail getDetail(Long siteId, String pageRef, String regionName,
                                  String scope, Collection<String> themeKeys) {
        RegionMatch match = requireRegionSlotDef(pageRef, regionName, themeKeys);
        SlotDef regionSlotDef = match.slotDef();
        requireValidScope(scope);
        String owner = regionSlotDef.getSharedFrom();
        PageRegion region = mapper.selectByUnique(siteId, pageRef, regionName, scope);
        if (region == null && owner != null && PageRegion.SCOPE_DEFAULT.equals(scope)) {
            return getDetail(siteId, owner, regionName, PageRegion.SCOPE_DEFAULT, themeKeys)
                    .withSharing(owner, false);
        }
        boolean overrideExists = region != null;
        RegionLayout published;
        if (region != null) {
            published = RegionLayout.parse(region.getLayoutJson());
        } else if (!PageRegion.SCOPE_DEFAULT.equals(scope)) {
            // 覆盖行不存在：以有效默认行（本页默认行，共享区域无则宿主行）的已发布树为复制种子（皆无为空树）
            PageRegion fallback = effectiveDefaultRow(siteId, pageRef, regionName, regionSlotDef);
            published = fallback != null ? RegionLayout.parse(fallback.getLayoutJson()) : RegionLayout.EMPTY;
        } else {
            published = RegionLayout.EMPTY;
        }
        RegionLayout draft = region != null && region.isDraftPresent()
                ? RegionLayout.parse(region.getDraftLayoutJson()) : null;
        List<PanelDetail> panels = listRegionPanels(siteId, pageRef, regionName).stream()
                .map(PanelDetail::of)
                .toList();
        boolean forked = owner != null && (PageRegion.SCOPE_DEFAULT.equals(scope) ? region != null
                : mapper.selectByUnique(siteId, pageRef, regionName, PageRegion.SCOPE_DEFAULT) != null);
        return new RegionDetail(pageRef, regionName, scope, overrideExists,
                region != null ? region.getVersion() : 0,
                published, draft, regionSlotDef.getAllowedShapes(), regionSlotDef.getAllowedRowLayouts(),
                regionSlotDef.getMaxRows(), regionSlotDef.getMaxPanelsPerColumn(),
                regionSlotDef.getLabel(), regionSlotDef.getDescription(), panels, owner, forked);
    }

    /**
     * 有效默认行（§15）：本页默认行；共享区域且本页未分叉 → 宿主页默认行；皆无 → {@code null}。
     */
    @Nullable
    private PageRegion effectiveDefaultRow(Long siteId, String pageRef, String regionName, SlotDef def) {
        PageRegion region = mapper.selectByUnique(siteId, pageRef, regionName, PageRegion.SCOPE_DEFAULT);
        return region == null && def.getSharedFrom() != null ? ownerDefaultRow(siteId, regionName, def) : region;
    }

    @Nullable
    private PageRegion ownerDefaultRow(Long siteId, String regionName, SlotDef def) {
        return mapper.selectByUnique(siteId, def.getSharedFrom(), regionName, PageRegion.SCOPE_DEFAULT);
    }

    /**
     * 页面下已存在的覆盖范围值（构建器范围切换器目录，visual-builder §9）。
     */
    public List<String> listScopes(Long siteId, String pageRef) {
        return mapper.listScopes(siteId, pageRef);
    }

    /**
     * 页面调色板 Shape 目录（visual-builder §7.1）：页面全部区域 {@code allowedShapes} 的并集
     * （区域 Schema 强制 allowedShapes 非空，无「不限制」形态），另并入既有面板的 Shape——声明收紧后
     * 的旧面板仍需标签显示，不被任何区域接受的条目由控制台按 §7.4 置灰。按命名空间链解析
     * （活动主题 ∪ core，与 {@link #resolvePanel} 的渲染口径一致），
     * 解析不到 → {@code missing}（§5.4 该形状缺失）。
     */
    public List<PaletteShape> paletteShapes(Long siteId, String pageRef,
                                            Collection<String> themeKeys) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        Set<String> names = new LinkedHashSet<>();
        // 区域名 → 宿主页名（共享区域，§15；自有区域为 null）
        Map<String, String> regionOwners = new LinkedHashMap<>();
        for (String themeKey : themeKeys) {
            PageManifest manifest = snapshot.getThemeManifest(themeKey, pageRef);
            if (manifest != null) {
                manifest.getSlots().forEach((regionName, def) -> {
                    names.addAll(def.getAllowedShapes());
                    regionOwners.putIfAbsent(regionName, def.getSharedFrom());
                });
            }
        }
        for (Map.Entry<String, String> entry : regionOwners.entrySet()) {
            for (Placement panel : listRegionPanels(siteId, pageRef, entry.getKey())) {
                names.add(panel.getPanelShape());
            }
            if (entry.getValue() != null) {
                // 共享区域：宿主行的既有面板同样出现在本页画布
                for (Placement panel : listRegionPanels(siteId, entry.getValue(), entry.getKey())) {
                    names.add(panel.getPanelShape());
                }
            }
        }
        List<PaletteShape> result = new ArrayList<>(names.size());
        for (String name : names) {
            Shape shape = snapshot.resolveTrackShape(themeKeys, name);
            result.add(shape != null
                    ? new PaletteShape(shape.getName(), shape.getVersion(), shape.getLabel(),
                            shape.getDescription(), shape.getTags(), shape.getSource(),
                            shape.getPresentation(), resolveThumbnail(shape), false)
                    : new PaletteShape(name, "", Map.of(), null, List.of(), "", Map.of(), null, true));
        }
        return result;
    }

    /**
     * 解析调色板缩略图为可加载 URL（visual-builder P6/D11，服务端统一解析，控制台不拼装）：
     * {@code data:image/} URI 直通；相对路径相对主题根目录经模板存储公开前缀解析
     * （{@code {templatesUrl}/{siteId}/{themeName}/…}，A4 画廊口径，纯 URL 拼装无文件 IO；
     * theme-owned-shapes D15 起 Shape 只有主题来源）。
     * 加载期 Schema 已校验语法，此处再拒绝穿越/绝对路径/协议前缀（纵深防御）；
     * 不可解析 → {@code null}，控制台回退 icon。
     */
    @Nullable
    private String resolveThumbnail(Shape shape) {
        Object value = shape.getPresentation().get("thumbnail");
        if (!(value instanceof String path) || path.isBlank()) {
            return null;
        }
        if (path.startsWith("data:image/")) {
            return path;
        }
        if (path.startsWith("/") || path.contains("..") || path.contains(":") || path.contains("\\")) {
            return null;
        }
        String source = shape.getSource();
        if (source.startsWith(Shape.SOURCE_THEME_PREFIX)) {
            String themeKey = source.substring(Shape.SOURCE_THEME_PREFIX.length());
            return configService.getUnique().getTemplateStorage().getUrl() + "/" + themeKey + "/" + path;
        }
        return null;
    }

    /**
     * 保存草稿（§7.3）：事务内 upsert 面板投放 + 写 {@code draft_layout_json_}。
     * 先做乐观锁快速失败（{@code clientVersion} 与当前行 {@code version_} 不符 → 409——并发编辑不静默覆盖，
     * 否则后保存方会经孤立面板清理删除先保存方刚建的面板；guarded update 兜底 check-then-act 竞态），
     * 再校验结构（行布局键、列数与行布局一致、行数/单列面板数上限），逐面板由 {@link PlacementService#savePanel}
     * 校验 Shape/来源被区域允许、卡片 Shape 匹配；再以已发布 Schema 复核所建树；最后清理孤立面板。
     *
     * <p><b>写时复制（visual-builder shell-polish D20）</b>：草稿保存绝不改写被已发布树引用的面板——
     * 内容未变则保留其ID（不写库，仅复验），内容有变则复制为新投放并让草稿树引用副本
     * （{@link #saveDraftPanel}）。此前 {@code enabled_}/{@code settings_json_} 由草稿与已发布树共用同一行，
     * 设置编辑一经保存即上线、丢弃草稿也无从回退。
     *
     * <p>返回新版本号 + 按遍历序（行 → 列 → 面板）的真实面板ID（visual-builder §6：保存即逐操作持久化，
     * 构建器凭返回值串联下一次操作，无需回读详情）。
     *
     * @param clientVersion 客户端读取详情时的 {@code version}（无区域行时为 0）
     */
    @Transactional(rollbackFor = Exception.class)
    public SaveDraftResult saveDraft(Long siteId, String pageRef, String regionName, String scope,
                                     List<RowInput> rowInputs, int clientVersion, Collection<String> themeKeys) {
        RegionMatch match = requireRegionSlotDef(pageRef, regionName, themeKeys);
        SlotDef regionSlotDef = match.slotDef();
        requireValidScope(scope);
        requireScopeAllowedOnOwner(pageRef, regionName, scope, themeKeys);
        // 乐观锁快速失败（在建面板前；事务内最终以 updateWithLock 兜底）
        PageRegion region = mapper.selectByUnique(siteId, pageRef, regionName, scope);
        requireVersionMatch(region, clientVersion);
        // 建行即剥离全部面板ID（visual-builder §9 创建覆盖 / page-regions §15 跟随页分叉）：不存在的行不引用
        // 任何面板，客户端提交的ID必属他行（默认行或宿主行的面板）——复制为本行自己的新投放（同 Shape/设置），
        // 原行的面板永不被改写；卡片实体仍共享（卡片库语义）
        boolean creating = region == null;
        // 结构校验（在 upsert 前快速失败；逐面板 Shape/来源校验在 savePanel 内）
        List<String> structureErrors = toLayout(rowInputs, true).validate(regionSlotDef, null);
        if (!structureErrors.isEmpty()) {
            throw new Http400Exception("区域结构非法: " + String.join("; ", structureErrors));
        }
        // upsert 面板并以真实投放ID构建树；按遍历序收集真实ID供构建器串联后续操作。
        // 被任一范围行的已发布树引用的面板是「冻结」的（写时复制，D20）——引用并集与孤立清理同一口径
        Set<String> publishedIds = publishedPanelIds(siteId, pageRef, regionName);
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        List<PanelRef> panelRefs = new ArrayList<>();
        List<RegionLayout.Row> rows = new ArrayList<>(rowInputs.size());
        for (RowInput rowInput : rowInputs) {
            List<RegionLayout.Column> columns = new ArrayList<>(rowInput.columns().size());
            for (ColumnInput columnInput : rowInput.columns()) {
                List<String> ids = new ArrayList<>(columnInput.panels().size());
                for (PanelInput panelInput : columnInput.panels()) {
                    PanelInput effective = creating ? panelInput.withoutId() : panelInput;
                    Placement bean = effective.toBean(siteId, pageRef, regionName);
                    bean.setSettings(sanitizeSettings(snapshot.resolveTrackShape(themeKeys,
                            effective.shape()), effective.shape(), bean.getSettings()));
                    Long id = saveDraftPanel(bean, regionSlotDef, publishedIds);
                    ids.add(String.valueOf(id));
                    panelRefs.add(new PanelRef(String.valueOf(id), effective.shape()));
                }
                columns.add(new RegionLayout.Column(ids));
            }
            rows.add(new RegionLayout.Row(rowInput.id(), rowInput.layout(), columns));
        }
        RegionLayout draft = new RegionLayout(rows);
        String draftJson = draft.toJson();
        validateLayoutSchema(draftJson);
        region = upsertRegionDraft(siteId, pageRef, regionName, scope, draftJson, region, clientVersion);
        cleanupOrphanPanels(siteId, pageRef, regionName);
        return new SaveDraftResult(region.getVersion() != null ? region.getVersion() : 0, List.copyOf(panelRefs));
    }

    /**
     * 草稿面板持久化（写时复制，visual-builder shell-polish D20）：面板ID被任一已发布树引用（冻结）→
     * 内容（Shape/设置/启用/显示设备）与库中行相同则保留ID且不写库（仍复验 Shape/显示设备）；
     * 内容有变则复制为新投放——新雪花ID，来源三元组（origin/originId/originHash）与归档标记随副本，
     * 种子谱系与「操作员已改」散列判定不因复制失真——草稿树改引用副本。发布翻转树后原行经孤立清理删除，
     * 丢弃草稿则副本被清理。归属不符/行已不存在的冻结ID交给 {@link PlacementService#savePanel}
     * 的归属防线与未知ID重建；仅草稿树引用的面板照旧就地更新。
     */
    private Long saveDraftPanel(Placement bean, SlotDef regionSlotDef, Set<String> publishedIds) {
        Long id = bean.getId();
        if (id != null && id != 0L && publishedIds.contains(String.valueOf(id))) {
            Placement existed = placementService.select(id);
            if (existed != null && existed.getPanelShape() != null
                    && Objects.equals(existed.getSiteId(), bean.getSiteId())
                    && Objects.equals(existed.getPageRef(), bean.getPageRef())
                    && Objects.equals(existed.getSlotName(), bean.getSlotName())) {
                if (sameContent(existed, bean)) {
                    placementService.validate(bean, regionSlotDef);
                    return id;
                }
                bean.setId(0L);
                bean.setOrigin(existed.getOrigin());
                bean.setOriginId(existed.getOriginId());
                bean.setOriginHash(existed.getOriginHash());
                bean.setArchivedAt(existed.getArchivedAt());
            }
        }
        return placementService.savePanel(bean, regionSlotDef);
    }

    /**
     * 面板内容相等：Shape、启用态（null 视同启用）、显示设备、设置值（两侧均为 Jackson 解析树，
     * 库中 JSON 与请求 JSON 的数值类型一致，Map 相等不受键序影响）。库中设置 JSON 损坏视同有变（复制即修复）。
     */
    private static boolean sameContent(Placement existed, Placement bean) {
        Map<String, Object> existedSettings;
        try {
            existedSettings = existed.getSettings();
        } catch (IllegalStateException e) {
            return false;
        }
        boolean existedEnabled = existed.getEnabled() == null || existed.getEnabled();
        boolean beanEnabled = bean.getEnabled() == null || bean.getEnabled();
        return Objects.equals(existed.getPanelShape(), bean.getPanelShape())
                && existedEnabled == beanEnabled
                && Objects.equals(existed.getVisibility(), bean.getVisibility())
                && Objects.equals(existedSettings, bean.getSettings());
    }

    /**
     * 区域全部范围行的已发布树引用的面板ID并集（各范围行共用投放池，冻结判定须跨全范围）。
     */
    private Set<String> publishedPanelIds(Long siteId, String pageRef, String regionName) {
        Set<String> ids = new LinkedHashSet<>();
        for (PageRegion row : mapper.listByRegion(siteId, pageRef, regionName)) {
            ids.addAll(RegionLayout.parse(row.getLayoutJson()).panelIds());
        }
        return ids;
    }

    /**
     * 发布（§7.3）：草稿树 → 已发布树，清空草稿。乐观锁同 {@link #saveDraft}——并发编辑下不发布他人草稿。
     * 发布后显式失效构成缓存（纯重排发布无面板增删，不触发投放侧的失效路径）。
     *
     * <p>发布门（design-review P1-4 / B3）：草稿树内启用面板的必填设置为空 → 400 指名行列/面板/字段
     * （保存期刻意不强制，见 {@link #sanitizeSettings}；已发布存量与组件演化产生的缺失由健康报告呈现）。
     *
     * <p>发布快照（region-history / B1）：只追加历史——先回填漂移（被替换的已发布树未被最新快照覆盖：
     * 特性上线前存量或种子导入直写），再快照新发布状态（与最新条目相同则去重跳过），最后按保留上限
     * 裁剪。快照物化整棵树（逐面板 shape/settings/enabled，不含面板ID——面板池会被孤立清理改写）；
     * 快照失败即发布失败（静默缺记的历史比发布失败更糟）。
     *
     * @return 发布后的乐观锁版本号
     */
    @Transactional(rollbackFor = Exception.class)
    public int publish(Long siteId, String pageRef, String regionName, String scope,
                       int clientVersion, Collection<String> themeKeys, @Nullable String actor,
                       @Nullable String label) {
        return doPublish(siteId, pageRef, regionName, scope, clientVersion, themeKeys, actor, label,
                PageRegionSnapshot.SOURCE_PUBLISH, null);
    }

    private int doPublish(Long siteId, String pageRef, String regionName, String scope,
                          int clientVersion, Collection<String> themeKeys, @Nullable String actor,
                          @Nullable String label, String source, @Nullable Long restoredFrom) {
        PageRegion region = require(siteId, pageRef, regionName, scope);
        requireVersionMatch(region, clientVersion);
        if (!region.isDraftPresent()) {
            throw new Http400Exception("无草稿可发布");
        }
        RegionLayout draft = RegionLayout.parse(region.getDraftLayoutJson());
        requireRequiredSettings(siteId, draft, themeKeys);
        // 回填：被替换的已发布状态若未被最新快照覆盖，先补记——「恢复亦快照被替换者」的普适保证
        SnapshotContent replaced = materialize(siteId, RegionLayout.parse(region.getLayoutJson()));
        appendSnapshotIfDrifted(region, replaced, PageRegionSnapshot.SOURCE_BACKFILL, actor, null, null);
        // 新发布状态在翻转前物化（同一草稿树，面板此刻全部存活）
        SnapshotContent published = materialize(siteId, draft);
        region.setLayoutJson(region.getDraftLayoutJson());
        region.setDraftLayoutJson(null);
        region.setModified(OffsetDateTime.now());
        updateWithLockOr409(region, clientVersion);
        appendSnapshotIfDrifted(region, published, source, actor, label, restoredFrom);
        trimHistory(siteId, pageRef, regionName, scope);
        cleanupOrphanPanels(siteId, pageRef, regionName);
        cacheSync.invalidateAll("page-region");
        return region.getVersion() != null ? region.getVersion() : clientVersion + 1;
    }

    /**
     * 恢复历史快照（region-history §6）：恢复 = 走标准 saveDraft→publish 管线的一次新发布——结构、
     * allowedShapes、设置清洗与必填门全部按<b>当前世界</b>重跑（主题收紧后不再合法的快照以 400 报错，
     * 不静默复活旧树）；面板始终复制新建（快照不含面板ID，同覆盖行创建的剥离语义）。存在未发布草稿时
     * 拒绝，除非 {@code discardDraft}（显式、经操作日志审计的同意）——恢复绝不静默销毁未发布工作。
     * 行不存在（覆盖行已删除）时经草稿保存路径重建（{@code clientVersion} 须为 0）。
     * 历史效应：发布路径先回填被替换状态，再追加 {@code source=restore} 条目——历史只增不减。
     *
     * @return 恢复后的乐观锁版本号（构建器重链）
     */
    @Transactional(rollbackFor = Exception.class)
    public int restore(Long siteId, String pageRef, String regionName, String scope,
                       Long snapshotId, int clientVersion, boolean discardDraft,
                       Collection<String> themeKeys, @Nullable String actor) {
        requireValidScope(scope);
        PageRegionSnapshot snap = snapshotMapper.select(snapshotId);
        if (snap == null || !Objects.equals(snap.getSiteId(), siteId) || !snap.getPageRef().equals(pageRef)
                || !snap.getRegionName().equals(regionName)
                || !snap.getScope().equals(scope)) {
            throw new Http400Exception("快照不存在或与请求坐标不符: " + snapshotId);
        }
        PageRegion region = mapper.selectByUnique(siteId, pageRef, regionName, scope);
        requireVersionMatch(region, clientVersion);
        int version = clientVersion;
        if (region != null && region.isDraftPresent()) {
            if (!discardDraft) {
                throw new Http400Exception("存在未发布草稿，请先发布或丢弃草稿再恢复");
            }
            discard(siteId, pageRef, regionName, scope, version);
            version++;
        }
        SnapshotContent content;
        try {
            content = SnapshotContent.parse(snap.getContentJson());
        } catch (IllegalArgumentException e) {
            throw new Http400Exception("快照内容无法解析: " + snapshotId);
        }
        SaveDraftResult draftResult = saveDraft(siteId, pageRef, regionName, scope,
                toRowInputs(content), version, themeKeys);
        return doPublish(siteId, pageRef, regionName, scope, draftResult.version(), themeKeys, actor,
                null, PageRegionSnapshot.SOURCE_RESTORE, snap.getId());
    }

    /**
     * 坐标下的发布快照历史（新→旧；行数受 {@link #HISTORY_RETENTION} 约束）。
     */
    public List<PageRegionSnapshot> listHistory(Long siteId, String pageRef, String regionName,
                                                String scope) {
        requireValidScope(scope);
        return snapshotMapper.listByCoordinates(siteId, pageRef, regionName, scope);
    }

    /**
     * 单条快照（含物化内容；站点归属校验）。
     */
    public PageRegionSnapshot getSnapshot(Long siteId, Long id) {
        PageRegionSnapshot snap = snapshotMapper.select(id);
        if (snap == null || !Objects.equals(snap.getSiteId(), siteId)) {
            throw new Http400Exception("快照不存在: " + id);
        }
        return snap;
    }

    /**
     * 丢弃草稿（§7.3）。乐观锁同 {@link #saveDraft}——并发编辑下不丢弃他人草稿。
     */
    @Transactional(rollbackFor = Exception.class)
    public void discard(Long siteId, String pageRef, String regionName, String scope,
                        int clientVersion) {
        PageRegion region = require(siteId, pageRef, regionName, scope);
        requireVersionMatch(region, clientVersion);
        if (!region.isDraftPresent()) {
            return;
        }
        region.setDraftLayoutJson(null);
        region.setModified(OffsetDateTime.now());
        updateWithLockOr409(region, clientVersion);
        cleanupOrphanPanels(siteId, pageRef, regionName);
    }

    /**
     * 删除覆盖行（visual-builder §9 revert-to-default）：恢复该范围到页面默认。仅覆盖范围可删
     * （默认行不经此路径删除）——例外：共享区域跟随页的默认行（page-regions §15 取消分叉，
     * 回到宿主行；本页的频道/变体覆盖行不动，它们是各自独立的状态）；乐观锁同 {@link #saveDraft}；
     * 随后清理仅被该行引用的孤立面板（全范围引用并集保证不误删默认行/其他覆盖行的面板），并失效构成缓存。
     *
     * <p>删除快照（region-history §5c）：删除前捕获被销毁的已发布树（{@code source=delete}，
     * 与最新条目相同则去重）——回填检查覆盖不到删除（行没了，后续发布回填的是新行的前状态）；
     * 凭该快照可经 {@link #restore} 重建覆盖行。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteOverride(Long siteId, String pageRef, String regionName, String scope,
                               int clientVersion, Collection<String> themeKeys, @Nullable String actor) {
        requireValidScope(scope);
        if (PageRegion.SCOPE_DEFAULT.equals(scope)
                && requireRegionSlotDef(pageRef, regionName, themeKeys).slotDef().getSharedFrom() == null) {
            throw new Http400Exception("页面默认行不可删除");
        }
        PageRegion region = require(siteId, pageRef, regionName, scope);
        requireVersionMatch(region, clientVersion);
        SnapshotContent destroyed = materialize(siteId, RegionLayout.parse(region.getLayoutJson()));
        appendSnapshotIfDrifted(region, destroyed, PageRegionSnapshot.SOURCE_DELETE, actor, null, null);
        trimHistory(siteId, pageRef, regionName, scope);
        mapper.delete(region.getId());
        cleanupOrphanPanels(siteId, pageRef, regionName);
        cacheSync.invalidateAll("page-region");
    }

    // ---------------------------------------------------------------------------------------------
    // 种子重置（theme-marketplace D22）：ThemeSeedImporter 重建本源区域结构树的发布原语。
    // 与 backfill/publish 成对、由导入器在同一事务内按「回填 → 面板改写 → 发布」顺序调用——
    // 回填必须先于面板改写：快照物化自面板行现值，后改写则被替换状态里的操作员内容无从保全。
    // ---------------------------------------------------------------------------------------------

    /**
     * 种子重置前置回填（D22）：在面板行被种子内容改写<b>之前</b>物化并快照当前状态——先未发布
     * 草稿（若在场；重置将丢弃草稿，「绝不静默销毁未发布工作」由本快照保全）、后已发布树
     * （均 {@code source=backfill}，与最新条目相同则去重）。此后整次重置即可经构建器发布历史
     * 逐区域恢复（恢复始终复制建新面板，掉树面板被孤立清理删除也无碍）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void seedResetBackfill(PageRegion region, @Nullable String actor) {
        if (region.isDraftPresent()) {
            SnapshotContent draft = materialize(region.getSiteId(),
                    RegionLayout.parse(region.getDraftLayoutJson()));
            appendSnapshotIfDrifted(region, draft, PageRegionSnapshot.SOURCE_BACKFILL, actor, null, null);
        }
        SnapshotContent replaced = materialize(region.getSiteId(), RegionLayout.parse(region.getLayoutJson()));
        appendSnapshotIfDrifted(region, replaced, PageRegionSnapshot.SOURCE_BACKFILL, actor, null, null);
    }

    /**
     * 种子重置发布（D22）：以导入器已构建的种子树（面板已按种子创建/刷新）替换本源区域的
     * 已发布树，丢弃未发布草稿（前置回填已快照），追加 {@code source=seed-reset} 快照并裁剪
     * 历史，清理掉树面板（标准孤立清理——不被任何范围行引用即删除，内容已由回填快照保全），
     * 失效构成缓存。刻意不设必填设置发布门（与种子新导入路径同宽——种子内容缺必填由健康报告
     * 呈现，绝不使整次还原失败）。
     *
     * @param region     本源区域行（调用方已校验 {@code origin_} 归属）
     * @param layout     以真实投放ID装配的种子结构树（已通过 Schema 复核）
     * @param originHash 新结构树投影散列（R4；{@code updateWithLock} 不含来源列，经专用语句写入）
     * @param actor      操作员用户名（快照署名；安装引导等无请求上下文时为 {@code null}）
     */
    @Transactional(rollbackFor = Exception.class)
    public void seedResetPublish(PageRegion region, RegionLayout layout, String originHash,
                                 @Nullable String actor) {
        SnapshotContent published = materialize(region.getSiteId(), layout);
        region.setLayoutJson(layout.toJson());
        region.setDraftLayoutJson(null);
        region.setModified(OffsetDateTime.now());
        int version = region.getVersion() != null ? region.getVersion() : 0;
        updateWithLockOr409(region, version);
        mapper.updateOriginHash(region.getId(), originHash);
        region.setOriginHash(originHash);
        appendSnapshotIfDrifted(region, published, PageRegionSnapshot.SOURCE_SEED_RESET, actor, null, null);
        trimHistory(region.getSiteId(), region.getPageRef(), region.getRegionName(), region.getScope());
        cleanupOrphanPanels(region.getSiteId(), region.getPageRef(), region.getRegionName());
        cacheSync.invalidateAll("page-region");
    }

    // ---------------------------------------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------------------------------------

    private PageRegion upsertRegionDraft(Long siteId, String pageRef, String regionName, String scope,
                                         String draftJson, @Nullable PageRegion region, int clientVersion) {
        OffsetDateTime now = OffsetDateTime.now();
        if (region == null) {
            region = new PageRegion();
            region.setId(snowflakeSequence.nextId());
            region.setSiteId(siteId);
            region.setPageRef(pageRef);
            region.setRegionName(regionName);
            region.setScope(scope);
            region.setLayoutJson(RegionLayout.EMPTY.toJson());
            region.setDraftLayoutJson(draftJson);
            region.setVersion(1);
            region.setCreated(now);
            region.setModified(now);
            try {
                mapper.insert(region);
            } catch (DuplicateKeyException e) {
                // 两个会话并发创建同一范围行：先插入者胜，后到者以 409 走既有冲突对话框（跨范围 QA 路径）
                throw new Http409Exception("error.pageRegion.versionConflict");
            }
        } else {
            region.setDraftLayoutJson(draftJson);
            region.setModified(now);
            updateWithLockOr409(region, clientVersion);
        }
        return region;
    }

    /**
     * 范围值合法性校验（后台入口统一调用；渲染期候选值由服务端上下文构造，不经此处）。
     */
    private static void requireValidScope(String scope) {
        if (!PageRegion.isValidScope(scope)) {
            throw new Http400Exception("区域范围非法: " + scope);
        }
    }

    /**
     * 乐观锁快速失败：客户端版本与当前行版本（无行时 0）不符 → 409。
     * 事务内的 {@link #updateWithLockOr409} 兜底 check-then-act 竞态。
     */
    private static void requireVersionMatch(@Nullable PageRegion region, int clientVersion) {
        int current = region != null && region.getVersion() != null ? region.getVersion() : 0;
        if (clientVersion != current) {
            throw new Http409Exception("error.pageRegion.versionConflict");
        }
    }

    /**
     * 乐观锁更新（WHERE 带客户端版本号；0 行 → 409），命中后同步内存版本号。
     */
    private void updateWithLockOr409(PageRegion region, int clientVersion) {
        if (mapper.updateWithLock(region, clientVersion) <= 0) {
            throw new Http409Exception("error.pageRegion.versionConflict");
        }
        region.setVersion(clientVersion + 1);
    }

    /**
     * 清理孤立面板：删除属于本区域、但不被<b>任何范围行</b>的已发布树或草稿树引用的面板投放。
     * 各范围行共用投放池（visual-builder §9），引用并集必须跨全部范围行取——只按当前行清理会误删
     * 默认行/其他覆盖行刚建或仍引用的面板。
     */
    private void cleanupOrphanPanels(Long siteId, String pageRef, String regionName) {
        Set<String> referenced = new LinkedHashSet<>();
        for (PageRegion row : mapper.listByRegion(siteId, pageRef, regionName)) {
            referenced.addAll(RegionLayout.parse(row.getLayoutJson()).panelIds());
            if (row.isDraftPresent()) {
                referenced.addAll(RegionLayout.parse(row.getDraftLayoutJson()).panelIds());
            }
        }
        for (Placement panel : listRegionPanels(siteId, pageRef, regionName)) {
            if (!referenced.contains(String.valueOf(panel.getId()))) {
                placementService.delete(panel.getId());
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 归档面板删除与残留清理（D21 健康页路径）。保存期不变量是「树引用 ⊆ 存活面板」，
    // 因此删除投放必须树先行——先从 (page, region) 全部范围行剔除引用，再删行，
    // 否则即制造悬空引用（带外删除残留）。
    // ---------------------------------------------------------------------------------------------

    /**
     * 删除单个归档面板（健康页逐行清理）：先从所属区域全部范围行的已发布树与草稿树剔除引用，
     * 再删除投放行。归档校验由调用方（Controller）完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteArchivedPanel(Placement panel) {
        prunePanelRefs(panel.getSiteId(), panel.getPageRef(), panel.getSlotName(),
                Set.of(String.valueOf(panel.getId())));
        placementService.delete(panel.getId());
        cacheSync.invalidateAll("page-region");
    }

    /**
     * 清空归档面板（健康页批量清理）：按区域分组树先行剔除引用后删除全部已归档投放行。
     *
     * @return 删除的归档投放行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteArchivedPanels(Long siteId) {
        List<Placement> archived = placementService.listBySiteId(siteId).stream()
                .filter(Placement::isArchived).toList();
        if (archived.isEmpty()) {
            return 0;
        }
        Map<String, Set<String>> byRegion = new LinkedHashMap<>();
        for (Placement panel : archived) {
            byRegion.computeIfAbsent(panel.getPageRef() + "\n" + panel.getSlotName(),
                    key -> new LinkedHashSet<>()).add(String.valueOf(panel.getId()));
        }
        byRegion.forEach((key, ids) -> {
            int sep = key.indexOf('\n');
            prunePanelRefs(siteId, key.substring(0, sep), key.substring(sep + 1), ids);
        });
        archived.forEach(panel -> placementService.delete(panel.getId()));
        cacheSync.invalidateAll("page-region");
        return archived.size();
    }

    /**
     * 清理悬空引用（健康页显式修复动作，绝不启动自愈）：剔除全站结构树（已发布 + 草稿）中
     * 指向已不存在投放行的面板引用。仅删纯垃圾——归档面板的行仍存活，其引用保留（恢复即渲染）。
     *
     * @return 剔除的引用数
     */
    @Transactional(rollbackFor = Exception.class)
    public int pruneDanglingRefs(Long siteId) {
        Set<String> liveIds = new LinkedHashSet<>();
        for (Placement panel : placementService.listBySiteId(siteId)) {
            liveIds.add(String.valueOf(panel.getId()));
        }
        int pruned = 0;
        for (PageRegion row : mapper.listBySiteId(siteId)) {
            pruned += pruneRow(row, id -> !liveIds.contains(id));
        }
        if (pruned > 0) {
            cacheSync.invalidateAll("page-region");
        }
        return pruned;
    }

    /**
     * 从一个区域的全部范围行剔除指定面板引用（各范围行共用投放池，引用须跨全范围剔除）。
     */
    private void prunePanelRefs(Long siteId, String pageRef, String regionName, Set<String> panelIds) {
        for (PageRegion row : mapper.listByRegion(siteId, pageRef, regionName)) {
            pruneRow(row, panelIds::contains);
        }
    }

    /**
     * 从单个范围行的已发布树与草稿树剔除满足条件的面板引用；有变更则乐观锁写回（版本 +1，
     * 打开中的编辑器随下次保存冲突刷新）。
     *
     * @return 剔除的引用数
     */
    private int pruneRow(PageRegion row, java.util.function.Predicate<String> shouldRemove) {
        RegionLayout published = RegionLayout.parse(row.getLayoutJson());
        RegionLayout prunedPublished = removePanelRefs(published, shouldRemove);
        int pruned = published.panelIds().size() - prunedPublished.panelIds().size();
        boolean changed = pruned > 0;
        if (changed) {
            row.setLayoutJson(prunedPublished.toJson());
        }
        if (row.isDraftPresent()) {
            RegionLayout draft = RegionLayout.parse(row.getDraftLayoutJson());
            RegionLayout prunedDraft = removePanelRefs(draft, shouldRemove);
            int draftPruned = draft.panelIds().size() - prunedDraft.panelIds().size();
            if (draftPruned > 0) {
                row.setDraftLayoutJson(prunedDraft.toJson());
                pruned += draftPruned;
                changed = true;
            }
        }
        if (changed) {
            row.setModified(OffsetDateTime.now());
            updateWithLockOr409(row, row.getVersion());
        }
        return pruned;
    }

    /**
     * 树变换：剔除满足条件的面板引用，行/列结构原样保留（空列合法，渲染为空）。
     */
    private static RegionLayout removePanelRefs(RegionLayout layout, java.util.function.Predicate<String> shouldRemove) {
        List<RegionLayout.Row> rows = new ArrayList<>();
        for (RegionLayout.Row row : layout.rows()) {
            List<RegionLayout.Column> columns = new ArrayList<>();
            for (RegionLayout.Column column : row.columns()) {
                columns.add(new RegionLayout.Column(
                        column.panels().stream().filter(id -> !shouldRemove.test(id)).toList()));
            }
            rows.add(new RegionLayout.Row(row.id(), row.layout(), columns));
        }
        return new RegionLayout(rows);
    }

    // ---------------------------------------------------------------------------------------------
    // 发布快照历史（region-history / design-review P1-1）与发布门（P1-4）
    // ---------------------------------------------------------------------------------------------

    /**
     * 物化布局树为自包含快照内容（region-history §4）：面板ID引用展开为面板内容
     * （shape/settings/enabled）。悬空引用丢弃（与渲染口径一致）；不含面板ID——面板池会被孤立清理
     * 与就地编辑改写，恢复始终复制建新投放。
     */
    private SnapshotContent materialize(Long siteId, RegionLayout layout) {
        Map<Long, Placement> panelsById = loadPanels(siteId, layout);
        List<SnapshotRow> rows = new ArrayList<>(layout.rows().size());
        for (RegionLayout.Row row : layout.rows()) {
            List<SnapshotColumn> columns = new ArrayList<>(row.columns().size());
            for (RegionLayout.Column column : row.columns()) {
                List<SnapshotPanel> panels = new ArrayList<>(column.panels().size());
                for (String panelId : column.panels()) {
                    Long pid = parseId(panelId);
                    Placement placement = pid != null ? panelsById.get(pid) : null;
                    if (placement == null || placement.getPanelShape() == null) {
                        continue;
                    }
                    panels.add(new SnapshotPanel(placement.getPanelShape(), placement.getSettings(),
                            placement.getEnabled() == null || placement.getEnabled(),
                            placement.getVisibility()));
                }
                columns.add(new SnapshotColumn(panels));
            }
            rows.add(new SnapshotRow(row.id(), row.layout(), columns));
        }
        return new SnapshotContent(rows);
    }

    /**
     * 追加快照（与坐标下最新条目内容语义相同则去重跳过——MySQL JSON 列会规范化键序，须按解析树比较）。
     */
    private void appendSnapshotIfDrifted(PageRegion region, SnapshotContent content, String source,
                                         @Nullable String actor, @Nullable String label,
                                         @Nullable Long restoredFrom) {
        String json = content.toJson();
        PageRegionSnapshot latest = latestSnapshot(region.getSiteId(), region.getPageRef(),
                region.getRegionName(), region.getScope());
        if (latest != null && jsonEquals(latest.getContentJson(), json)) {
            return;
        }
        PageRegionSnapshot snap = new PageRegionSnapshot();
        snap.setId(snowflakeSequence.nextId());
        snap.setSiteId(region.getSiteId());
        snap.setPageRef(region.getPageRef());
        snap.setRegionName(region.getRegionName());
        snap.setScope(region.getScope());
        snap.setContentJson(json);
        snap.setRowsCount(content.rows().size());
        snap.setPanelsCount(content.panelCount());
        snap.setSource(source);
        snap.setRestoredFrom(restoredFrom);
        snap.setActor(actor);
        snap.setLabel(label);
        snap.setCreated(OffsetDateTime.now());
        snapshotMapper.insert(snap);
    }

    /**
     * 坐标下最新一条快照（回填漂移检测 / 追加去重用）。整列点查——行数受保留上限约束（≤50 条 KB 级行），
     * 无需分页方言。
     */
    @Nullable
    private PageRegionSnapshot latestSnapshot(Long siteId, String pageRef, String regionName,
                                              String scope) {
        List<PageRegionSnapshot> list = snapshotMapper.listByCoordinates(siteId, pageRef, regionName, scope);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按保留上限裁剪坐标下的历史（新→旧保留前 {@link #HISTORY_RETENTION} 条；裁剪只削最旧，
     * 回填漂移检测依赖的最新条目不受影响）。
     */
    private void trimHistory(Long siteId, String pageRef, String regionName, String scope) {
        List<PageRegionSnapshot> list = snapshotMapper.listByCoordinates(siteId, pageRef, regionName, scope);
        for (int i = HISTORY_RETENTION; i < list.size(); i++) {
            snapshotMapper.delete(list.get(i).getId());
        }
    }

    /**
     * 快照内容 → 草稿保存输入（恢复用；面板ID为空——始终复制建新投放）。
     */
    private static List<RowInput> toRowInputs(SnapshotContent content) {
        List<RowInput> rows = new ArrayList<>(content.rows().size());
        for (SnapshotRow row : content.rows()) {
            List<ColumnInput> columns = new ArrayList<>(row.columns().size());
            for (SnapshotColumn column : row.columns()) {
                List<PanelInput> panels = new ArrayList<>(column.panels().size());
                for (SnapshotPanel panel : column.panels()) {
                    panels.add(new PanelInput(null, panel.shape(), panel.settings(), panel.enabled(),
                            panel.visibility()));
                }
                columns.add(new ColumnInput(panels));
            }
            rows.add(new RowInput(row.id(), row.layout(), columns));
        }
        return rows;
    }

    /**
     * JSON 语义相等（对象键序不敏感——MySQL JSON 列存取会规范化键序，字符串比较必假阴性）。
     */
    private static boolean jsonEquals(String a, String b) {
        try {
            return MAPPER.readTree(a).equals(MAPPER.readTree(b));
        } catch (JacksonException e) {
            return false;
        }
    }

    /**
     * 发布门：必填设置校验（design-review P1-4 / B3）。遍历草稿树的<b>启用</b>面板，按当前轨道命名空间链
     * 解析组件，凡声明 {@code required} 的设置值为空（{@link FieldValueValidator#isEmpty} 口径；声明
     * {@code default} 亦不豁免——required 语义为「操作员必须显式设置」，想要静默回退就只声明 default）
     * → 400 指名行列/面板/字段。悬空引用与不可解析组件跳过（前者渲染即丢弃，后者由健康报告呈现，
     * 发布它渲染的是占位而非静默缺省内容）。按设备隐藏（{@code visibility}，P2-5）<b>不豁免</b>——
     * 仅桌面/仅移动的面板仍在另一设备类别发布渲染，必填门照常适用；唯 {@code enabled=false} 豁免。
     */
    private void requireRequiredSettings(Long siteId, RegionLayout draft,
                                         Collection<String> themeKeys) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        Map<Long, Placement> panelsById = loadPanels(siteId, draft);
        List<String> errors = new ArrayList<>();
        List<RegionLayout.Row> rows = draft.rows();
        for (int r = 0; r < rows.size(); r++) {
            List<RegionLayout.Column> columns = rows.get(r).columns();
            for (int c = 0; c < columns.size(); c++) {
                for (String panelId : columns.get(c).panels()) {
                    Long pid = parseId(panelId);
                    Placement panel = pid != null ? panelsById.get(pid) : null;
                    String shapeName = panel != null ? panel.getPanelShape() : null;
                    if (panel == null || shapeName == null || Boolean.FALSE.equals(panel.getEnabled())) {
                        continue;
                    }
                    Shape shape = snapshot.resolveTrackShape(themeKeys, shapeName);
                    if (shape == null) {
                        continue;
                    }
                    String where = String.format("第%d行第%d列 面板[%s]", r + 1, c + 1,
                            displayLabel(shape.getLabel(), shape.getName()));
                    Map<String, Object> settings = panel.getSettings();
                    shape.getSettings().forEach((name, def) -> {
                        if (def.isRequired() && FieldValueValidator.isEmpty(settings.get(name))) {
                            errors.add(where + " 设置[" + fieldLabel(def, name) + "]");
                        }
                    });
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new Http400Exception("发布被阻止，必填设置为空: " + String.join("; ", errors));
        }
    }

    /**
     * 组件/字段显示名（zh-CN 回退声明名；错误信息与后台约定一致用中文）。
     */
    private static String displayLabel(Map<String, String> label, String fallback) {
        String zh = label.get("zh-CN");
        return zh != null && !zh.isBlank() ? zh : fallback;
    }

    private static String fieldLabel(FieldDef def, String name) {
        Object zh = def.getMap("label").get("zh-CN");
        return zh instanceof String str && !str.isBlank() ? str : name;
    }

    /**
     * 面板设置保存前处理（component-settings §3）：按组件声明的设置结构拒绝未声明键、逐值按类型校验
     * 并做保存期处理（{@code html} 字段 XSS 清理——与卡片数据同一套字段管线）。必填性刻意不在保存期
     * 强制（调色板落面板以空设置实例化，渲染期以缺省值补齐）。组件不可解析（§5.4 该轨道缺失）时无从
     * 校验：设置为空放行（旧面板重排重提交），非空拒绝——不落无法清洗的自由数据。
     */
    private Map<String, Object> sanitizeSettings(@Nullable Shape shape, String shapeName,
                                                 Map<String, Object> settings) {
        if (shape == null) {
            if (!settings.isEmpty()) {
                throw new Http400Exception("组件不可解析，无法校验设置: " + shapeName);
            }
            return settings;
        }
        Map<String, FieldDef> defs = shape.getSettings();
        List<String> errors = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>(settings.size());
        settings.forEach((name, value) -> {
            FieldDef def = defs.get(name);
            if (def == null) {
                errors.add(name + ": 设置未在组件中声明");
                return;
            }
            if (value == null || (value instanceof String str && str.isBlank())) {
                result.put(name, value);
                return;
            }
            List<String> valueErrors = fieldValueValidator.validateValue(value, def);
            if (!valueErrors.isEmpty()) {
                valueErrors.forEach(message -> errors.add(name + ": " + message));
                return;
            }
            result.put(name, fieldValueValidator.processValueOnSave(value, def));
        });
        if (!errors.isEmpty()) {
            throw new Http400Exception("面板设置非法: " + String.join("; ", errors));
        }
        return result;
    }

    /**
     * 本区域的全部面板投放（{@code slot_name_ = region_name_}、{@code panel_shape_} 非空以区分经典投放）。
     */
    private List<Placement> listRegionPanels(Long siteId, String pageRef, String regionName) {
        return placementService.listByPage(siteId, pageRef).stream()
                .filter(p -> regionName.equals(p.getSlotName()) && p.getPanelShape() != null)
                .toList();
    }

    private PageRegion require(Long siteId, String pageRef, String regionName, String scope) {
        PageRegion region = mapper.selectByUnique(siteId, pageRef, regionName, scope);
        if (region == null) {
            throw new Http400Exception("Region not found: " + pageRef + "/" + regionName
                    + "/" + scope);
        }
        return region;
    }

    @Nullable
    private SlotDef findRegionSlotDef(RegistrySnapshot snapshot, String pageRef, String regionName,
                                      String themeKey) {
        PageManifest manifest = snapshot.getThemeManifest(themeKey, pageRef);
        if (manifest == null) {
            return null;
        }
        return manifest.getSlots().get(regionName);
    }

    /**
     * 解析区域 SlotDef；遍历站点活动主题清单（任一声明即可）。
     */
    private RegionMatch requireRegionSlotDef(String pageRef, String regionName,
                                             Collection<String> themeKeys) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        for (String themeKey : themeKeys) {
            SlotDef slot = findRegionSlotDef(snapshot, pageRef, regionName, themeKey);
            if (slot != null) {
                return new RegionMatch(themeKey, slot);
            }
        }
        throw new Http400Exception("Region not declared: " + pageRef + "/" + regionName);
    }

    /**
     * 共享区域宿主页仅有默认范围（§15）：宿主坐标上的频道/变体覆盖行永不被解析（跟随页的候选链只回退到
     * 宿主<b>默认</b>行），拒绝建立死状态。
     */
    private void requireScopeAllowedOnOwner(String pageRef, String regionName, String scope,
                                            Collection<String> themeKeys) {
        if (PageRegion.SCOPE_DEFAULT.equals(scope)) {
            return;
        }
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        for (String themeKey : themeKeys) {
            Map<String, PageManifest> manifests = snapshot.getThemeManifests().get(themeKey);
            if (manifests == null) {
                continue;
            }
            for (PageManifest manifest : manifests.values()) {
                SlotDef slot = manifest.getSlots().get(regionName);
                if (slot != null && pageRef.equals(slot.getSharedFrom())) {
                    throw new Http400Exception("共享区域宿主页仅有默认范围: " + pageRef + "/" + regionName);
                }
            }
        }
    }

    private void validateLayoutSchema(String json) {
        try {
            List<String> errors = schemas.validate(CompositionSchemas.PAGE_REGION_LAYOUT, MAPPER.readTree(json));
            if (!errors.isEmpty()) {
                throw new Http400Exception("区域布局树不符合 Schema: " + String.join("; ", errors));
            }
        } catch (JacksonException e) {
            throw new Http400Exception("区域布局树 JSON 非法");
        }
    }

    private static RegionLayout toLayout(List<RowInput> rowInputs, boolean placeholderIds) {
        List<RegionLayout.Row> rows = new ArrayList<>(rowInputs.size());
        for (RowInput rowInput : rowInputs) {
            List<RegionLayout.Column> columns = new ArrayList<>(rowInput.columns().size());
            for (ColumnInput columnInput : rowInput.columns()) {
                List<String> ids = new ArrayList<>(columnInput.panels().size());
                for (int i = 0; i < columnInput.panels().size(); i++) {
                    ids.add(placeholderIds ? "_" : "");
                }
                columns.add(new RegionLayout.Column(ids));
            }
            rows.add(new RegionLayout.Row(rowInput.id(), rowInput.layout(), columns));
        }
        return new RegionLayout(rows);
    }

    @Nullable
    private static Long parseId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record RegionMatch(@Nullable String themeKey, SlotDef slotDef) {
    }

    // ---------------------------------------------------------------------------------------------
    // 输入 / 输出结构（控制器据此绑定）
    // ---------------------------------------------------------------------------------------------

    /**
     * 草稿保存——行输入
     */
    public record RowInput(String id, String layout, List<ColumnInput> columns) {
        public RowInput {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    /**
     * 草稿保存——列输入
     */
    public record ColumnInput(List<PanelInput> panels) {
        public ColumnInput {
            panels = panels == null ? List.of() : List.copyOf(panels);
        }
    }

    /**
     * 草稿保存——面板输入 v2（component-settings §3：面板 = 组件实例，一个设置值块）
     *
     * @param id         已存在面板投放ID（新增为空）
     * @param shape      组件（Shape）名（须在区域 allowedShapes 内）
     * @param settings   设置值（按组件声明的设置结构；缺省空——渲染期以设置缺省值补齐）
     * @param enabled    是否启用（缺省 true）
     * @param visibility 显示设备（design-review P2-5：{@code all}/{@code desktop}/{@code mobile}；
     *                   缺省 {@code all}——非法值由 {@code PlacementService.savePanel} 以 400 拒绝）
     */
    public record PanelInput(@Nullable Long id, String shape, @Nullable Map<String, Object> settings,
                             @Nullable Boolean enabled, @Nullable String visibility) {
        /**
         * 剥离面板ID的副本（创建覆盖行的首次保存用，visual-builder §9）：以同配置新建投放，
         * 而非改写默认树的既有面板。
         */
        public PanelInput withoutId() {
            return id == null ? this : new PanelInput(null, shape, settings, enabled, visibility);
        }

        public Placement toBean(@Nullable Long siteId, String pageRef, String regionName) {
            Placement bean = new Placement();
            if (id != null && id != 0L) {
                bean.setId(id);
            }
            bean.setSiteId(siteId);
            bean.setPageRef(pageRef);
            bean.setSlotName(regionName);
            bean.setPanelShape(shape);
            bean.setSettings(settings != null ? new LinkedHashMap<>(settings) : new LinkedHashMap<>(2));
            bean.setEnabled(enabled == null || enabled);
            bean.setVisibility(visibility == null || visibility.isBlank() ? Placement.VISIBILITY_ALL : visibility);
            return bean;
        }
    }

    /**
     * 草稿保存结果（visual-builder §6）：新版本号 + 按遍历序（行 → 列 → 面板）的真实面板ID，
     * 供构建器逐操作串联（乐观锁版本链）而无需回读详情。
     *
     * @param version 保存后的乐观锁版本号
     * @param panels  真实面板引用（与提交的行/列/面板遍历序一一对应）
     */
    public record SaveDraftResult(int version, List<PanelRef> panels) {
    }

    /**
     * 面板引用（草稿保存结果）
     *
     * @param id    面板投放ID（雪花ID，字符串以免 JS 精度丢失）
     * @param shape 面板 Shape
     */
    public record PanelRef(String id, String shape) {
    }

    /**
     * 区域目录行（页面目录入口，§9）
     *
     * @param page          页面清单名
     * @param region        区域名
     * @param pageLabel     页面显示名（按 locale）
     * @param label         区域显示名（按 locale；清单 label，未声明为空映射）
     * @param description   区域描述（未声明为 null）
     * @param allowedShapes 区域允许的面板 Shape
     * @param publishedRows 已发布树的行数（0 表示尚未配置；共享区域未分叉时为宿主行）
     * @param hasDraft      是否有未发布草稿
     * @param sharedFrom    共享区域的宿主页名（page-regions §15；自有区域为 {@code null}）
     * @param forked        共享区域是否已在本页分叉（本页存在自有默认行）
     */
    public record RegionSummary(String page, String region, Map<String, String> pageLabel,
                                Map<String, String> label, @Nullable String description,
                                List<String> allowedShapes, int publishedRows, boolean hasDraft,
                                @Nullable String sharedFrom, boolean forked) {
    }

    /**
     * 调色板条目（visual-builder §7.1，页面聚合详情用）
     *
     * @param name         Shape 名
     * @param version      版本（missing 时为空串）
     * @param label        按 locale 显示名
     * @param description  描述
     * @param tags         标签（presentation.group 缺省时第一个 tag 作调色板分组回退）
     * @param source       来源命名空间（core / theme:xxx；missing 时为空串）
     * @param presentation 呈现元数据（icon / group / order / thumbnail 原始值；未声明为空 Map）
     * @param thumbnail    服务端解析后的缩略图 URL（P6/D11：data URI 直通 / 核心静态前缀 /
     *                     主题模板存储公开前缀；不可解析或未声明为 {@code null}，控制台回退 icon）
     * @param missing      该轨道命名空间无法解析（§5.4 该形状在此轨道缺失；控制台置灰）
     */
    public record PaletteShape(String name, String version, Map<String, String> label,
                               @Nullable String description, List<String> tags, String source,
                               Map<String, Object> presentation, @Nullable String thumbnail, boolean missing) {
    }

    /**
     * 区域详情 v2（编辑器，component-settings §3：面板配置 = 组件设置值，装饰字段/来源类型退役）
     *
     * @param page              详情实际对应的页面坐标（共享区域未分叉时为宿主页名，page-regions §15；
     *                          控制台以此为后续读写坐标）
     * @param region            区域名
     * @param scope             区域范围（visual-builder §9：{@code default} / 覆盖范围值）
     * @param overrideExists    该范围行是否已存在（覆盖范围尚未创建时为 {@code false}，此时
     *                          {@code published} 为页面默认树的复制种子、{@code version} 为 0）
     * @param version           乐观锁版本（无区域行时为 0）
     * @param published         已发布结构树（覆盖行缺失时为页面默认行的已发布树）
     * @param draft             草稿结构树（无草稿为 null）
     * @param allowedShapes     区域允许的面板组件（Shape）
     * @param allowedRowLayouts 区域允许的行布局键
     * @param maxRows           行数上限（null 不限）
     * @param maxPanelsPerColumn 单列面板数上限（null 不限）
     * @param label             区域显示名（按 locale；清单 label，未声明为空映射）
     * @param description       区域描述（未声明为 null）
     * @param panels            本区域全部面板（投放池全量，含各范围行引用的面板；按ID索引）
     * @param sharedFrom        共享区域的宿主页名（§15；自有区域为 {@code null}）
     * @param forked            共享区域是否已在请求页分叉（请求页存在自有默认行）
     */
    public record RegionDetail(String page, String region, String scope, boolean overrideExists, int version,
                               RegionLayout published,
                               @Nullable RegionLayout draft, List<String> allowedShapes,
                               List<String> allowedRowLayouts,
                               @Nullable Integer maxRows, @Nullable Integer maxPanelsPerColumn,
                               Map<String, String> label, @Nullable String description,
                               List<PanelDetail> panels, @Nullable String sharedFrom, boolean forked) {
        /**
         * 以宿主页详情为底、标注共享来源（跟随页未分叉时的详情形态）。
         */
        RegionDetail withSharing(String owner, boolean forkedOnRequestPage) {
            return new RegionDetail(page, region, scope, overrideExists, version, published, draft,
                    allowedShapes, allowedRowLayouts, maxRows, maxPanelsPerColumn, label, description,
                    panels, owner, forkedOnRequestPage);
        }
    }

    /**
     * 面板详情 v2（编辑器）：组件实例 = 组件名 + 设置值 + 启用/显示设备。
     */
    public record PanelDetail(String id, String shape, Map<String, Object> settings, boolean enabled,
                              String visibility) {
        public static PanelDetail of(Placement bean) {
            return new PanelDetail(String.valueOf(bean.getId()), bean.getPanelShape(), bean.getSettings(),
                    bean.getEnabled(), bean.getVisibility());
        }
    }

    /**
     * 快照内容（region-history §4）：物化发布树——与 {@link RegionLayout} 同构，但面板ID引用展开为
     * 面板内容（自包含；不含面板ID，恢复始终复制建新投放）。
     */
    public record SnapshotContent(List<SnapshotRow> rows) {
        public SnapshotContent {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        /**
         * 解析快照内容 JSON。结构非法抛 {@link IllegalArgumentException}。
         */
        public static SnapshotContent parse(String json) {
            try {
                SnapshotContent content = MAPPER.readValue(json, SnapshotContent.class);
                return content != null ? content : new SnapshotContent(List.of());
            } catch (JacksonException e) {
                throw new IllegalArgumentException("Invalid region snapshot json", e);
            }
        }

        public String toJson() {
            try {
                return MAPPER.writeValueAsString(this);
            } catch (JacksonException e) {
                throw new IllegalStateException("Cannot serialize region snapshot", e);
            }
        }

        /**
         * 面板总数（列表展示的冗余计数）。
         */
        public int panelCount() {
            int count = 0;
            for (SnapshotRow row : rows) {
                for (SnapshotColumn column : row.columns()) {
                    count += column.panels().size();
                }
            }
            return count;
        }
    }

    /**
     * 快照内容——行（行ID/行布局键与 {@link RegionLayout.Row} 同义，恢复时原样复用）
     */
    public record SnapshotRow(String id, String layout, List<SnapshotColumn> columns) {
        public SnapshotRow {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    /**
     * 快照内容——列
     */
    public record SnapshotColumn(List<SnapshotPanel> panels) {
        public SnapshotColumn {
            panels = panels == null ? List.of() : List.copyOf(panels);
        }
    }

    /**
     * 快照内容——面板（组件实例：组件名 + 设置值 + 是否启用 + 显示设备；{@code enabled} 空按启用——
     * 写入时恒有值，宽容解析）。{@code visibility} 的规范形是「缺省不落键」：{@code all} 归一为
     * {@code null} 且序列化省略——未标记面板的快照 JSON 与特性上线前逐字节一致，发布去重 / 回填
     * 漂移检测跨版本不受扰（同 {@code SeedHash} 的投影口径）。
     */
    public record SnapshotPanel(String shape, Map<String, Object> settings, @Nullable Boolean enabled,
                                @com.fasterxml.jackson.annotation.JsonInclude(
                                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                                @Nullable String visibility) {
        public SnapshotPanel {
            settings = settings == null ? Map.of() : new LinkedHashMap<>(settings);
            String normalized = Placement.normalizeVisibility(visibility);
            visibility = Placement.VISIBILITY_ALL.equals(normalized) ? null : normalized;
        }
    }
}
