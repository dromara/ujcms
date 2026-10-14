package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.ujcms.cms.core.composition.PageManifest.SlotDef;
import com.ujcms.cms.core.composition.SeedPlan.CategoryPlan;
import com.ujcms.cms.core.composition.SeedPlan.Collision;
import com.ujcms.cms.core.composition.SeedTokenExpander.SeedTokenException;
import com.ujcms.cms.core.composition.ThemeContentSeedImporter.Failure;
import com.ujcms.cms.core.domain.PageRegion;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.PlacementMapper;
import com.ujcms.cms.ext.mapper.VoteMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 主题种子导入器（§8.3 / §12；page-regions §10）。把主题包 {@code seed/regions/*.regions.json}
 * 导入为带来源标记（{@code origin = theme:<themeName>}）的页面区域与面板投放
 * （设置驱动面板，component-settings §4.3；卡片/经典投放种子已随其子系统于 Phase D 退役）。
 *
 * <p><b>幂等</b>：面板投放按 {@code (origin, originId)} select-then-insert（§6.3）——重复导入不产生重复行；
 * originId 合成为 {@code <page>#<region>#<rowId>#<col>#<panel>}（page-regions §10）。
 * 区域行无 {@code origin_id_} 列，而 {@code (site, page, region)} 本就是唯一键，故区域按该唯一键幂等
 * （已存在即跳过，{@code origin_} 仅记录来源）。
 * <b>非破坏</b>：目标 {@code (site, page, region)} 已有区域行（操作员编排或旧种子）则结构树默认整块跳过，
 * 不覆盖操作员改动；其本源面板内容按覆盖策略刷新（§6.1 / A3）。<b>结构重建例外</b>（D22）：
 * 本主题本源区域行在 {@code resetAllOrigin} 下无条件、在 {@code syncUnmodified} 下当散列证明结构
 * 未被操作员改动且种子结构有变时，整体重建为种子默认（结构 + 面板，含重建已删除的种子面板）——
 * 重建前草稿与被替换的已发布树先回填快照（region-history），整次重置可经构建器发布历史逐区域恢复；
 * 操作员区域行（无来源标记）在任何策略下结构不动（§6.1 安全不变量 1）；<b>他主题本源</b>区域行
 * （{@code origin_ = theme:<other>}，上一主题演示布局的遗留——区域坐标无主题维度）仅在
 * {@code resetAllOrigin} 下、或散列证明其未被操作员改动时在任何策略下，同样整体重建并改记本主题
 * 来源（D23）；改过的他主题布局只有 {@code resetAllOrigin} 才替换。
 * <b>资源前缀</b>：面板设置中的 {@code {{theme}}} 在导入时展开为主题资源 URL 前缀
 * （{@code <templateStorageUrl>/<siteId>/<themeName>}，§8.3）；{@code {{asset:<relpath>}}}
 * 同内容种子一样经 {@link SeedAssetCopier} 展开（theme-seed-export E1：导出器把面板设置里的
 * 本站上传媒体改写为 asset 令牌——内容媒体必须活得比主题目录久，不能进 {{theme}} 前缀）。
 *
 * <p><b>插槽归属</b>（page-regions §10）：导入器按本主题清单解析区域 {@link SlotDef}，
 * 主题包只是种子的载体。区域插槽未声明则跳过该区域并告警，不使整个主题安装失败。
 *
 * <p><b>引用令牌降级</b>（theme-seed-export R2 通道对构成的延伸，2026-07-27）：面板设置的
 * {@code {{channel:…}}} 等引用令牌不可解析（白板站点 {@code seedScope=composition} 的常态）时
 * <b>整块跳过所在区域</b>并记行级失败明细（{@link Failure}，类别 {@code panels}）——与未声明插槽
 * 同法以区域为跳过单元：区域行一旦落库，默认策略下重导既不覆盖结构也不凭空重建面板，
 * 面板级跳过会留下无从续传的半区域；整块跳过则建目标内容（或补内容种子）后重导即整块补齐。
 * 结构重建路径（D22）同口径：缺失面板的令牌不可解析即整块放弃重建（落回内容刷新）。
 * 刷新路径（{@link SeedOverwrite}）无结构含义，降级单元为单面板（旧内容保留）。异常绝不逃逸
 * 为整次导入失败（HTTP 500）。
 *
 * <p><b>只填空下的失效引用修复</b>（theme-marketplace D24）：面板展开时把 {@code {{channel:…}}} 等令牌
 * 钉成裸ID，而 移除演示数据 物理删除目标、重导入以新雪花ID重建——既有本源面板的引用自此失联。
 * 只填空对既有行「一律跳过」的例外：R4 散列<b>在场且复现</b>（操作员从未动过）的本主题本源面板，
 * 其引用经 {@link SettingsResolver#staleRefs}（与健康页同一口径）判定失联时按种子整体刷新
 * （{@link #repairStaleRefPanels}）——未动过的行上的死引用是空缺而非操作员成果，与 D23 对他主题
 * 区域行的散列证明同一法则。
 *
 * @author PONY
 */
@Component
public class ThemeSeedImporter {
    private static final Logger logger = LoggerFactory.getLogger(ThemeSeedImporter.class);

    private static final String SEED_DIR = "seed";
    private static final String REGIONS_DIR = "regions";
    private static final String CONTENT_DIR = "content";
    private static final String ASSETS_DIR = "assets";
    private static final String REGIONS_SUFFIX = ".regions.json";
    /**
     * 来源标记前缀（不含版本——保证跨版本/重装幂等，见决策日志）
     */
    public static final String ORIGIN_PREFIX = "theme:";

    private final PlacementMapper placementMapper;
    private final PageRegionMapper pageRegionMapper;
    private final PlacementService placementService;
    private final RegionService regionService;
    private final ThemeSettingsService themeSettingsService;
    private final ManifestService manifestService;
    private final SettingsResolver settingsResolver;
    private final CompositionSchemas schemas;
    private final SnowflakeSequence snowflakeSequence;
    private final SeedAssetCopier assetCopier;
    /**
     * 面板设置的令牌展开（R1 起与修复动作共用同一遍历，严格模式）
     */
    private final SeedTokenExpander tokenExpander;

    public ThemeSeedImporter(PlacementMapper placementMapper, PageRegionMapper pageRegionMapper,
                             ChannelMapper channelMapper, ArticleMapper articleMapper, BlockMapper blockMapper,
                             VoteMapper voteMapper,
                             PlacementService placementService, RegionService regionService,
                             ThemeSettingsService themeSettingsService,
                             ManifestService manifestService, SettingsResolver settingsResolver,
                             CompositionSchemas schemas,
                             SnowflakeSequence snowflakeSequence, SeedAssetCopier assetCopier) {
        this.placementMapper = placementMapper;
        this.pageRegionMapper = pageRegionMapper;
        this.placementService = placementService;
        this.regionService = regionService;
        this.themeSettingsService = themeSettingsService;
        this.manifestService = manifestService;
        this.settingsResolver = settingsResolver;
        this.schemas = schemas;
        this.snowflakeSequence = snowflakeSequence;
        this.assetCopier = assetCopier;
        this.tokenExpander = new SeedTokenExpander(channelMapper, articleMapper, blockMapper, voteMapper);
    }

    /**
     * 来源标记（{@code theme:<themeName>}）。
     */
    public static String originOf(String themeName) {
        return ORIGIN_PREFIX + themeName;
    }

    /**
     * 导入主题的全部构成种子（区域 + 面板）。无 {@code seed/} 目录则为空结果。导入后标记
     * {@code ujcms_theme_state.seed_imported_ = true}。
     *
     * @param siteId           站点ID
     * @param themeName        主题名（目录名）
     * @param themeDir         主题目录
     * @param templateStoreUrl 模板存储 URL 前缀（{@code Config.templateStorage.url}）
     * @param userId           操作员ID（保留参数：内容种子导入器同签名）
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public SeedImportResult importSeed(Long siteId, String themeName, File themeDir, String templateStoreUrl,
                                       @Nullable Long userId) {
        return importSeed(siteId, themeName, themeDir, templateStoreUrl, userId, SeedOverwrite.FILL_ONLY);
    }

    /**
     * 导入主题的全部构成种子（带覆盖策略，§6.1 / A3）。既有本源行按 {@link SeedOverwrite} 处理：
     * {@code fillOnly} 跳过（唯一例外：散列证明未动过的本源面板的失效引用按种子修复，D24）；
     * {@code syncUnmodified} 仅刷新「操作员未改动」的行（R4 散列判定）；
     * {@code resetAllOrigin} 无条件重置。刷新覆盖种子形投影（面板 settings/enabled），
     * 保留行ID/created；区域<b>结构树</b>在 {@code fillOnly} 下只填空（操作员编排优先——结构重排走
     * 可视化构建器），在 {@code resetAllOrigin} 下整体重建为种子默认（含重建已删除的种子面板；
     * 重建前回填快照，可经构建器发布历史恢复），在 {@code syncUnmodified} 下仅当散列证明结构未被
     * 操作员改动且种子结构有变时重建（D22）。非本源行（操作员行）在任何策略下都不被触碰（安全不变量 1）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SeedImportResult importSeed(Long siteId, String themeName, File themeDir, String templateStoreUrl,
                                       @Nullable Long userId, SeedOverwrite overwrite) {
        String origin = originOf(themeName);
        String themeKey = ThemeSettingsService.themeKey(siteId, themeName);
        String assetPrefix = stripTrailingSlash(templateStoreUrl) + "/" + siteId + "/" + themeName;
        // {{asset:}} 令牌与内容种子同源同路径展开（theme-seed-export E1：导出器产出的面板设置可携带）
        File contentAssetsDir = new File(new File(new File(themeDir, SEED_DIR), CONTENT_DIR), ASSETS_DIR);
        SeedAssetCopier.Session assets = assetCopier.open(siteId, themeName, contentAssetsDir,
                userId != null ? userId : User.ANONYMOUS_ID);

        File regionsDir = new File(new File(themeDir, SEED_DIR), REGIONS_DIR);

        Tally tally = new Tally();
        // 区域（page-regions §10）：结构树 + 面板投放
        for (File file : listFiles(regionsDir, REGIONS_SUFFIX)) {
            SeedRegionFile regionFile = parseRegionFile(file);
            for (SeedRegion seed : regionFile.regions()) {
                importRegion(siteId, themeKey, regionFile.page(), seed, origin, assetPrefix, assets,
                        file.getPath(), overwrite, tally);
            }
        }

        // 标记 ujcms_theme_state.seed_imported_ = true（懒建行）
        themeSettingsService.markSeedImported(siteId, themeName);

        SeedImportResult result = tally.toResult();
        logger.info("Theme seed imported: themeKey={}, overwrite={}, {}", themeKey, overwrite, result);
        return result;
    }

    /**
     * R4 单一脏检测：行「操作员未改动」当且仅当当前行投影散列复现 {@code origin_hash_}；
     * 散列缺失视作未改（无从判定，宁可少报）。
     */
    private static boolean isUnmodified(@Nullable String storedHash, String rowHash) {
        return storedHash == null || storedHash.equals(rowHash);
    }

    /**
     * 导入计数器（SeedImportResult 的可变累加形）
     */
    private static final class Tally {
        int panelsImported;
        int panelsSkipped;
        int panelsUpdated;
        int panelsRepaired;
        int regionsImported;
        int regionsReset;
        int regionsSkipped;
        final List<String> undeclaredSlots = new ArrayList<>();
        final List<String> sharedSlots = new ArrayList<>();
        final List<Failure> failures = new ArrayList<>();

        /**
         * 记录一次面板级失败（R2 通道，与内容导入器 {@link Failure} 同形；上限同享
         * {@link Failure#MAX_DETAILS}，计数以 {@code regionsSkipped}/{@code panelsSkipped} 为准）。
         */
        void failPanel(String panelOriginId, String detail) {
            if (failures.size() < Failure.MAX_DETAILS) {
                failures.add(new Failure(Failure.CAT_PANELS, panelOriginId,
                        Failure.REASON_PANEL_TOKEN_UNRESOLVED, detail));
            }
        }

        SeedImportResult toResult() {
            return new SeedImportResult(panelsImported, panelsSkipped, panelsUpdated, panelsRepaired,
                    regionsImported, regionsReset, regionsSkipped,
                    List.copyOf(undeclaredSlots), List.copyOf(sharedSlots), List.copyOf(failures));
        }
    }

    /**
     * 落面板投放来源散列（R4）。在服务层保存之后按 bean 最终状态计算。
     */
    private void stampPlacementHash(Placement placement) {
        placementMapper.updateOriginHash(placement.getId(), SeedHash.placementHash(placement));
    }

    // ----- regions (page-regions §10) -----

    /**
     * 导入一个种子区域：建面板投放 + 结构树，写 {@code ujcms_page_region}（直接发布，无草稿）。
     * 幂等 + 非破坏：目标 {@code (site, page, region)} 已有区域行则<b>结构树</b>默认整块跳过
     * ——但其本源<b>面板</b>内容仍按覆盖策略刷新（§6.1 / A3：结构属操作员编排，
     * 面板内容是种子形投影；只填空下仅修复散列证明未动过的面板的失效引用，D24）；满足
     * {@link #shouldRebuildStructure} 条件的本主题本源行（D22）
     * 与他主题本源行（{@code resetAllOrigin} 下恒、其余策略下散列证明未改动时，D23）例外，整体重建为
     * 种子默认（{@link #rebuildRegion}）。区域插槽未在对应清单声明也跳过并告警，
     * 不使整个主题安装失败。面板设置的引用令牌不可解析同法整块跳过并记 {@link Failure} 明细
     * （R2 行级降级，见类注释）。
     */
    private void importRegion(Long siteId, String themeKey, String page, SeedRegion seed, String origin,
                              String assetPrefix, SeedAssetCopier.Session assets, String filePath,
                              SeedOverwrite overwrite, Tally tally) {
        SlotDef regionSlotDef = resolveRegionSlotDef(page, seed.regionName(), themeKey);
        if (regionSlotDef == null) {
            logger.warn("Seed region skipped — region slot not declared: page={}, region={} (theme={})",
                    page, seed.regionName(), themeKey);
            tally.undeclaredSlots.add(page + "#" + seed.regionName());
            tally.regionsSkipped++;
            return;
        }
        if (regionSlotDef.isShared()) {
            // 共享区域（page-regions §15）在宿主页播种；跟随页的种子会造出一个默认分叉，违背共享本意——跳过并告警
            logger.warn("Seed region skipped — region is shared from page {}; seed it there: page={}, region={} (theme={})",
                    regionSlotDef.getSharedFrom(), page, seed.regionName(), themeKey);
            tally.sharedSlots.add(page + "#" + seed.regionName() + "->" + regionSlotDef.getSharedFrom());
            tally.regionsSkipped++;
            return;
        }
        // 幂等 + 非破坏：区域唯一键已存在（操作员编排或旧种子）则结构默认不覆盖；本源面板内容按策略刷新。
        // 例外（D22/D23）：本主题本源行满足重建条件、或他主题本源行（还原下恒、否则须散列证明未改动），
        // 整体收敛到种子默认（重建放弃则照常落回内容刷新）
        PageRegion existing = pageRegionMapper.selectByUnique(siteId, page, seed.regionName(),
                PageRegion.SCOPE_DEFAULT);
        if (existing != null) {
            if (shouldRebuildStructure(existing, seed, origin, overwrite)
                    && rebuildRegion(siteId, themeKey, page, seed, existing, origin, assetPrefix, assets,
                            filePath, overwrite, regionSlotDef, tally)) {
                tally.regionsReset++;
                return;
            }
            if (overwrite != SeedOverwrite.FILL_ONLY) {
                syncRegionPanels(siteId, page, seed, origin, assetPrefix, assets, regionSlotDef, overwrite, tally);
            } else {
                repairStaleRefPanels(siteId, themeKey, page, seed, origin, assetPrefix, assets, regionSlotDef,
                        tally);
            }
            tally.regionsSkipped++;
            return;
        }
        // 先以占位ID校验结构（行布局键/列数/上限），在建面板前快速失败
        List<String> structureErrors = toPlaceholderLayout(seed).validate(regionSlotDef, null);
        if (!structureErrors.isEmpty()) {
            throw new CompositionLoadException(filePath, structureErrors);
        }
        // 先展开全部面板设置（引用令牌解析发生在此）：任一令牌不可解析则整块跳过本区域——
        // 尚未落任何行，建目标内容（或补内容种子）后重导即整块补齐（R2 行级降级，见类注释）
        Map<String, Placement> built = new LinkedHashMap<>(16);
        boolean unresolvable = false;
        for (SeedRow row : seed.rows()) {
            for (int ci = 0; ci < row.columns().size(); ci++) {
                SeedColumn column = row.columns().get(ci);
                for (int pi = 0; pi < column.panels().size(); pi++) {
                    SeedPanel panel = column.panels().get(pi);
                    String panelOriginId = page + "#" + seed.regionName()
                            + "#" + row.id() + "#" + ci + "#" + pi;
                    try {
                        built.put(panelOriginId, buildPanelPlacement(siteId, page, seed, panel, origin,
                                panelOriginId, assetPrefix, assets));
                    } catch (SeedTokenException e) {
                        unresolvable = true;
                        tally.failPanel(panelOriginId, "Unresolvable seed token " + e.getMessage()
                                + " — region " + page + "#" + seed.regionName()
                                + " skipped");
                        logger.warn("Seed region skipped — unresolvable token {}: page={}, region={}, "
                                        + "panel={} (theme={})", e.getMessage(), page,
                                seed.regionName(), panelOriginId, themeKey);
                    }
                }
            }
        }
        if (unresolvable) {
            tally.regionsSkipped++;
            return;
        }
        // 建面板投放并以真实投放ID装配结构树
        List<RegionLayout.Row> rows = new ArrayList<>(seed.rows().size());
        for (SeedRow row : seed.rows()) {
            List<RegionLayout.Column> columns = new ArrayList<>(row.columns().size());
            for (int ci = 0; ci < row.columns().size(); ci++) {
                SeedColumn column = row.columns().get(ci);
                List<String> panelIds = new ArrayList<>(column.panels().size());
                for (int pi = 0; pi < column.panels().size(); pi++) {
                    String panelOriginId = page + "#" + seed.regionName()
                            + "#" + row.id() + "#" + ci + "#" + pi;
                    Long panelId = importPanel(siteId, built.get(panelOriginId), regionSlotDef, tally);
                    panelIds.add(String.valueOf(panelId));
                }
                columns.add(new RegionLayout.Column(panelIds));
            }
            rows.add(new RegionLayout.Row(row.id(), row.layout(), columns));
        }
        RegionLayout layout = new RegionLayout(rows);
        // 已发布树落库前再以已发布 Schema 复核（与 RegionService.saveDraft 一致的纵深防御）
        validateLayoutSchema(filePath, layout.toJson());
        insertRegion(siteId, page, seed.regionName(), origin, layout);
        tally.regionsImported++;
    }

    /**
     * D22 结构重建条件：本主题本源行——{@code resetAllOrigin} 无条件重建（悬空引用/被删面板一并
     * 自愈——重建即收敛到种子默认，红色警示确认在前）；{@code syncUnmodified} 仅当散列<b>在场</b>且
     * 证明结构未被操作员改动、且种子结构确有变化时重建（散列缺失不重建——结构重建比内容刷新破坏性强，
     * R4「宁可少报」在此取不动而非动）；{@code fillOnly} 恒不重建。
     * D23 追加：<b>他主题本源</b>行（{@code theme:<other>}）——区域坐标 {@code (site, page, region, scope)}
     * 无主题维度，该行是上一主题演示布局的遗留而非操作员编排：{@code resetAllOrigin} 恒重建；
     * {@code fillOnly}/{@code syncUnmodified} 仅当该行的 R4 散列<b>在场且复现</b>（证明自上一主题导入后
     * 未被操作员改动——纯遗留演示布局，重建无所失）时重建，散列缺失/不复现则照旧跳过（改过的旧布局
     * 只有红色警示的还原才替换）。操作员行（无来源标记）恒不动（§6.1 安全不变量 1）。
     */
    private boolean shouldRebuildStructure(PageRegion existing, SeedRegion seed, String origin,
                                           SeedOverwrite overwrite) {
        if (!origin.equals(existing.getOrigin())) {
            if (!isForeignThemeOrigin(existing.getOrigin())) {
                return false;
            }
            return overwrite == SeedOverwrite.RESET_ALL_ORIGIN || isUntouchedSinceImport(existing);
        }
        if (overwrite == SeedOverwrite.RESET_ALL_ORIGIN) {
            return true;
        }
        if (overwrite != SeedOverwrite.SYNC_UNMODIFIED) {
            return false;
        }
        String stored = existing.getOriginHash();
        if (stored == null) {
            return false;
        }
        String currentHash = SeedHash.regionHash(RegionLayout.parse(existing.getLayoutJson()));
        return stored.equals(currentHash)
                && !currentHash.equals(SeedHash.regionHash(toPlaceholderLayout(seed)));
    }

    /**
     * 区域行自其来源导入后是否未被操作员改动（R4 单一脏检测：{@code origin_hash_} 在场且由当前结构树
     * 投影复现）。散列缺失（早于 R4 的导入）或结构树不可解析 → 无从判定 → 视作改过（宁可不动）。
     */
    private static boolean isUntouchedSinceImport(PageRegion existing) {
        String stored = existing.getOriginHash();
        if (stored == null) {
            return false;
        }
        RegionLayout layout = RegionLayout.parse(existing.getLayoutJson());
        return layout != null && stored.equals(SeedHash.regionHash(layout));
    }

    /**
     * 是否他主题本源标记（{@code theme:<other>}，D23）——操作员行为 {@code null}/空，不在此列。
     */
    private static boolean isForeignThemeOrigin(@Nullable String rowOrigin) {
        return rowOrigin != null && rowOrigin.startsWith(ORIGIN_PREFIX);
    }

    /**
     * 重建位置决策（{@link #rebuildRegion} 预构建产物，逐种子位置一条）。
     *
     * @param bean    按种子构建的新面板（{@code keepOld} 时为 {@code null}——令牌不可解析，沿用旧内容）
     * @param existed 该位置既有的本源面板行（{@code null} = 已删除，重建新面板）
     * @param moved   既有行已被操作员移出本区域（仅 {@code resetAllOrigin} 可达：剥离来源 + 原位重建）
     */
    private record RebuildSlot(@Nullable Placement bean, @Nullable Placement existed, boolean moved) {
    }

    /**
     * 重建既有本源区域（D22）：结构树与面板整体收敛到种子默认，单事务、与
     * {@link RegionService#seedResetBackfill}/{@link RegionService#seedResetPublish} 成对。流程：
     * <ol>
     *   <li>占位结构校验（同新导入——种子文件坏即整次失败）；</li>
     *   <li>预构建全部面板（令牌展开，<b>无写入</b>）：某位置令牌不可解析时，该位置有可沿用的存活
     *       面板则保旧内容（记失败明细），面板行已不在则整块放弃重建（返回 {@code false}，调用方落回
     *       内容刷新——建目标内容后重导即整块补齐，R2 口径）；{@code syncUnmodified} 下发现面板被
     *       移出本区域同样放弃（移出必改本区域结构树、散列即不再复现，实际不可达——防御性守卫）；</li>
     *   <li>回填快照（未发布草稿 + 被替换的已发布树，先于任何面板改写——快照物化自面板行现值）；</li>
     *   <li>逐位置落面板：已删除的重建；存活的按策略刷新（{@code resetAllOrigin} 恒刷新并解除归档，
     *       {@code syncUnmodified} 保留操作员改过的面板内容）；被移出的单行剥离来源（转操作员投放，
     *       绝不搬回）后原位重建新面板；</li>
     *   <li>装配真实ID结构树 + Schema 复核 + {@code seedResetPublish}（掉树面板经标准孤立清理删除，
     *       内容已由回填快照保全——整次重置可经构建器发布历史逐区域恢复）。</li>
     * </ol>
     *
     * @return 是否完成重建（{@code false} = 放弃，调用方落回面板内容刷新路径）
     */
    private boolean rebuildRegion(Long siteId, String themeKey, String page, SeedRegion seed,
                                  PageRegion existing, String origin, String assetPrefix,
                                  SeedAssetCopier.Session assets, String filePath, SeedOverwrite overwrite,
                                  SlotDef regionSlotDef, Tally tally) {
        List<String> structureErrors = toPlaceholderLayout(seed).validate(regionSlotDef, null);
        if (!structureErrors.isEmpty()) {
            throw new CompositionLoadException(filePath, structureErrors);
        }
        // 预构建（无写入）：放弃与否必须在任何行被改写之前决出。keepOld 的失败明细暂存，
        // 决定重建后才入账——放弃路径会落回 syncRegionPanels，由它自行记明细，先入账即重复
        Map<String, RebuildSlot> slots = new LinkedHashMap<>(16);
        List<String[]> pendingKeepOldFailures = new ArrayList<>();
        for (SeedRow row : seed.rows()) {
            for (int ci = 0; ci < row.columns().size(); ci++) {
                SeedColumn column = row.columns().get(ci);
                for (int pi = 0; pi < column.panels().size(); pi++) {
                    SeedPanel panel = column.panels().get(pi);
                    String panelOriginId = page + "#" + seed.regionName()
                            + "#" + row.id() + "#" + ci + "#" + pi;
                    Placement existed = placementMapper.selectByOrigin(siteId, origin, panelOriginId);
                    boolean moved = existed != null && (!page.equals(existed.getPageRef())
                            || !seed.regionName().equals(existed.getSlotName()));
                    if (moved && overwrite != SeedOverwrite.RESET_ALL_ORIGIN) {
                        logger.warn("Seed region rebuild aborted — panel moved out of region: panel={} "
                                + "(theme={})", panelOriginId, themeKey);
                        return false;
                    }
                    Placement bean = null;
                    try {
                        bean = buildPanelPlacement(siteId, page, seed, panel, origin, panelOriginId,
                                assetPrefix, assets);
                    } catch (SeedTokenException e) {
                        if (existed == null || moved) {
                            // 面板须重建而令牌不可解析：整块放弃（建目标内容后重导即整块补齐）
                            tally.failPanel(panelOriginId, "Unresolvable seed token " + e.getMessage()
                                    + " — region " + page + "#" + seed.regionName() + " rebuild skipped");
                            logger.warn("Seed region rebuild skipped — unresolvable token {}: page={}, "
                                            + "region={}, panel={} (theme={})", e.getMessage(), page,
                                    seed.regionName(), panelOriginId, themeKey);
                            return false;
                        }
                        pendingKeepOldFailures.add(new String[]{panelOriginId,
                                "Unresolvable seed token " + e.getMessage() + " — panel kept as-is"});
                    }
                    slots.put(panelOriginId, new RebuildSlot(bean, existed, moved));
                }
            }
        }
        // 回填快照先于任何面板改写：被替换的草稿/已发布状态（含操作员内容）由此保全
        String actor = seedActor();
        regionService.seedResetBackfill(existing, actor);
        pendingKeepOldFailures.forEach(entry -> tally.failPanel(entry[0], entry[1]));
        // 逐位置落面板并以真实投放ID装配结构树
        List<RegionLayout.Row> rows = new ArrayList<>(seed.rows().size());
        for (SeedRow row : seed.rows()) {
            List<RegionLayout.Column> columns = new ArrayList<>(row.columns().size());
            for (int ci = 0; ci < row.columns().size(); ci++) {
                SeedColumn column = row.columns().get(ci);
                List<String> panelIds = new ArrayList<>(column.panels().size());
                for (int pi = 0; pi < column.panels().size(); pi++) {
                    String panelOriginId = page + "#" + seed.regionName()
                            + "#" + row.id() + "#" + ci + "#" + pi;
                    panelIds.add(String.valueOf(rebuildPanel(slots.get(panelOriginId), overwrite,
                            regionSlotDef, tally)));
                }
                columns.add(new RegionLayout.Column(panelIds));
            }
            rows.add(new RegionLayout.Row(row.id(), row.layout(), columns));
        }
        RegionLayout layout = new RegionLayout(rows);
        validateLayoutSchema(filePath, layout.toJson());
        regionService.seedResetPublish(existing, layout, SeedHash.regionHash(layout), actor);
        if (!origin.equals(existing.getOrigin())) {
            // D23：他主题本源行重建后改记为本主题来源（区域坐标无主题维度，此后按本主题种子管理）
            pageRegionMapper.updateOrigin(existing.getId(), origin);
            existing.setOrigin(origin);
        }
        return true;
    }

    /**
     * 重建路径的单面板落库（{@link #rebuildRegion} 第 4 步）。
     *
     * @return 该位置在新结构树上的真实投放ID
     */
    private Long rebuildPanel(RebuildSlot slot, SeedOverwrite overwrite, SlotDef regionSlotDef, Tally tally) {
        Placement existed = slot.existed();
        Placement bean = slot.bean();
        if (bean == null) {
            // keepOld：令牌不可解析，旧内容原位保留（失败明细已入账）
            tally.panelsSkipped++;
            return existed.getId();
        }
        if (existed != null && !slot.moved()) {
            // 面板内容刷新判定与 syncRegionPanels 同一 R4 口径
            if (overwrite.shouldOverwrite(isUnmodified(existed.getOriginHash(),
                    SeedHash.placementHash(existed)))) {
                bean.setId(existed.getId());
                bean.setCreated(existed.getCreated());
                placementService.savePanel(bean, regionSlotDef);
                stampPlacementHash(bean);
                if (existed.isArchived()) {
                    // 重置收敛到种子默认：归档的种子面板（切换孤儿）解除归档，重新可渲染
                    placementService.restore(existed.getId());
                }
                tally.panelsUpdated++;
            } else {
                // syncUnmodified：操作员改过的面板内容保留，仅结构位置归位
                tally.panelsSkipped++;
            }
            return existed.getId();
        }
        if (existed != null) {
            // 被移出的本源行剥离来源（转操作员投放，绝不搬回），种子位置重建新面板
            placementMapper.clearOriginById(existed.getId());
        }
        placementService.savePanel(bean, regionSlotDef);
        stampPlacementHash(bean);
        tally.panelsImported++;
        return bean.getId();
    }

    /**
     * 快照署名：当前操作员用户名（安装引导等无请求上下文时为 {@code null}）。
     */
    @Nullable
    private static String seedActor() {
        User user = com.ujcms.cms.core.support.Contexts.findCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    /**
     * 保存一个已构建的区域面板投放（{@code slot_name_ = region_name_}、{@code panel_shape_} 已设、
     * 来源标记 origin；构建与令牌展开在 {@link #importRegion} 的前置遍历完成）。
     * 幂等：面板 {@code (origin, originId)} 已存在则复用其ID。校验经 {@link PlacementService#savePanel}。
     */
    private Long importPanel(Long siteId, Placement bean, SlotDef regionSlotDef, Tally tally) {
        Placement existed = placementMapper.selectByOrigin(siteId, bean.getOrigin(), bean.getOriginId());
        if (existed != null) {
            return existed.getId();
        }
        Long id = placementService.savePanel(bean, regionSlotDef);
        stampPlacementHash(bean);
        tally.panelsImported++;
        return id;
    }

    /**
     * 按覆盖策略刷新既有区域的<b>本源面板</b>内容（§6.1 / A3）：面板按 {@code (origin, originId)}
     * 定位（originId 编码种子内位置；面板被可视化构建器移动后 originId 不变，内容照常刷新），
     * 结构树不动。面板行已不存在（区域为操作员自建、或面板已删）则无事发生——面板不在结构树上
     * 凭空重建只会产生悬挂投放。
     */
    private void syncRegionPanels(Long siteId, String page, SeedRegion seed, String origin, String assetPrefix,
                                  SeedAssetCopier.Session assets, SlotDef regionSlotDef, SeedOverwrite overwrite,
                                  Tally tally) {
        for (SeedRow row : seed.rows()) {
            for (int ci = 0; ci < row.columns().size(); ci++) {
                SeedColumn column = row.columns().get(ci);
                for (int pi = 0; pi < column.panels().size(); pi++) {
                    SeedPanel panel = column.panels().get(pi);
                    String panelOriginId = page + "#" + seed.regionName()
                            + "#" + row.id() + "#" + ci + "#" + pi;
                    Placement existed = placementMapper.selectByOrigin(siteId, origin, panelOriginId);
                    if (existed == null) {
                        continue;
                    }
                    // 面板已被移出本区域（页面/插槽不再匹配种子位置）：跳过——savePanel 的归属校验
                    // 会拒绝（400），而构成导入是单事务，异常将使整次导入回滚
                    if (!page.equals(existed.getPageRef()) || !seed.regionName().equals(existed.getSlotName())) {
                        tally.panelsSkipped++;
                        continue;
                    }
                    if (overwrite.shouldOverwrite(isUnmodified(existed.getOriginHash(),
                            SeedHash.placementHash(existed)))) {
                        Placement bean;
                        try {
                            bean = buildPanelPlacement(siteId, page, seed, panel, origin, panelOriginId,
                                    assetPrefix, assets);
                        } catch (SeedTokenException e) {
                            // 刷新形的行级降级（R2）：令牌不可解析则该面板跳过刷新（旧内容保留），后续照常
                            tally.panelsSkipped++;
                            tally.failPanel(panelOriginId, "Unresolvable seed token " + e.getMessage()
                                    + " — panel refresh skipped");
                            logger.warn("Seed panel refresh skipped — unresolvable token {}: panel={}",
                                    e.getMessage(), panelOriginId);
                            continue;
                        }
                        bean.setId(existed.getId());
                        bean.setCreated(existed.getCreated());
                        placementService.savePanel(bean, regionSlotDef);
                        stampPlacementHash(bean);
                        tally.panelsUpdated++;
                    } else {
                        tally.panelsSkipped++;
                    }
                }
            }
        }
    }

    /**
     * 只填空策略下的失效引用修复（theme-marketplace D24）。本主题本源面板的 R4 散列<b>在场且复现</b>
     * （操作员自导入后从未动过）、而其引用型设置经 {@link SettingsResolver#staleRefs}（与健康页同一口径）
     * 判定失联——典型成因：移除演示数据 删掉了面板展开时钉住的栏目/区块/投票ID，重导入以新ID重建了目标
     * ——则该面板按种子整体刷新（令牌重新展开；与 {@link #syncRegionPanels} 同一落库路径，保留行ID/created，
     * 重落散列）。法则与 D23 对他主题区域行的散列证明相同：<b>未动过的行上的死引用是空缺，不是操作员
     * 成果</b>——只填空的「填空」涵盖之。散列缺失（早于 R4）或不复现的面板照旧不动（改过的面板只有
     * 更新未修改项 / 还原 才刷新）；已归档、被移出本区域的面板跳过；组件不可解析时无从判定字段类型，
     * 跳过；令牌不可解析走 R2 行级降级（旧内容保留 + 失败明细）。
     */
    private void repairStaleRefPanels(Long siteId, String themeKey, String page, SeedRegion seed, String origin,
                                      String assetPrefix, SeedAssetCopier.Session assets, SlotDef regionSlotDef,
                                      Tally tally) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        for (SeedRow row : seed.rows()) {
            for (int ci = 0; ci < row.columns().size(); ci++) {
                SeedColumn column = row.columns().get(ci);
                for (int pi = 0; pi < column.panels().size(); pi++) {
                    SeedPanel panel = column.panels().get(pi);
                    String panelOriginId = page + "#" + seed.regionName()
                            + "#" + row.id() + "#" + ci + "#" + pi;
                    Placement existed = placementMapper.selectByOrigin(siteId, origin, panelOriginId);
                    if (existed == null || existed.isArchived() || !page.equals(existed.getPageRef())
                            || !seed.regionName().equals(existed.getSlotName())) {
                        continue;
                    }
                    String stored = existed.getOriginHash();
                    if (stored == null || !stored.equals(SeedHash.placementHash(existed))) {
                        continue;
                    }
                    Shape shape = snapshot.resolveTrackShape(List.of(themeKey), existed.getPanelShape());
                    if (shape == null || settingsResolver.staleRefs(shape, existed.getSettings(), siteId).isEmpty()) {
                        continue;
                    }
                    Placement bean;
                    try {
                        bean = buildPanelPlacement(siteId, page, seed, panel, origin, panelOriginId,
                                assetPrefix, assets);
                    } catch (SeedTokenException e) {
                        tally.panelsSkipped++;
                        tally.failPanel(panelOriginId, "Unresolvable seed token " + e.getMessage()
                                + " — stale-ref repair skipped");
                        logger.warn("Seed panel stale-ref repair skipped — unresolvable token {}: panel={}",
                                e.getMessage(), panelOriginId);
                        continue;
                    }
                    bean.setId(existed.getId());
                    bean.setCreated(existed.getCreated());
                    placementService.savePanel(bean, regionSlotDef);
                    stampPlacementHash(bean);
                    tally.panelsRepaired++;
                    logger.info("Seed panel stale refs repaired: panel={} (theme={})", panelOriginId, themeKey);
                }
            }
        }
    }

    /**
     * 构建区域面板投放（设置驱动面板 {@code component} + {@code settings}，component-settings §4.3）：
     * 与构建器保存（{@code RegionService.PanelInput.toBean}）同构；设置值经 {@code {{theme}}} 前缀
     * 与 {@code {{channel:…}}}/{@code {{article:…}}} 引用令牌展开。
     */
    private Placement buildPanelPlacement(Long siteId, String page, SeedRegion seed, SeedPanel panel,
                                          String origin, String panelOriginId, String assetPrefix,
                                          SeedAssetCopier.Session assets) {
        Placement placement = new Placement();
        placement.setSiteId(siteId);
        placement.setPageRef(page);
        placement.setSlotName(seed.regionName());
        placement.setPanelShape(panel.component());
        placement.setSettings(tokenExpander.expandData(panel.settings() != null ? panel.settings() : Map.of(),
                new SeedTokenExpander.ExpandContext(assetPrefix, assets, siteId, origin, false)));
        placement.setEnabled(panel.enabled() == null || panel.enabled());
        placement.setVisibility(panel.visibility() == null ? Placement.VISIBILITY_ALL : panel.visibility());
        placement.setOrigin(origin);
        placement.setOriginId(panelOriginId);
        return placement;
    }

    private void insertRegion(Long siteId, String page, String regionName, String origin,
                              RegionLayout layout) {
        OffsetDateTime now = OffsetDateTime.now();
        PageRegion region = new PageRegion();
        region.setId(snowflakeSequence.nextId());
        region.setSiteId(siteId);
        region.setPageRef(page);
        region.setRegionName(regionName);
        // 种子直接落地为已发布树（无草稿）——日 1 可见
        region.setLayoutJson(layout.toJson());
        region.setDraftLayoutJson(null);
        region.setOrigin(origin);
        // R4：结构树散列（面板ID占位归一——面板内容各有散列，真实ID是每次导入分配的雪花ID）
        region.setOriginHash(SeedHash.regionHash(layout));
        region.setVersion(1);
        region.setCreated(now);
        region.setModified(now);
        pageRegionMapper.insert(region);
    }

    /**
     * 以已发布的 {@code page-region-layout} Schema 复核所建结构树（落库前的纵深防御，
     * 与 {@code RegionService.saveDraft} 的 {@code validateLayoutSchema} 一致）。
     */
    private void validateLayoutSchema(String filePath, String json) {
        try {
            List<String> errors = schemas.validate(CompositionSchemas.PAGE_REGION_LAYOUT, MAPPER.readTree(json));
            if (!errors.isEmpty()) {
                throw new CompositionLoadException(filePath, errors);
            }
        } catch (JacksonException e) {
            throw new CompositionLoadException(filePath, e);
        }
    }

    /**
     * 解析区域 {@link SlotDef}（本主题清单）。未声明返回 {@code null}（调用方跳过）。
     */
    @Nullable
    private SlotDef resolveRegionSlotDef(String page, String regionName, String themeKey) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        PageManifest manifest = snapshot.getThemeManifest(themeKey, page);
        if (manifest == null) {
            return null;
        }
        return manifest.getSlots().get(regionName);
    }

    /**
     * 以占位面板ID构建结构树，仅供结构校验（行布局键、列数与行布局一致、行数/单列面板数上限），
     * 在创建真实面板投放前快速失败（与 {@code RegionService.saveDraft} 的占位校验一致）。
     */
    private static RegionLayout toPlaceholderLayout(SeedRegion seed) {
        List<RegionLayout.Row> rows = new ArrayList<>(seed.rows().size());
        for (SeedRow row : seed.rows()) {
            List<RegionLayout.Column> columns = new ArrayList<>(row.columns().size());
            for (SeedColumn column : row.columns()) {
                List<String> ids = new ArrayList<>(column.panels().size());
                for (int i = 0; i < column.panels().size(); i++) {
                    ids.add("_");
                }
                columns.add(new RegionLayout.Column(ids));
            }
            rows.add(new RegionLayout.Row(row.id(), row.layout(), columns));
        }
        return new RegionLayout(rows);
    }

    // ----- parsing -----

    private SeedRegionFile parseRegionFile(File file) {
        JsonNode node = readJson(file);
        List<String> errors = schemas.validate(CompositionSchemas.SEED_REGIONS, node);
        if (!errors.isEmpty()) {
            throw new CompositionLoadException(file.getPath(), errors);
        }
        String page = node.get("page").asText();
        List<SeedRegion> regions = new ArrayList<>();
        node.get("regions").properties().forEach(entry -> {
            JsonNode regionNode = entry.getValue();
            List<SeedRow> rows = new ArrayList<>();
            for (JsonNode rowNode : regionNode.get("rows")) {
                List<SeedColumn> columns = new ArrayList<>();
                for (JsonNode columnNode : rowNode.get("columns")) {
                    List<SeedPanel> panels = new ArrayList<>();
                    for (JsonNode panelNode : columnNode.get("panels")) {
                        Boolean enabled = panelNode.hasNonNull("enabled") ? panelNode.get("enabled").asBoolean() : null;
                        String visibility = panelNode.hasNonNull("visibility")
                                ? panelNode.get("visibility").asText() : null;
                        // 设置驱动面板（component-settings §4.3）：{component, settings}
                        Map<String, Object> settings = MAPPER.convertValue(panelNode.get("settings"),
                                new TypeReference<>() {
                                });
                        panels.add(new SeedPanel(panelNode.get("component").asText(), settings, enabled,
                                visibility));
                    }
                    columns.add(new SeedColumn(panels));
                }
                rows.add(new SeedRow(rowNode.get("id").asText(), rowNode.get("layout").asText(), columns));
            }
            regions.add(new SeedRegion(entry.getKey(), rows));
        });
        return new SeedRegionFile(page, regions);
    }

    private JsonNode readJson(File file) {
        try {
            return MAPPER.readTree(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new CompositionLoadException(file.getPath(), e);
        } catch (IOException e) {
            throw new CompositionLoadException(file.getPath(), e);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static File[] listFiles(File dir, String suffix) {
        File[] files = dir.listFiles(file -> file.isFile() && file.getName().endsWith(suffix));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }

    private record SeedRegionFile(String page, List<SeedRegion> regions) {
    }

    private record SeedRegion(String regionName, List<SeedRow> rows) {
    }

    private record SeedRow(String id, String layout, List<SeedColumn> columns) {
    }

    private record SeedColumn(List<SeedPanel> panels) {
    }

    /**
     * 种子区域面板（设置驱动，component-settings §4.3）：{@code {component, settings, enabled?, visibility?}}。
     */
    private record SeedPanel(String component, @Nullable Map<String, Object> settings, @Nullable Boolean enabled,
                             @Nullable String visibility) {
    }

    /**
     * 种子导入结果
     *
     * @param panelsImported 新导入面板数（含新区域内的面板与重建时补建的已删面板）
     * @param panelsSkipped  跳过面板数（已存在且策略不覆盖 / 面板已被移出 / 刷新令牌不可解析）
     * @param panelsUpdated  按覆盖策略刷新的既有本源面板数（§6.1，A3）
     * @param panelsRepaired 只填空下修复失效引用的既有本源面板数（D24：散列证明未动过、引用目标已删——
     *                       如 移除演示数据 → 导入 后的死引用；按种子整体重展开）
     * @param regionsImported 新导入区域数
     * @param regionsReset    结构重建的既有本源区域数（D22：{@code resetAllOrigin} 恒重建、
     *                        {@code syncUnmodified} 散列证明未改且种子有变时重建）
     * @param regionsSkipped  跳过区域数（已存在且不满足重建条件 / 区域插槽未声明 / 面板令牌不可解析）
     * @param undeclaredSlots 因区域插槽未在对应清单声明而跳过的区域（{@code page#region}，
     *                        计入 {@code regionsSkipped}——弃警不拒，明细供操作员核对，H5）
     * @param sharedSlots     因区域为共享引用而跳过的区域（{@code page#region->owner}，page-regions §15：
     *                        种子应放在宿主页；计入 {@code regionsSkipped}）
     * @param failures        面板级失败明细（R2 通道对构成的延伸，2026-07-27：引用令牌不可解析的
     *                        整区域跳过 / 刷新面板跳过；与 {@code ContentSeedResult.failures} 同形，
     *                        至多 {@link Failure#MAX_DETAILS} 条）
     */
    public record SeedImportResult(int panelsImported, int panelsSkipped, int panelsUpdated, int panelsRepaired,
                                   int regionsImported, int regionsReset, int regionsSkipped,
                                   List<String> undeclaredSlots, List<String> sharedSlots,
                                   List<Failure> failures) {
        public static final SeedImportResult EMPTY =
                new SeedImportResult(0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
    }

    // ----- plan (§6.2 seed/preview) -----

    /**
     * 构成种子干跑计划（只读，§6.2）。逐区域对比种子声明与站点现状；面板散列与导入时刻
     * {@code origin_hash_} 比较。区域比较结构树投影（行布局键 + 每列面板数——面板ID归一）。
     */
    public CompositionSeedPlan plan(Long siteId, String themeName, File themeDir, String templateStoreUrl) {
        String origin = originOf(themeName);
        String assetPrefix = stripTrailingSlash(templateStoreUrl) + "/" + siteId + "/" + themeName;
        // 干跑资源会话：{{asset:}} 展开为确定性 URL，不落盘、不登记附件（§6.2）
        SeedAssetCopier.Session assets = assetCopier.openDry(siteId, themeName);
        PlanCounts panels = new PlanCounts();
        PlanCounts regions = new PlanCounts();
        List<Collision> collisions = new ArrayList<>();

        for (File file : listFiles(new File(new File(themeDir, SEED_DIR), REGIONS_DIR), REGIONS_SUFFIX)) {
            SeedRegionFile regionFile = parseRegionFile(file);
            for (SeedRegion seed : regionFile.regions()) {
                planRegion(siteId, origin, regionFile.page(), seed, assetPrefix, assets, regions, panels,
                        collisions);
            }
        }
        return new CompositionSeedPlan(panels.toPlan(), regions.toPlan(), collisions);
    }

    private void planRegion(Long siteId, String origin, String page, SeedRegion seed, String assetPrefix,
                            SeedAssetCopier.Session assets, PlanCounts regions, PlanCounts panels,
                            List<Collision> collisions) {
        PageRegion existing = pageRegionMapper.selectByUnique(siteId, page, seed.regionName(),
                PageRegion.SCOPE_DEFAULT);
        if (existing == null) {
            // 区域未建：结构树 + 面板全部新导入
            regions.added++;
            for (SeedRow row : seed.rows()) {
                for (SeedColumn column : row.columns()) {
                    panels.added += column.panels().size();
                }
            }
            return;
        }
        if (!origin.equals(existing.getOrigin())) {
            regions.linked++;
            collisions.add(new Collision("region", page + "#" + seed.regionName(),
                    page + "#" + seed.regionName(), existing.getId()));
        } else {
            regions.existing++;
            String stored = existing.getOriginHash();
            if (stored != null) {
                RegionLayout rowLayout = RegionLayout.parse(existing.getLayoutJson());
                if (rowLayout != null && !stored.equals(SeedHash.regionHash(rowLayout))) {
                    regions.operatorModified++;
                }
                if (!stored.equals(SeedHash.regionHash(toPlaceholderLayout(seed)))) {
                    regions.seedChanged++;
                }
            }
        }
        // 既有区域的本源面板（面板内容可刷新；本源区域的结构可按 D22 条件重建）
        for (SeedRow row : seed.rows()) {
            for (int ci = 0; ci < row.columns().size(); ci++) {
                SeedColumn column = row.columns().get(ci);
                for (int pi = 0; pi < column.panels().size(); pi++) {
                    SeedPanel panel = column.panels().get(pi);
                    String panelOriginId = page + "#" + seed.regionName()
                            + "#" + row.id() + "#" + ci + "#" + pi;
                    Placement existed = placementMapper.selectByOrigin(siteId, origin, panelOriginId);
                    if (existed == null) {
                        // D22：本源区域缺失的种子面板行（面板已删）——结构重建会补建，计入新增；
                        // linked（他源/操作员）区域的结构不动，缺失面板不重建、不计数
                        if (origin.equals(existing.getOrigin())) {
                            panels.added++;
                        }
                        continue;
                    }
                    panels.existing++;
                    String stored = existed.getOriginHash();
                    if (stored == null) {
                        continue;
                    }
                    if (!stored.equals(SeedHash.placementHash(existed))) {
                        panels.operatorModified++;
                    }
                    try {
                        Placement bean = buildPanelPlacement(siteId, page, seed, panel, origin, panelOriginId,
                                assetPrefix, assets);
                        if (!stored.equals(SeedHash.placementHash(bean))) {
                            panels.seedChanged++;
                        }
                    } catch (SeedTokenException e) {
                        // 令牌不可解析（目标内容不存在）：种子形无从复现存量散列——计入种子有变，
                        // 干跑绝不因此失败（导入路径同令牌走 R2 行级降级）
                        panels.seedChanged++;
                    }
                }
            }
        }
    }

    /**
     * 干跑计划计数器
     */
    private static final class PlanCounts {
        int added;
        int linked;
        int existing;
        int operatorModified;
        int seedChanged;

        CategoryPlan toPlan() {
            return new CategoryPlan(added, linked, existing, operatorModified, seedChanged);
        }
    }

    /**
     * 构成种子干跑计划（{@code SeedPlan} 的构成部分，§6.2）
     */
    public record CompositionSeedPlan(CategoryPlan panels, CategoryPlan regions, List<Collision> collisions) {
        public static final CompositionSeedPlan EMPTY = new CompositionSeedPlan(
                CategoryPlan.EMPTY, CategoryPlan.EMPTY, List.of());
    }
}
