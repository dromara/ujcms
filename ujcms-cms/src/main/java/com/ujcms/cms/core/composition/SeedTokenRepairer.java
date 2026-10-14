package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.PageManifest.SlotDef;
import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.PlacementMapper;
import com.ujcms.cms.ext.mapper.VoteMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 种子令牌修复器（theme-seed-export R1）：对导入遗留的未展开种子引用就地重跑展开——两种词形：
 * <ul>
 *   <li><b>令牌字面量</b>（D9 之前导入）：{@code {{channel:}}}/{@code {{article:}}}/{@code {{asset:}}}/
 *       {@code {{theme}}}——{@link SeedTokenExpander} 宽容模式（引用按站内别名/来源 originId 解析，
 *       资源经 {@link SeedAssetCopier} 真实会话复制登记，{@code {{theme}}} 展开为面板来源主题的
 *       资源前缀；来源缺失的操作员面板回退站点活动主题）；</li>
 *   <li><b>别名映射</b>（令牌文法之前的手写种子形，D20）：channelRef 值 {@code {"alias": "<x>"}}——
 *       字段定义驱动展开为站内栏目ID（组件不可解析时无从判定字段类型，该形跳过）。</li>
 * </ul>
 *
 * <p><b>显式操作员动作</b>（构成健康页按钮），绝不启动自愈（硬性项目规则）。<b>单事务</b>：与构成
 * 导入同语义——结构性异常（如组件越出区域 allowedShapes）使整次修复回滚；不可解析令牌不是异常，
 * 原样保留并逐面板列明（跳过明细）。保存经 {@link PlacementService#savePanel}（区域插槽校验照常），
 * 服务层写库保证缓存失效广播——直改 SQL 对运行中后端不可见。
 *
 * <p><b>来源散列</b>：修复前行「操作员未改动」（散列缺失或复现）则修复后重落散列——行等价于
 * 由 D9 之后的导入器产出（散列恒为展开后投影，D9 决策注记）；修复前已被操作员改动的行保留原散列
 * （修复不清洗脏标记）。
 *
 * @author PONY
 */
@Component
public class SeedTokenRepairer {
    private static final Logger logger = LoggerFactory.getLogger(SeedTokenRepairer.class);

    private static final String SEED_DIR = "seed";
    private static final String CONTENT_DIR = "content";
    private static final String ASSETS_DIR = "assets";

    private final PlacementService placementService;
    private final PlacementMapper placementMapper;
    private final ManifestService manifestService;
    private final SeedAssetCopier assetCopier;
    private final SeedTokenExpander tokenExpander;

    public SeedTokenRepairer(PlacementService placementService, PlacementMapper placementMapper,
                             ManifestService manifestService, SeedAssetCopier assetCopier,
                             ChannelMapper channelMapper, ArticleMapper articleMapper,
                             BlockMapper blockMapper, VoteMapper voteMapper) {
        this.placementService = placementService;
        this.placementMapper = placementMapper;
        this.manifestService = manifestService;
        this.assetCopier = assetCopier;
        this.tokenExpander = new SeedTokenExpander(channelMapper, articleMapper, blockMapper, voteMapper);
    }

    /**
     * 修复站点全部在用面板（{@code panel_shape_} 非空且未归档——与健康检测同范围）的令牌字面量。
     *
     * @param siteId            站点ID
     * @param activeThemeKeys   站点活动主题命名空间（桌面 + 移动，规范化）——区域插槽解析用
     * @param fallbackThemeName 无来源标记面板的令牌展开回退主题（站点活动桌面主题，裸名）
     * @param templateStoreUrl  模板存储 URL 前缀（{@code {{theme}}} 展开用）
     * @param themeDirByName    主题目录解析（裸主题名 → 目录；{@code {{asset:}}} 源文件所在）
     * @param userId            操作员ID（资源附件归属）
     * @return 逐面板修复报告
     */
    @Transactional(rollbackFor = Exception.class)
    public RepairReport repair(Long siteId, List<String> activeThemeKeys, String fallbackThemeName,
                               String templateStoreUrl, Function<String, File> themeDirByName, Long userId) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        String storeUrl = stripTrailingSlash(templateStoreUrl);
        // 同主题的资源会话复用（同一 relpath 本次运行只复制一次——与导入会话语义一致）
        Map<String, SeedAssetCopier.Session> sessions = new LinkedHashMap<>(4);
        List<RepairReport.PanelRepair> panels = new ArrayList<>();
        int panelsRepaired = 0;
        int tokensExpanded = 0;
        int tokensSkipped = 0;
        for (Placement placement : placementService.listBySiteId(siteId)) {
            if (placement.getPanelShape() == null || placement.isArchived()) {
                continue;
            }
            // 别名映射遗留形（D20）按字段定义驱动——组件不可解析时无从判定字段类型，仅处理令牌形
            Shape shape = snapshot.resolveTrackShape(activeThemeKeys, placement.getPanelShape());
            Map<String, FieldDef> fieldDefs = shape != null ? shape.getSettings() : Map.of();
            List<String> before = legacyRefs(placement.getSettings(), fieldDefs);
            if (before.isEmpty()) {
                continue;
            }
            SlotDef slotDef = resolveRegionSlotDef(snapshot, activeThemeKeys, placement);
            if (slotDef == null) {
                // 区域插槽不可解析（主题切换后残留等）：savePanel 无从校验，整面板跳过
                tokensSkipped += before.size();
                panels.add(new RepairReport.PanelRepair(String.valueOf(placement.getId()), placement.getPageRef(),
                        placement.getSlotName(), placement.getPanelShape(),
                        0, before, "区域插槽未在活动主题清单声明，无法校验保存，跳过"));
                continue;
            }
            String themeName = themeNameOf(placement.getOrigin(), fallbackThemeName);
            SeedAssetCopier.Session session = sessions.computeIfAbsent(themeName, name ->
                    assetCopier.open(siteId, name, contentAssetsDir(themeDirByName.apply(name)), userId));
            Map<String, Object> expanded = tokenExpander.expandData(placement.getSettings(),
                    new SeedTokenExpander.ExpandContext(storeUrl + "/" + siteId + "/" + themeName,
                            session, siteId, placement.getOrigin(), true));
            expanded = tokenExpander.expandAliasRefs(expanded, fieldDefs, siteId);
            List<String> leftover = legacyRefs(expanded, fieldDefs);
            int expandedCount = before.size() - leftover.size();
            if (expandedCount > 0) {
                String storedHash = placement.getOriginHash();
                boolean wasUnmodified = storedHash == null
                        || storedHash.equals(SeedHash.placementHash(placement));
                placement.setSettings(expanded);
                placementService.savePanel(placement, slotDef);
                if (wasUnmodified) {
                    placementMapper.updateOriginHash(placement.getId(), SeedHash.placementHash(placement));
                }
                panelsRepaired++;
            }
            tokensExpanded += expandedCount;
            tokensSkipped += leftover.size();
            panels.add(new RepairReport.PanelRepair(String.valueOf(placement.getId()), placement.getPageRef(),
                    placement.getSlotName(), placement.getPanelShape(),
                    expandedCount, leftover, null));
        }
        RepairReport report = new RepairReport(panels.size(), panelsRepaired, tokensExpanded, tokensSkipped,
                panels);
        logger.info("Seed tokens repaired: site={}, panelsWithTokens={}, repaired={}, expanded={}, skipped={}",
                siteId, panels.size(), panelsRepaired, tokensExpanded, tokensSkipped);
        return report;
    }

    /**
     * 面板设置内全部种子引用遗留（令牌字面量 + 别名映射，{@code 路径: 形} 展示清单）。
     */
    private static List<String> legacyRefs(Map<String, Object> settings, Map<String, FieldDef> fieldDefs) {
        List<String> refs = SeedTokenExpander.listTokens(settings);
        refs.addAll(SeedTokenExpander.listAliasRefs(settings, fieldDefs));
        return refs;
    }

    /**
     * 面板来源标记 → 令牌展开主题：{@code theme:<name>} 取其种子来源主题（{{asset:}} 源文件与
     * {{theme}} 前缀都属它）；无来源/非主题来源回退站点活动主题。
     */
    private static String themeNameOf(@Nullable String origin, String fallbackThemeName) {
        if (origin != null && origin.startsWith(ThemeSeedImporter.ORIGIN_PREFIX)) {
            return origin.substring(ThemeSeedImporter.ORIGIN_PREFIX.length());
        }
        return fallbackThemeName;
    }

    private static File contentAssetsDir(File themeDir) {
        return new File(new File(new File(themeDir, SEED_DIR), CONTENT_DIR), ASSETS_DIR);
    }

    /**
     * 解析面板的区域插槽（与渲染/健康口径一致）：任一活动主题清单。
     */
    @Nullable
    private static SlotDef resolveRegionSlotDef(RegistrySnapshot snapshot, List<String> themeKeys,
                                                Placement placement) {
        for (String themeKey : themeKeys) {
            PageManifest manifest = snapshot.getThemeManifest(themeKey, placement.getPageRef());
            if (manifest != null) {
                SlotDef def = manifest.getSlots().get(placement.getSlotName());
                if (def != null) {
                    return def;
                }
            }
        }
        return null;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 修复报告
     *
     * @param panelsWithTokens 存有令牌字面量的面板数（修复范围）
     * @param panelsRepaired   实际展开并保存的面板数
     * @param tokensExpanded   展开的令牌数
     * @param tokensSkipped    保留的令牌数（不可解析——悬空别名/originId、来源缺失的文章令牌、插槽不可解析的整面板）
     * @param panels           逐面板明细
     */
    public record RepairReport(int panelsWithTokens, int panelsRepaired, int tokensExpanded, int tokensSkipped,
                               List<PanelRepair> panels) {

        /**
         * 单面板修复明细
         *
         * @param panelId   面板投放ID
         * @param pageRef   页面名
         * @param region    区域名（{@code slot_name_}）
         * @param component 组件名
         * @param expanded  展开的令牌数
         * @param skipped   保留的令牌（{@code 路径: 令牌} 展示形）
         * @param note      整面板跳过原因（可空；如区域插槽不可解析）
         */
        public record PanelRepair(String panelId, String pageRef, String region, String component,
                                  int expanded, List<String> skipped, @Nullable String note) {
        }
    }
}
