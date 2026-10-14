package com.ujcms.cms.core.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujcms.cms.core.component.ThemeInfo;
import com.ujcms.cms.core.component.ThemeInstallException;
import com.ujcms.cms.core.component.ThemeInstallReport;
import com.ujcms.cms.core.component.ThemeInstallValidator;
import com.ujcms.cms.core.component.ThemeMetadata;
import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.PlacementService;
import com.ujcms.cms.core.composition.RegistrySnapshot;
import com.ujcms.cms.core.composition.Semver;
import com.ujcms.cms.core.composition.SeedOverwrite;
import com.ujcms.cms.core.composition.SeedPlan;
import com.ujcms.cms.core.composition.ThemeContentSeedImporter.DeclaredContent;
import com.ujcms.cms.core.composition.ThemeContentSeedRemover;
import com.ujcms.cms.core.composition.ThemeContentSeedRemover.RemoveResult;
import com.ujcms.cms.core.composition.ThemeImpactReport;
import com.ujcms.cms.core.composition.ThemeLifecycleLock;
import com.ujcms.cms.core.composition.ThemeSeedImporter;
import com.ujcms.cms.core.composition.ThemeSeedOrchestrator;
import com.ujcms.cms.core.composition.ThemeSeedOrchestrator.SeedRunResult;
import com.ujcms.cms.core.composition.ThemeSettingsService;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Placement;
import com.ujcms.cms.core.domain.RegistryReload;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.ThemeState;
import com.ujcms.cms.core.domain.Topic;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.BlockMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.PageRegionMapper;
import com.ujcms.cms.core.mapper.PlacementMapper;
import com.ujcms.cms.ext.mapper.FormTypeMapper;
import com.ujcms.cms.ext.mapper.LinkGroupMapper;
import com.ujcms.cms.ext.mapper.SurveyMapper;
import com.ujcms.cms.ext.mapper.VoteMapper;
import com.ujcms.cms.core.mapper.ThemeStateMapper;
import com.ujcms.cms.core.mapper.TopicMapper;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.web.PathResolver;

/**
 * Phase 5 marketplace installer. Coordinates the validator, the filesystem extraction, and
 * the composition registry so admin operators can install / uninstall / activate themes from
 * the console.
 *
 * <h2>Operations</h2>
 * <ul>
 *   <li>{@link #install} — validate ZIP, extract to {@code templates/{siteId}/{themeName}/},
 *       optionally activate.</li>
 *   <li>{@link #uninstall} — refuse if the theme is referenced by any site, otherwise
 *       delete the directory.</li>
 *   <li>{@link #activate} — set {@code Site.theme} (and optionally {@code mobileTheme}).</li>
 *   <li>{@link #listThemes} — scan {@code templates/{siteId}/} and return metadata
 *       for each subdirectory.</li>
 * </ul>
 *
 * <h2>Storage assumptions</h2>
 * Templates live on the local filesystem under {@code Config.templateStorage.path}. The spec
 * commits to "private deployments" with no CDN or remote template store, so this service
 * resolves paths through Spring's {@link ResourceLoader} (matching {@code SiteController}'s
 * theme list) and operates directly on {@link File}. Non-local template storage is not
 * supported in Phase 5.
 *
 * <p>There is intentionally no auto-update path — operators uninstall and reinstall under a
 * different theme name to migrate (per design decision recorded in the plan).
 */
@Service
public class ThemeInstallService {
    private static final Logger logger = LoggerFactory.getLogger(ThemeInstallService.class);

    /** Theme directory names: kebab/snake case, no path separators. */
    private static final Pattern THEME_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,49}$");
    /** Names reserved by the framework that themes must not collide with. */
    private static final List<String> RESERVED_NAMES = List.of(Constants.TEMPLATE_SHARE);

    private final ResourceLoader resourceLoader;
    private final ThemeInstallValidator validator;
    private final SiteService siteService;
    private final ConfigService configService;
    private final PathResolver pathResolver;
    private final Props props;
    private final ObjectMapper objectMapper;
    private final ManifestService manifestService;
    private final ThemeSeedOrchestrator seedOrchestrator;
    private final ThemeContentSeedRemover contentSeedRemover;
    private final ThemeSettingsService themeSettingsService;
    private final PlacementService placementService;
    private final PlacementMapper placementMapper;
    private final PageRegionMapper pageRegionMapper;
    private final ChannelMapper channelMapper;
    private final ArticleMapper articleMapper;
    private final TopicMapper topicMapper;
    private final BlockMapper blockMapper;
    private final VoteMapper voteMapper;
    private final SurveyMapper surveyMapper;
    private final FormTypeMapper formTypeMapper;
    private final LinkGroupMapper linkGroupMapper;
    private final com.ujcms.cms.core.mapper.ModelMapper modelMapper;
    private final com.ujcms.cms.core.mapper.DictTypeMapper dictTypeMapper;
    private final ThemeStateMapper themeStateMapper;
    private final ThemeLifecycleLock lifecycleLock;
    private final LayoutVariantService layoutVariantService;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    /**
     * §19.2 主题安装耗时指标名
     */
    public static final String METRIC_INSTALL = "ujcms.theme.install.duration";

    @Value("${ujcms.theme.install.maxSizeMb:" + ThemeInstallValidator.DEFAULT_MAX_SIZE_MB + "}")
    private int maxSizeMb = ThemeInstallValidator.DEFAULT_MAX_SIZE_MB;

    public ThemeInstallService(ResourceLoader resourceLoader,
                               ThemeInstallValidator validator,
                               SiteService siteService,
                               ConfigService configService,
                               PathResolver pathResolver,
                               Props props,
                               ObjectMapper objectMapper,
                               ManifestService manifestService,
                               ThemeSeedOrchestrator seedOrchestrator,
                               ThemeContentSeedRemover contentSeedRemover,
                               ThemeSettingsService themeSettingsService,
                               PlacementService placementService,
                               PlacementMapper placementMapper,
                               PageRegionMapper pageRegionMapper,
                               ChannelMapper channelMapper,
                               ArticleMapper articleMapper,
                               TopicMapper topicMapper,
                               BlockMapper blockMapper,
                               VoteMapper voteMapper,
                               SurveyMapper surveyMapper,
                               FormTypeMapper formTypeMapper,
                               LinkGroupMapper linkGroupMapper,
                               com.ujcms.cms.core.mapper.ModelMapper modelMapper,
                               com.ujcms.cms.core.mapper.DictTypeMapper dictTypeMapper,
                               ThemeStateMapper themeStateMapper,
                               ThemeLifecycleLock lifecycleLock,
                               LayoutVariantService layoutVariantService,
                               io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.resourceLoader = resourceLoader;
        this.validator = validator;
        this.siteService = siteService;
        this.configService = configService;
        this.pathResolver = pathResolver;
        this.props = props;
        this.objectMapper = objectMapper;
        this.manifestService = manifestService;
        this.seedOrchestrator = seedOrchestrator;
        this.contentSeedRemover = contentSeedRemover;
        this.themeSettingsService = themeSettingsService;
        this.placementService = placementService;
        this.placementMapper = placementMapper;
        this.pageRegionMapper = pageRegionMapper;
        this.channelMapper = channelMapper;
        this.articleMapper = articleMapper;
        this.topicMapper = topicMapper;
        this.blockMapper = blockMapper;
        this.voteMapper = voteMapper;
        this.surveyMapper = surveyMapper;
        this.formTypeMapper = formTypeMapper;
        this.linkGroupMapper = linkGroupMapper;
        this.modelMapper = modelMapper;
        this.dictTypeMapper = dictTypeMapper;
        this.themeStateMapper = themeStateMapper;
        this.lifecycleLock = lifecycleLock;
        this.layoutVariantService = layoutVariantService;
        this.meterRegistry = meterRegistry;
    }

    /** Test seam for explicit max size. */
    public void setMaxSizeMb(int maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }

    public int getMaxSizeMb() {
        return maxSizeMb;
    }

    // ----- install --------------------------------------------------------

    /**
     * Validate, extract, optionally activate (no seed import). Convenience 4-arg form.
     *
     * @param activate which site slot(s) to activate the theme into after install; {@code null} = install only
     */
    public ThemeInstallReport install(MultipartFile zipPart, Long siteId, String themeName,
                                      @Nullable ActivateTarget activate) {
        return install(zipPart, siteId, themeName, activate, SeedScope.NONE);
    }

    /**
     * Validate, extract, optionally seed + activate. On any failure after extraction begins, the
     * partially-extracted theme directory is removed (rollback). Composition artifacts (shapes,
     * page manifests, settings schema) are validated against a candidate snapshot before the live
     * registry is reloaded; an invalid bundle rolls back the extraction (§12.1). Guarded by the
     * per-site lifecycle mutex shared with the file watcher (§12.5).
     *
     * @param activate  which site slot(s) to activate the theme into after install
     *                  ({@code desktop|mobile|both}); {@code null} = install only
     * @param seedScope what origin-tagged seed data to import after install (R5, §6.1)
     */
    public ThemeInstallReport install(MultipartFile zipPart, Long siteId, String themeName,
                                      @Nullable ActivateTarget activate, SeedScope seedScope) {
        Site site = requireSite(siteId);
        validateThemeName(themeName);
        long start = System.nanoTime();
        try {
            return lifecycleLock.withSiteLock(siteId, () ->
                    doInstall(zipPart, site, siteId, themeName, activate, seedScope));
        } finally {
            // §19.2：安装耗时（含锁等待 + 校验 + 解包 + 可选种子导入 + 重扫）
            meterRegistry.timer(METRIC_INSTALL)
                    .record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 文件入径安装（remote-marketplace D-C1-5）：包已在本地磁盘（如市场下载的临时文件）时的入口，
     * 与 Multipart 入径共用同一条 doInstallFile 管线——校验 / 解包 / 构成门槛 / 种子生命周期分毫不差。
     * {@code zipFile} 的生命周期归调用方（本方法不删除）。
     */
    public ThemeInstallReport install(Path zipFile, Long siteId, String themeName,
                                      @Nullable ActivateTarget activate, SeedScope seedScope) {
        Site site = requireSite(siteId);
        validateThemeName(themeName);
        long start = System.nanoTime();
        try {
            return lifecycleLock.withSiteLock(siteId, () ->
                    doInstallFile(zipFile.toFile(), site, siteId, themeName, activate, seedScope));
        } finally {
            meterRegistry.timer(METRIC_INSTALL)
                    .record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private ThemeInstallReport doInstall(MultipartFile zipPart, Site site, Long siteId, String themeName,
                                         @Nullable ActivateTarget activate, SeedScope seedScope) {
        File themeDir = themeDir(siteId, themeName);
        if (themeDir.exists()) {
            throw new ThemeInstallException("theme.install.error.themeExists",
                    "Theme directory '" + themeName + "' already exists. Choose a different name "
                            + "or uninstall the existing theme first.");
        }

        File tempZip;
        try {
            tempZip = Files.createTempFile("ujcms-theme-", ".zip").toFile();
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to create temp file: " + e.getMessage(), e);
        }

        try {
            zipPart.transferTo(tempZip);
            return doInstallFile(tempZip, site, siteId, themeName, activate, seedScope);
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to read uploaded file: " + e.getMessage(), e);
        } finally {
            FileUtils.deleteQuietly(tempZip);
        }
    }

    private ThemeInstallReport doInstallFile(File zipFile, Site site, Long siteId, String themeName,
                                             @Nullable ActivateTarget activate, SeedScope seedScope) {
        File themeDir = themeDir(siteId, themeName);
        if (themeDir.exists()) {
            throw new ThemeInstallException("theme.install.error.themeExists",
                    "Theme directory '" + themeName + "' already exists. Choose a different name "
                            + "or uninstall the existing theme first.");
        }

        try {
            ThemeInstallReport report;
            try (InputStream in = new FileInputStream(zipFile)) {
                report = validator.validate(in, zipFile.length(), maxSizeBytes());
            }
            report.setSha256(sha256Hex(zipFile));
            // 版本兼容门槛（H2）：解包前拦下，不留半成品目录
            requireVersionCompatible(report.getMetadata(), themeName);
            // 纯数据包不可激活（§8）：解包前拦下，不留半成品目录
            if (activate != null && isDataOnly(report.getMetadata())) {
                throw new ThemeInstallException("theme.install.error.notActivatable",
                        "A data-only package (kind: \"data-only\") cannot be activated. "
                                + "Install without activation, then import its seed.");
            }
            try (InputStream in = new FileInputStream(zipFile)) {
                extractTo(in, themeDir);
            } catch (RuntimeException | IOException e) {
                FileUtils.deleteQuietly(themeDir);
                if (e instanceof ThemeInstallException ti) {
                    throw ti;
                }
                throw new ThemeInstallException("theme.install.error.io",
                        "Failed to extract theme: " + e.getMessage(), e);
            }

            // 构成制品校验门槛（§12.1）：不自洽则回滚解包，不污染线上注册表
            List<String> errors = manifestService.validateThemeArtifacts(siteId + "/" + themeName, themeDir);
            if (errors != null && !errors.isEmpty()) {
                FileUtils.deleteQuietly(themeDir);
                throw new ThemeInstallException("theme.install.error.compositionInvalid",
                        "Theme composition artifacts are invalid: " + String.join("; ", errors));
            }
            // 重扫并替换注册表快照（新主题的 shapes/pages/settings 生效）
            reloadRegistry();
            recordThemeVersion(siteId, themeName, report.getMetadata());

            // 先激活后导种子（A2 调整）：内容种子的布局变体取值按站点目录（生效主题对）校验，
            // 激活在前才能让本主题声明的变体全量通过；构成种子不受此顺序影响。
            if (activate != null) {
                applyActivation(site, themeName, activate);
            }
            if (seedScope.includesComposition()) {
                SeedRunResult seed = seedOrchestrator.importSeed(siteId, themeName, themeDir, templateStorageUrl(),
                        currentUserId(), seedScope.includesContent());
                logger.info("Theme install seed: site={} name={} {}", siteId, themeName, seed);
                // 种子结果随安装报告出仓（theme-seed-export §8.1 / D22：失败明细要到得了控制台）
                report.setSeed(seed);
                // 未声明区域插槽的种子跳过明细并入安装报告 warnings（弃警不拒，H5）——控制台既有警告展示直接呈现
                if (!seed.composition().undeclaredSlots().isEmpty()) {
                    report.getWarnings().add("seed regions skipped — region slot not declared: "
                            + String.join(", ", seed.composition().undeclaredSlots()));
                }
                if (!seed.composition().sharedSlots().isEmpty()) {
                    report.getWarnings().add("seed regions skipped — region is shared, seed it on the owner page: "
                            + String.join(", ", seed.composition().sharedSlots()));
                }
            }
            logger.info("Theme installed: site={} name={} files={} seedScope={} sha256={}",
                    siteId, themeName, report.getTemplateFileCount(), seedScope, report.getSha256());
            return report;
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to read package file: " + e.getMessage(), e);
        }
    }

    // ----- copy (sandbox-preview-site §3.2) --------------------------------

    /**
     * 跨站点复制单个已安装主题实例（sandbox-preview-site §3.2 步骤3 / D5）：
     * {@code templates/{fromSiteId}/{themeName}} → {@code templates/{toSiteId}/{themeName}}，
     * 经模板存储 {@code FileHandler}（{@code SiteT2Service#copy} 整目录复制的窄化形式，
     * 本地/MinIO/FTP 存储一致）。主题包的 {@code seed/**} 随目录逐字节复制。复制后按安装同款
     * 门槛重跑构成制品校验（不自洽即删除副本回滚）、重扫注册表、记录主题版本；并复制源站点
     * {@code theme_state} 行的设置值（若有）——沙箱呈现操作员已调优的候选设置，
     * 与 {@code _theme=} 预览一致（D6）。
     *
     * <p>可激活性（纯数据包 / 版本兼容）不在此把关——调用方按需先过
     * {@link #requireThemePreviewable}；本方法只保证「复制出来的实例与安装出来的实例
     * 通过同一套制品校验」。调用方须已持有目标站点的生命周期锁；本方法内对源站点加锁，
     * 防止复制途中源主题被更新/卸载（半换名目录）。
     *
     * @param fromSiteId 源站点ID（候选主题所在站点）
     * @param toSiteId   目标站点ID（沙箱站点）
     * @param themeName  主题名称（裸名）
     */
    public void copyThemeInstance(Long fromSiteId, Long toSiteId, String themeName) {
        validateThemeName(themeName);
        lifecycleLock.withSiteLock(fromSiteId, () -> {
            File fromDir = themeDir(fromSiteId, themeName);
            if (!fromDir.exists()) {
                throw new ThemeInstallException("theme.install.error.notFound",
                        "Theme '" + themeName + "' not found on site " + fromSiteId + ".");
            }
            File toDir = themeDir(toSiteId, themeName);
            if (toDir.exists()) {
                throw new ThemeInstallException("theme.install.error.themeExists",
                        "Theme directory '" + themeName + "' already exists on site " + toSiteId + ".");
            }
            FileHandler templateFileHandler = configService.getUnique().getTemplateStorage()
                    .getFileHandler(pathResolver);
            templateFileHandler.copy("/" + fromSiteId + "/" + themeName, "/" + toSiteId + "/" + themeName);

            // 构成制品校验门槛（§12.1 同款）：副本不自洽则删除回滚，不污染线上注册表
            List<String> errors = manifestService.validateThemeArtifacts(toSiteId + "/" + themeName, toDir);
            if (errors != null && !errors.isEmpty()) {
                FileUtils.deleteQuietly(toDir);
                throw new ThemeInstallException("theme.install.error.compositionInvalid",
                        "Theme composition artifacts are invalid: " + String.join("; ", errors));
            }
            reloadRegistry();
            recordThemeVersion(toSiteId, themeName, readMetadata(toDir));

            // 设置值行复制（D6）：只搬 settings_json_，seed_imported_ 等状态留给目标站点自身的生命周期
            ThemeState fromState = themeStateMapper.selectByUnique(fromSiteId, themeName);
            String settingsJson = fromState != null ? fromState.getSettingsJson() : null;
            if (settingsJson != null && !settingsJson.isBlank()) {
                ThemeState toState = themeSettingsService.getOrCreate(toSiteId, themeName);
                toState.setSettingsJson(settingsJson);
                toState.setModified(OffsetDateTime.now());
                themeStateMapper.update(toState);
            }
            logger.info("Theme instance copied: {}/{} -> {}/{}", fromSiteId, themeName, toSiteId, themeName);
        });
    }

    /**
     * 主题显示名（清单 {@code name}，缺失回退主题名）。沙箱站点命名等轻量场景用，
     * 免走全量 {@link #listThemes}。
     */
    public String themeDisplayName(Long siteId, String themeName) {
        validateThemeName(themeName);
        ThemeMetadata metadata = readMetadata(themeDir(siteId, themeName));
        return metadata != null && metadata.getName() != null && !metadata.getName().isBlank()
                ? metadata.getName() : themeName;
    }

    // ----- uninstall ------------------------------------------------------

    /** Backward-compatible 2-arg uninstall (keeps theme-origin rows as operator rows). */
    public void uninstall(Long siteId, String themeName) {
        uninstall(siteId, themeName, UninstallDataPolicy.KEEP);
    }

    /**
     * Uninstall a theme and apply a data policy to its origin-tagged rows (§12.4):
     * {@link UninstallDataPolicy#KEEP keep} (clear {@code origin_} → become ordinary operator rows),
     * {@link UninstallDataPolicy#ARCHIVE archive}, or {@link UninstallDataPolicy#DELETE delete}.
     * Removes the {@code ujcms_theme_state} row. Refuses if the theme is still active anywhere.
     * Guarded by the per-site lifecycle mutex.
     */
    public void uninstall(Long siteId, String themeName, UninstallDataPolicy dataPolicy) {
        validateThemeName(themeName);
        lifecycleLock.withSiteLock(siteId, () -> doUninstall(siteId, themeName, dataPolicy));
    }

    private void doUninstall(Long siteId, String themeName, UninstallDataPolicy dataPolicy) {
        File themeDir = themeDir(siteId, themeName);
        if (!themeDir.exists()) {
            throw new ThemeInstallException("theme.install.error.notFound",
                    "Theme '" + themeName + "' not found.");
        }
        // Refuse if any site (in any tenant tree) is currently bound to this theme. Operators
        // must switch the site to another theme first.
        List<Site> bound = sitesUsingTheme(siteId, themeName);
        if (!bound.isEmpty()) {
            String names = bound.stream().map(Site::getName).reduce((a, b) -> a + ", " + b).orElse("");
            throw new ThemeInstallException("theme.install.error.themeInUse",
                    "Theme '" + themeName + "' is in use by site(s): " + names
                            + ". Change those sites to a different theme first.");
        }
        applyUninstallDataPolicy(siteId, themeName, dataPolicy);
        ThemeState state = themeStateMapper.selectByUnique(siteId, themeName);
        if (state != null) {
            themeStateMapper.delete(state.getId());
        }
        try {
            FileUtils.deleteDirectory(themeDir);
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to delete theme directory: " + e.getMessage(), e);
        }
        reloadRegistry();
        logger.info("Theme uninstalled: site={} name={} dataPolicy={}", siteId, themeName, dataPolicy);
    }

    /**
     * Apply the uninstall data policy to this site's rows tagged {@code theme:<themeName>}
     * (§12.4; page-regions §10; content extension §6.1 / A3). Site-scoped: {@code origin_} carries
     * no site id and the same theme name is routinely installed on several sites (themes are
     * per-site copies under {@code templates/{siteId}/}), so an unscoped query would destroy or
     * de-provenance other sites' rows. Region panels are themselves origin-tagged placements, so
     * the placement branch already archives/deletes them; only the {@code ujcms_page_region}
     * wrapper rows need explicit handling. Regions have no archive state, so ARCHIVE drops their
     * provenance (panels archived → region renders empty, recoverable in Health) exactly like KEEP.
     *
     * <p>Content rows (channels/articles/topics) follow §6.1: KEEP → clear origin; ARCHIVE →
     * published articles {@code STATUS_ARCHIVED} (origin kept), channels/topics
     * origin-cleared (no archived state exists for them); DELETE → hard remove with the
     * non-origin-children guard (guard-failed rows are de-provenanced, never deleted — D7).
     */
    private void applyUninstallDataPolicy(Long siteId, String themeName, UninstallDataPolicy dataPolicy) {
        String origin = ThemeSeedImporter.originOf(themeName);
        switch (dataPolicy) {
            case KEEP -> {
                placementMapper.clearOrigin(siteId, origin);
                pageRegionMapper.clearOrigin(siteId, origin);
                contentSeedRemover.keepForUninstall(siteId, themeName);
            }
            case ARCHIVE -> {
                placementMapper.listByOrigin(siteId, origin).forEach(p -> placementService.archive(p.getId()));
                pageRegionMapper.clearOrigin(siteId, origin);
                contentSeedRemover.archiveForUninstall(siteId, themeName);
            }
            case DELETE -> {
                placementMapper.listByOrigin(siteId, origin).forEach(p -> placementService.delete(p.getId()));
                pageRegionMapper.listByOrigin(siteId, origin).forEach(r -> pageRegionMapper.delete(r.getId()));
                RemoveResult removed = contentSeedRemover.remove(siteId, themeName, false,
                        Contexts.findCurrentUser());
                logger.info("Uninstall content DELETE: site={} theme={} {}", siteId, themeName, removed);
            }
            default -> throw new ThemeInstallException("theme.install.error.dataPolicy",
                    "Unknown uninstall data policy: " + dataPolicy);
        }
    }

    // ----- activate -------------------------------------------------------

    /**
     * Make {@code themeName} the site's active theme in the given slot(s) — desktop, mobile or
     * both (no seed). The directory must exist.
     */
    public void activate(Long siteId, String themeName, ActivateTarget target) {
        activate(siteId, themeName, target, SeedScope.NONE);
    }

    /**
     * Activate / switch (§12.3), fill-only seed. Convenience 4-arg form.
     */
    public void activate(Long siteId, String themeName, ActivateTarget target, SeedScope seedScope) {
        activate(siteId, themeName, target, seedScope, SeedOverwrite.FILL_ONLY);
    }

    /**
     * Activate / switch (§12.3). Partitions the site's placements against the resulting
     * desktop+mobile theme pair: orphans are archived — always — and archived placements whose
     * (page, region) is declared again are auto-restored (D21: a theme round trip needs no manual
     * Health work; delete stays a Health-page action). Flips {@code Site.theme} and/or
     * {@code mobileTheme} per {@code target}, then optionally imports the target theme's seed.
     * Activation itself never deletes anything (§6.1 safety invariant 3) — removal of the previous
     * theme's demo data is the separate explicit {@link #removeSeed} action. Guarded by the
     * per-site lifecycle mutex.
     *
     * @param target       which site slot(s) the theme goes into: desktop / mobile / both
     * @param seedScope    what seed data to import after switching (idempotent, §8.3 / R5)
     * @param overwrite    how the seed treats existing origin rows (§6.1; re-activation offers
     *                     {@code syncUnmodified} — "刷新未修改的演示数据")
     * @return the seed run result ({@code null} when {@code seedScope=none}) — failure detail must
     *         reach the console (theme-seed-export §8.1 / D22)
     */
    @Nullable
    public SeedRunResult activate(Long siteId, String themeName, ActivateTarget target,
                                  SeedScope seedScope, SeedOverwrite overwrite) {
        validateThemeName(themeName);
        File themeDir = themeDir(siteId, themeName);
        if (!themeDir.exists()) {
            throw new ThemeInstallException("theme.install.error.notFound",
                    "Theme '" + themeName + "' not found.");
        }
        requireActivatable(themeDir, themeName);
        return lifecycleLock.withSiteLock(siteId, () -> {
            Site site = requireSite(siteId);
            applyPlacementPartition(partitionPlacements(siteId, resultingThemeKeys(site, themeName, target)));
            applyActivation(site, themeName, target);
            if (!seedScope.includesComposition()) {
                return null;
            }
            SeedRunResult seed = seedOrchestrator.importSeed(siteId, themeName, themeDir, templateStorageUrl(),
                    currentUserId(), seedScope.includesContent(), overwrite);
            logger.info("Theme activate seed: site={} name={} overwrite={} {}",
                    siteId, themeName, overwrite, seed);
            return seed;
        });
    }

    // ----- seed lifecycle (§6.2, A3) -------------------------------------

    /**
     * 种子导入干跑计划（§6.2 {@code POST …/seed/preview}）：只读，站点锁内读主题目录
     * （与安装/更新的换名/半写状态互斥）。
     */
    public SeedPlan previewSeed(Long siteId, String themeName) {
        validateThemeName(themeName);
        return lifecycleLock.withSiteLock(siteId, () -> {
            File themeDir = themeDir(siteId, themeName);
            if (!themeDir.exists()) {
                throw new ThemeInstallException("theme.install.error.notFound",
                        "Theme '" + themeName + "' not found.");
            }
            return seedOrchestrator.plan(siteId, themeName, themeDir, templateStorageUrl());
        });
    }

    /**
     * 手动种子导入（§6.2 {@code POST …/seed/import}）：与安装/激活解耦——支持「先装码后导数」、
     * 纯数据包（§8），也是 A2 专题类失败（站点无专题模型）的续传路径。幂等；覆盖策略见 §6.1。
     */
    public SeedRunResult importSeed(Long siteId, String themeName, SeedScope scope, SeedOverwrite overwrite) {
        validateThemeName(themeName);
        return lifecycleLock.withSiteLock(siteId, () -> {
            File themeDir = themeDir(siteId, themeName);
            if (!themeDir.exists()) {
                throw new ThemeInstallException("theme.install.error.notFound",
                        "Theme '" + themeName + "' not found.");
            }
            SeedRunResult result = seedOrchestrator.importSeed(siteId, themeName, themeDir, templateStorageUrl(),
                    currentUserId(), scope.includesContent(), overwrite);
            logger.info("Theme manual seed import: site={} name={} scope={} overwrite={} {}",
                    siteId, themeName, scope, overwrite, result);
            return result;
        });
    }

    /**
     * 移除演示数据（§6.1 {@code DELETE …/seed}）：独立显式操作，只作用于内容本源行，物理删除
     * 不进回收站；{@code dryRun} 先出示计数（安全不变量 2）。主题目录无需存在——移除按来源标记
     * 定位，与主题文件无关（切换后移除上一主题演示数据的跟进卡片也走此路径）。
     */
    public RemoveResult removeSeed(Long siteId, String themeName, boolean dryRun) {
        validateThemeName(themeName);
        return lifecycleLock.withSiteLock(siteId, () ->
                contentSeedRemover.remove(siteId, themeName, dryRun, Contexts.findCurrentUser()));
    }

    /**
     * Impact report for a prospective activate / switch (§12.3): orphaned panel placements
     * against the resulting desktop+mobile theme pair, plus seed availability. Read-only.
     */
    public ThemeImpactReport previewActivate(Long siteId, String themeName, ActivateTarget target) {
        validateThemeName(themeName);
        File themeDir = themeDir(siteId, themeName);
        if (!themeDir.exists()) {
            throw new ThemeInstallException("theme.install.error.notFound",
                    "Theme '" + themeName + "' not found.");
        }
        requireActivatable(themeDir, themeName);
        Site site = requireSite(siteId);
        List<String> resulting = resultingThemeKeys(site, themeName, target);
        PlacementPartition partition = partitionPlacements(siteId, resulting);
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        String themeKey = siteId + "/" + themeName;
        int themeShapes = snapshot.getThemeShapes().getOrDefault(themeKey, Map.of()).size();
        int themePages = snapshot.getThemeManifests().getOrDefault(themeKey, Map.of()).size();
        int settingsGroups = snapshot.getThemeSettings(themeKey).getGroups().size();
        boolean seedAvailable = hasSeed(themeDir);
        ThemeState state = themeStateMapper.selectByUnique(siteId, themeName);
        boolean seedImported = state != null && Boolean.TRUE.equals(state.getSeedImported());

        List<ThemeImpactReport.OrphanedPlacement> placements = partition.toArchive().stream()
                .map(p -> new ThemeImpactReport.OrphanedPlacement(p.getId(), p.getPageRef(), p.getSlotName(),
                        p.getPanelShape(), p.getSettingsTitle()))
                .toList();
        // 孤立模板选择（template-track-variants D7）：显式 *_template_ 选择在切换后生效主题对的
        // 模板文件并集中缺失的选择数
        int orphanedTemplateSelections = layoutVariantService.countOrphanedTemplateSelections(siteId,
                resultingTemplates(resulting, Channel.TEMPLATE_PREFIX),
                resultingTemplates(resulting, Article.TEMPLATE_PREFIX),
                resultingTemplates(resulting, Topic.TEMPLATE_PREFIX));
        // 种子声明的内容条目数 + 本主题演示数据是否在库（§6.2，A3：重新激活时提示「刷新未修改的演示数据」）
        DeclaredContent seedContent = seedOrchestrator.declaredContent(themeDir);
        boolean demoDataPresent = demoDataPresent(siteId, themeName);
        return new ThemeImpactReport(resulting, placements, partition.toRestore().size(),
                orphanedTemplateSelections,
                seedAvailable, seedImported, seedContent, demoDataPresent,
                themeShapes, themePages, settingsGroups);
    }

    /**
     * 生效主题对 {@code templates/} 顶层符合前缀的模板基名并集（孤立模板选择计数用，
     * template-track-variants D7）。目录缺失/不可读 → 跳过该主题（计数侧对空集合整面不计）。
     *
     * @param themeKeys 生效主题键（{@code siteId/themeName}）
     * @param prefix    模板文件名前缀（{@code channel} / {@code article} / {@code topic}）
     */
    private Set<String> resultingTemplates(List<String> themeKeys, String prefix) {
        Set<String> names = new LinkedHashSet<>();
        for (String key : themeKeys) {
            int slash = key.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            File templatesDir = new File(themeDir(Long.valueOf(key.substring(0, slash)),
                    key.substring(slash + 1)), "templates");
            File[] matches = templatesDir.listFiles((dir, name) ->
                    name.startsWith(prefix) && name.endsWith(Constants.TEMPLATE_SUFFIX));
            if (matches == null) {
                continue;
            }
            for (File match : matches) {
                String name = match.getName();
                names.add(name.substring(0, name.length() - Constants.TEMPLATE_SUFFIX.length()));
            }
        }
        return names;
    }

    /**
     * 本主题的本源行（内容 + 构成表任一）是否仍在库（§6.2 {@code demoDataPresent}）。
     */
    private boolean demoDataPresent(Long siteId, String themeName) {
        String origin = ThemeSeedImporter.originOf(themeName);
        return channelMapper.countByOrigin(siteId, origin) > 0
                || articleMapper.countByOrigin(siteId, origin) > 0
                || topicMapper.countByOrigin(siteId, origin) > 0
                || blockMapper.countByOrigin(siteId, origin) > 0
                || voteMapper.countByOrigin(siteId, origin) > 0
                || surveyMapper.countByOrigin(siteId, origin) > 0
                || formTypeMapper.countByOrigin(siteId, origin) > 0
                || linkGroupMapper.countByOrigin(siteId, origin) > 0
                || modelMapper.countByOrigin(siteId, origin) > 0
                || dictTypeMapper.countByOrigin(siteId, origin) > 0
                || placementMapper.countByOrigin(siteId, origin) > 0
                || pageRegionMapper.countByOrigin(siteId, origin) > 0;
    }

    /**
     * 站点保存的主题绑定校验（A5 D15 补遗）：站点表单保存是激活的等价写入面——
     * 绑定到已安装的纯数据包目录一律 400。不存在的主题名保持既有宽容语义
     * （建站复制流程先写主题名、后装主题；渲染期自有回退），名称不合法同样不在此拦截。
     */
    public void validateThemeAssignable(Long siteId, @Nullable String themeName) {
        if (themeName == null || !THEME_NAME.matcher(themeName).matches()) {
            return;
        }
        File themeDir = themeDir(siteId, themeName);
        if (themeDir.exists()) {
            requireActivatable(themeDir, themeName);
        }
    }

    /**
     * 主题预览覆盖门槛（design-review P2-1 / H1）：前台 {@code ?_theme=<候选主题>} 渲染试看
     * 在 {@code SiteResolver} 确定站点后调用。名称不合法 / 目录不存在 → 400（预览失败要响亮，
     * 不静默回退活动主题）；已装目录再过 {@link #requireActivatable}——纯数据包与
     * {@code minUjcmsVersion} 门槛（P2-2）对预览同样生效：激活不了的主题也不该试看。
     */
    public void requireThemePreviewable(Long siteId, String themeName) {
        validateThemeName(themeName);
        File themeDir = themeDir(siteId, themeName);
        if (!themeDir.exists()) {
            throw new ThemeInstallException("theme.install.error.notFound",
                    "Theme '" + themeName + "' not found.");
        }
        requireActivatable(themeDir, themeName);
    }

    /**
     * 激活门槛（§8 安装可、激活不可）：纯数据包（{@code kind: data-only}）对
     * {@code activate} / {@code preview-activate} 一律 400。按已装目录的清单判定。
     * 版本兼容门槛（H2）同样在此把关——早于该门槛安装、或直接拷入磁盘的主题，
     * 激活/预检/站点绑定时仍会被拦截。
     */
    private void requireActivatable(File themeDir, String themeName) {
        ThemeMetadata metadata = readMetadata(themeDir);
        requireVersionCompatible(metadata, themeName);
        if (isDataOnly(metadata)) {
            throw new ThemeInstallException("theme.install.error.notActivatable",
                    "Theme '" + themeName + "' is a data-only package (kind: \"data-only\") "
                            + "and cannot be activated.");
        }
    }

    /**
     * 版本兼容门槛（H2）：清单声明的 {@code minUjcmsVersion} 高于当前运行版本时拒绝。
     * 安装/更新在解包前按 ZIP 校验报告拦截；激活面（activate / preview-activate / 站点绑定）
     * 按已装目录清单拦截。缺省或空白不约束；比较按 {@link Semver} 三段语义
     * （预发布/构建后缀忽略，§13.5）。运行版本无法解析（如资源未过滤的占位符）时记警告放行——
     * 该门槛是防呆护栏而非安全边界，不因构建配置问题瘫痪主题运维。
     * {@code compatibleSiteTypes} 无站点侧对应属性（Site 无类型概念），维持仅展示。
     */
    private void requireVersionCompatible(@Nullable ThemeMetadata metadata, String themeName) {
        String min = metadata == null ? null : metadata.getMinUjcmsVersion();
        if (min == null || min.isBlank()) {
            return;
        }
        Semver current = Semver.parse(props.getVersion());
        if (current.compareTo(new Semver(0, 0, 0)) == 0) {
            logger.warn("Runtime version '{}' is not a parseable semver; skipping minUjcmsVersion "
                    + "gate for theme '{}' (declared minimum: {})", props.getVersion(), themeName, min);
            return;
        }
        if (Semver.parse(min).compareTo(current) > 0) {
            throw new ThemeInstallException("theme.install.error.versionIncompatible",
                    "Theme '" + themeName + "' requires UJCMS " + min + " or later; "
                            + "current version is " + props.getVersion() + ".",
                    min, props.getVersion());
        }
    }

    private static boolean isDataOnly(@Nullable ThemeMetadata metadata) {
        return metadata != null && metadata.isDataOnly();
    }

    private void applyActivation(Site site, String themeName, ActivateTarget target) {
        // 站点只存裸主题名（R2）；/{siteId}/ 前缀由 Site.getThemeKey() / assembleTemplate 拼装。
        // 只改目标槽位：仅手机激活不碰桌面主题（手机参考主题的日常动作），反之亦然。
        if (target.includesDesktop()) {
            site.setTheme(themeName);
        }
        if (target.includesMobile()) {
            site.setMobileTheme(themeName);
        }
        siteService.update(site);
    }

    // ----- list -----------------------------------------------------------

    /**
     * List every theme directory under {@code templates/{siteId}/}, computing metadata for each.
     */
    public List<ThemeInfo> listThemes(Long siteId) {
        Site site = requireSite(siteId);
        File siteDir = siteDir(siteId);
        if (!siteDir.exists() || !siteDir.isDirectory()) {
            return new ArrayList<>();
        }
        File[] children = siteDir.listFiles(File::isDirectory);
        if (children == null) return new ArrayList<>();
        List<ThemeInfo> out = new ArrayList<>(children.length);
        for (File themeDir : children) {
            String themeName = themeDir.getName();
            ThemeMetadata metadata = readMetadata(themeDir);
            String displayName = (metadata != null && metadata.getName() != null
                    && !metadata.getName().isBlank())
                    ? metadata.getName() : themeName;
            String version = metadata != null ? metadata.getVersion() : null;
            // site.theme 只存裸主题名（R2），直接比较
            boolean active = themeName.equals(site.getTheme());
            boolean activeMobile = themeName.equals(site.getMobileTheme());
            boolean hasJson = new File(themeDir, "theme.manifest.json").isFile();
            // 种子状态（A4 画廊/详情页签）：seed_imported_ 是历史标记，demoDataPresent 才是在库实况
            ThemeState state = themeStateMapper.selectByUnique(siteId, themeName);
            boolean seedImported = state != null && Boolean.TRUE.equals(state.getSeedImported());
            // 登记状态（install-flow §14 / D43）：状态行在即已登记；无行目录挂「未登记」徽标 + 登记动作
            out.add(new ThemeInfo(themeName, displayName, active, activeMobile,
                    version, metadata, hasJson,
                    hasSeed(themeDir), seedImported, demoDataPresent(siteId, themeName),
                    state != null));
        }
        out.sort((a, b) -> a.getThemeName().compareToIgnoreCase(b.getThemeName()));
        return out;
    }

    /**
     * 未登记主题目录清单（install-flow §14 检测面，运维/脚本用）：站点目录下的主题目录
     * （跳过安装/更新瞬态目录）减去已有 {@code ujcms_theme_state} 行者。无清单的历史安装
     * 同样计入——它同样没有来历记录（D43）。控制台画廊走 {@link #listThemes} 的
     * {@code registered} 字段，不依赖本清单。
     */
    public List<String> listUnregisteredThemeDirs(Long siteId) {
        requireSite(siteId);
        File siteDir = siteDir(siteId);
        File[] children = siteDir.isDirectory() ? siteDir.listFiles(File::isDirectory) : null;
        if (children == null) {
            return List.of();
        }
        return java.util.Arrays.stream(children)
                .map(File::getName)
                .filter(name -> !com.ujcms.cms.core.composition.ThemeDirScanner.isTransientDir(name))
                .filter(name -> themeStateMapper.selectByUnique(siteId, name) == null)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    // ----- update ---------------------------------------------------------

    /**
     * Update to a new bundle version with fill-only seed. Convenience 4-arg form.
     */
    public UpdateReport update(MultipartFile zipPart, Long siteId, String themeName, SeedScope seedScope) {
        return update(zipPart, siteId, themeName, seedScope, SeedOverwrite.FILL_ONLY);
    }

    /**
     * Update an installed theme to a new bundle version (§12.2). Staged validation before any
     * replacement: extract to a temp staging dir, validate composition artifacts, then atomically
     * replace the live theme dir; on validation failure the old files stay untouched (rollback).
     * Returns a per-category seed diff ({@code SeedPlan}: added / seedChanged / operatorModified
     * via {@code origin_hash_}, §6.2 / R4) computed against the new version's seed before any
     * re-import, so operators can decide {@code fillOnly} / {@code syncUnmodified} /
     * {@code resetAllOrigin} — one-shot via {@code overwrite}, or later via the decoupled
     * {@code seed/import}. Guarded by the per-site lifecycle mutex.
     *
     * @param seedScope what seed data to (idempotently) re-import after replacing files (R5)
     * @param overwrite how the re-import treats existing origin rows (§6.1)
     */
    public UpdateReport update(MultipartFile zipPart, Long siteId, String themeName, SeedScope seedScope,
                               SeedOverwrite overwrite) {
        validateThemeName(themeName);
        File tempZip;
        try {
            tempZip = Files.createTempFile("ujcms-theme-update-", ".zip").toFile();
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to create temp file: " + e.getMessage(), e);
        }
        try {
            zipPart.transferTo(tempZip);
            return lifecycleLock.withSiteLock(siteId, () ->
                    doUpdate(tempZip, siteId, themeName, seedScope, overwrite));
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to read uploaded file: " + e.getMessage(), e);
        } finally {
            FileUtils.deleteQuietly(tempZip);
        }
    }

    /**
     * 文件入径更新（remote-marketplace C2，D-C1-5 的更新面）：包已在本地磁盘（市场下载的
     * 临时文件）时的入口，与 Multipart 入径共用同一条 doUpdate 管线——暂存校验 / 原子替换 /
     * 种子差异（origin_hash_ 三态）分毫不差。{@code zipFile} 的生命周期归调用方。
     */
    public UpdateReport update(Path zipFile, Long siteId, String themeName, SeedScope seedScope,
                               SeedOverwrite overwrite) {
        validateThemeName(themeName);
        return lifecycleLock.withSiteLock(siteId, () ->
                doUpdate(zipFile.toFile(), siteId, themeName, seedScope, overwrite));
    }

    private UpdateReport doUpdate(File zipFile, Long siteId, String themeName, SeedScope seedScope,
                                  SeedOverwrite overwrite) {
        File themeDir = themeDir(siteId, themeName);
        if (!themeDir.exists()) {
            throw new ThemeInstallException("theme.install.error.notFound",
                    "Theme '" + themeName + "' not found. Install it first.");
        }
        File staging = new File(themeDir.getParentFile(), themeName + ".staging-" + System.identityHashCode(zipFile));
        ThemeInstallReport report;
        try {
            try (InputStream in = new FileInputStream(zipFile)) {
                report = validator.validate(in, zipFile.length(), maxSizeBytes());
            }
            String sha256 = sha256Hex(zipFile);
            // 版本兼容门槛（H2）：替换文件前拦下，旧版本原样保留
            requireVersionCompatible(report.getMetadata(), themeName);
            // 生效中的主题不可换成纯数据包（不变量：纯数据包永不处于激活态）；替换文件前拦下
            if (isDataOnly(report.getMetadata()) && !sitesUsingTheme(siteId, themeName).isEmpty()) {
                throw new ThemeInstallException("theme.install.error.notActivatable",
                        "Theme '" + themeName + "' is active; it cannot be replaced by a "
                                + "data-only package (kind: \"data-only\"). Switch the site "
                                + "to another theme first.");
            }
            FileUtils.deleteQuietly(staging);
            try (InputStream in = new FileInputStream(zipFile)) {
                extractTo(in, staging);
            }
            // 候选快照校验门槛：暂存目录自洽才替换（§12.2 staged validation before replacement）
            List<String> errors = manifestService.validateThemeArtifacts(siteId + "/" + themeName, staging);
            if (errors != null && !errors.isEmpty()) {
                throw new ThemeInstallException("theme.install.error.compositionInvalid",
                        "New theme version's composition artifacts are invalid: " + String.join("; ", errors));
            }
            // 移旧到一旁 → 移入暂存 → 删旧备份（§12.2 / §21 Q12(d)）。此前是「删旧→移入」，一旦移入失败
            // 或期间崩溃，新旧两份都丢失；改为先备份旧目录、移入成功后才删备份，移入失败则回滚备份。
            swapInPlace(themeDir, staging, siteId, themeName);
            reloadRegistry();
            recordThemeVersion(siteId, themeName, report.getMetadata());

            // 逐类别种子差异（§6.2 / R4）：换包后、导种子前对比——added（新版新增）/
            // seedChanged（种子内容变了）/ operatorModified（操作员改过），二者由散列分辨、不再混同
            // （取代 A0 的单一 operatorEditedCards 计数，pre-release 干净切换）
            SeedPlan seedDiff = seedOrchestrator.plan(siteId, themeName, themeDir, templateStorageUrl());

            SeedRunResult seed = null;
            if (seedScope.includesComposition()) {
                seed = seedOrchestrator.importSeed(siteId, themeName, themeDir, templateStorageUrl(),
                        currentUserId(), seedScope.includesContent(), overwrite);
                logger.info("Theme update seed: site={} name={} overwrite={} {}",
                        siteId, themeName, overwrite, seed);
            }
            RegistrySnapshot snapshot = manifestService.getSnapshot();
            String themeKey = siteId + "/" + themeName;
            UpdateReport result = new UpdateReport(
                    snapshot.getThemeShapes().getOrDefault(themeKey, Map.of()).size(),
                    snapshot.getThemeManifests().getOrDefault(themeKey, Map.of()).size(),
                    snapshot.getThemeSettings(themeKey).getGroups().size(),
                    seedDiff, seed, sha256);
            logger.info("Theme updated: site={} name={} {}", siteId, themeName, result);
            return result;
        } catch (ThemeInstallException e) {
            FileUtils.deleteQuietly(staging);
            throw e;
        } catch (RuntimeException | IOException e) {
            FileUtils.deleteQuietly(staging);
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to update theme: " + e.getMessage(), e);
        }
    }

    /**
     * 就地替换主题目录（§12.2 / §21 Q12(d)）：现有目录先移到 {@code <theme>.backup-…} 一旁，再把已校验的
     * 暂存目录移入主题目录；成功后删备份。移入失败时（若主题目录尚不存在）从备份回滚，绝不同时丢失新旧两份。
     * 备份/暂存目录都带 {@code .backup-}/{@code .staging-} 中缀，扫描器会跳过（{@link ThemeDirScanner#isTransientDir}）。
     */
    private void swapInPlace(File themeDir, File staging, Long siteId, String themeName) throws IOException {
        File backup = new File(themeDir.getParentFile(),
                themeName + ".backup-" + System.identityHashCode(staging));
        FileUtils.deleteQuietly(backup);
        FileUtils.moveDirectory(themeDir, backup);
        try {
            FileUtils.moveDirectory(staging, themeDir);
        } catch (IOException | RuntimeException e) {
            // 移入失败：若主题目录仍不存在，从备份回滚旧版本（保证至少旧版本可用）
            if (!themeDir.exists()) {
                try {
                    FileUtils.moveDirectory(backup, themeDir);
                } catch (IOException | RuntimeException restoreErr) {
                    logger.error("Theme update rollback failed: site={} name={}; live theme is at {}",
                            siteId, themeName, backup, restoreErr);
                    throw restoreErr;
                }
            }
            throw e;
        }
        FileUtils.deleteQuietly(backup);
    }

    // ----- orphan analysis ------------------------------------------------

    /** Resulting active theme keys ({@code siteId/themeName}, deduped) after a prospective switch. */
    private List<String> resultingThemeKeys(Site site, String themeName, ActivateTarget target) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(2);
        // 每个槽位：随本次切换变为目标主题，否则保持该槽位当前主题
        keys.add(target.includesDesktop() ? site.getId() + "/" + themeName : site.getThemeKey());
        keys.add(target.includesMobile() ? site.getId() + "/" + themeName : site.getMobileThemeKey());
        return new ArrayList<>(keys);
    }

    /**
     * Partition of the site's placements against the resulting theme pair (§12.3 / D21 / D23):
     * an active placement is an orphan (to archive) when its (page, region) is declared by NEITHER
     * resulting theme <b>or</b> its shape resolves in neither theme namespace nor core (D23 — a
     * declared slot alone would let the previous theme's panel render as 未知面板类型 in production);
     * an archived placement whose (page, region) IS declared again AND whose shape resolves is to
     * restore — activation auto-restores them, so a theme round trip needs no manual Health work
     * (mirrors template selections' 切回即恢复).
     */
    private PlacementPartition partitionPlacements(Long siteId, List<String> resultingThemeKeys) {
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        Set<String> declaredSlots = new java.util.HashSet<>();
        for (String themeKey : resultingThemeKeys) {
            snapshot.getThemeManifests().getOrDefault(themeKey, Map.of()).forEach((page, manifest) ->
                    manifest.getSlots().keySet().forEach(slot -> declaredSlots.add(page + "/" + slot)));
        }
        List<Placement> toArchive = new ArrayList<>();
        List<Placement> toRestore = new ArrayList<>();
        for (Placement p : placementService.listBySiteId(siteId)) {
            boolean fits = declaredSlots.contains(p.getPageRef() + "/" + p.getSlotName())
                    && p.getPanelShape() != null
                    && snapshot.resolveTrackShape(resultingThemeKeys, p.getPanelShape()) != null;
            if (!p.isArchived() && !fits) {
                toArchive.add(p);
            } else if (p.isArchived() && fits) {
                toRestore.add(p);
            }
        }
        return new PlacementPartition(toArchive, toRestore);
    }

    // 孤儿一律归档（投放置 archived_at_），重新被声明的归档行自动恢复（D21）——切换往返零手工；
    // 删除是健康页的显式动作（逐行或清空）
    private void applyPlacementPartition(PlacementPartition partition) {
        partition.toArchive().forEach(p -> placementService.archive(p.getId()));
        partition.toRestore().forEach(p -> placementService.restore(p.getId()));
    }

    private static boolean hasSeed(File themeDir) {
        File seedDir = new File(themeDir, "seed");
        if (!seedDir.isDirectory()) {
            return false;
        }
        // 构成种子（seed/regions/）或内容种子（seed/content/，A2）任一存在即视为有种子
        return hasFiles(new File(seedDir, "regions")) || new File(seedDir, "content").isDirectory();
    }

    private static boolean hasFiles(File dir) {
        File[] files = dir.listFiles(File::isFile);
        return files != null && files.length > 0;
    }

    /** 包内共享（install-flow §14：登记后重扫由 {@link ThemePackageService} 复用）。 */
    void reloadRegistry() {
        try {
            // 触发人记用户名，与后台手动重扫一致（健康页加载记录直接展示）
            User user = Contexts.findCurrentUser();
            manifestService.scanAll(RegistryReload.SOURCE_INSTALL, user == null ? null : user.getUsername());
        } catch (RuntimeException e) {
            // 重扫失败（如另一主题制品损坏）：保留旧快照，记录日志；新主题文件已就位且单独校验通过
            logger.warn("Registry reload after theme op failed, old snapshot stays: {}", e.getMessage());
        }
    }

    private void recordThemeVersion(Long siteId, String themeName, @Nullable ThemeMetadata metadata) {
        if (metadata != null && metadata.getVersion() != null) {
            themeSettingsService.recordVersion(siteId, themeName, metadata.getVersion());
        }
    }

    /**
     * 模板存储的公开 URL 前缀（{@code {{theme}}} 展开、A4 画廊截图 URL 拼装用）。
     */
    public String templateStorageUrl() {
        return configService.getUnique().getTemplateStorage().getUrl();
    }

    @Nullable
    private Long currentUserId() {
        User user = Contexts.findCurrentUser();
        return user != null ? user.getId() : null;
    }

    // ----- helpers --------------------------------------------------------

    private long maxSizeBytes() {
        return ThemeInstallValidator.megabytesToBytes(maxSizeMb);
    }

    /**
     * 投放分区（§12.3 / D21）：{@code toArchive} = 孤立投放（归档），{@code toRestore} =
     * 页面/区域重新被声明的归档投放（激活时自动恢复）。
     */
    private record PlacementPartition(List<Placement> toArchive, List<Placement> toRestore) {
    }

    /**
     * 种子导入范围（R5，§6.1 政策词汇）。替代 {@code importSeed} 布尔——内容种子（Phase A2）落地后
     * {@code ALL} 才与 {@code COMPOSITION} 有别；A0 阶段两者行为一致（仅构成种子存在）。
     */
    public enum SeedScope {
        /** 不导入 */
        NONE,
        /** 仅构成种子（卡片 / 投放 / 区域） */
        COMPOSITION,
        /** 全部：内容种子（栏目 / 文章 / 专题，A2）+ 构成种子 */
        ALL;

        /**
         * 是否包含构成种子
         */
        public boolean includesComposition() {
            return this != NONE;
        }

        /**
         * 是否包含内容种子（栏目/文章/专题，A2）
         */
        public boolean includesContent() {
            return this == ALL;
        }

        /**
         * JSON / 表单值使用小写（{@code none|composition|all}）
         */
        @com.fasterxml.jackson.annotation.JsonCreator
        public static SeedScope parse(String value) {
            return valueOf(value.toUpperCase(java.util.Locale.ROOT));
        }

        @com.fasterxml.jackson.annotation.JsonValue
        public String toJson() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * 激活目标：主题进入站点的哪个槽位（{@code Site.theme} 桌面 / {@code Site.mobileTheme} 手机 / 两者）。
     * 取代旧的 {@code alsoMobile} 布尔——它只能表达「桌面」或「桌面+手机」，没有「仅手机」路径；
     * 手机参考主题（{@code mobile}）落地后「仅手机」才成为日常动作（theme-marketplace §3 R8）。
     */
    public enum ActivateTarget {
        /** 仅桌面槽位 */
        DESKTOP,
        /** 仅手机槽位 */
        MOBILE,
        /** 桌面 + 手机 */
        BOTH;

        /**
         * 是否改写桌面槽位
         */
        public boolean includesDesktop() {
            return this != MOBILE;
        }

        /**
         * 是否改写手机槽位
         */
        public boolean includesMobile() {
            return this != DESKTOP;
        }

        /**
         * JSON / 表单值使用小写（{@code desktop|mobile|both}）
         */
        @com.fasterxml.jackson.annotation.JsonCreator
        public static ActivateTarget parse(String value) {
            return valueOf(value.toUpperCase(java.util.Locale.ROOT));
        }

        @com.fasterxml.jackson.annotation.JsonValue
        public String toJson() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * 卸载数据策略（主题来源行，§12.4）
     */
    public enum UninstallDataPolicy {
        /** 保留：清除 origin_，转为普通操作员行 */
        KEEP,
        /** 归档 */
        ARCHIVE,
        /** 删除 */
        DELETE
    }

    /**
     * 主题更新报告（§12.2；A3 起带逐类别种子差异）
     *
     * @param themeShapes    更新后主题 Shape 数
     * @param themePages     更新后主题页面数
     * @param settingsGroups 更新后设置分组数
     * @param seedDiff       逐类别种子差异（换包后、导种子前：new=新版新增 / seedChanged=种子内容变了 /
     *                       operatorModified=操作员改过，§6.2 / R4——取代 A0 的 operatorEditedCards）
     * @param seed           本次种子导入结果（seedScope=none 时为 null）
     * @param sha256         上传 ZIP 的 SHA-256（十六进制小写；P3-1 / H3——供带外比对，不做签名/PKI）
     */
    public record UpdateReport(int themeShapes, int themePages, int settingsGroups,
                               SeedPlan seedDiff, @Nullable SeedRunResult seed, String sha256) {
    }

    /**
     * 上传 ZIP 的 SHA-256（十六进制小写；design-review P3-1 / H3——安装报告与日志展示，供带外比对，
     * 不做签名/PKI）。
     */
    private static String sha256Hex(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to hash uploaded file: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve {@code templates/{siteId}/{themeName}/} via the configured template storage.
     * Mirrors {@code SiteController.appendTheme} so the picker and installer agree on path.
     */
    public File themeDir(Long siteId, String themeName) {
        String storagePath = templateStoragePath();
        try {
            Resource resource = resourceLoader.getResource(storagePath + "/" + siteId + "/" + themeName);
            return resource.getFile();
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to resolve theme path: " + e.getMessage(), e);
        }
    }

    private File siteDir(Long siteId) {
        String storagePath = templateStoragePath();
        try {
            return resourceLoader.getResource(storagePath + "/" + siteId).getFile();
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to resolve site themes path: " + e.getMessage(), e);
        }
    }

    private String templateStoragePath() {
        return configService.getUnique().getTemplateStorage().getPath();
    }

    /** 包内共享（install-flow §14：登记入径的站点在场校验——D41 窄例前提——由 {@link ThemePackageService} 复用）。 */
    Site requireSite(Long siteId) {
        Site site = siteService.select(siteId);
        if (site == null) {
            throw new ThemeInstallException("theme.install.error.siteNotFound",
                    "Site not found: id=" + siteId);
        }
        return site;
    }

    private List<Site> sitesUsingTheme(Long siteId, String themeName) {
        // Themes are per-site copies (per spec), so only the owning site can reference them.
        // Still, return a list rather than a single site so the error message format scales if
        // we ever introduce shared themes.
        Site site = siteService.select(siteId);
        List<Site> bound = new ArrayList<>();
        // Site.theme / mobileTheme store the bare theme name (R2); compare directly.
        if (site != null && (themeName.equals(site.getTheme())
                || themeName.equals(site.getMobileTheme()))) {
            bound.add(site);
        }
        return bound;
    }

    @Nullable
    private ThemeMetadata readMetadata(File themeDir) {
        File metaFile = new File(themeDir, "theme.manifest.json");
        if (!metaFile.isFile() || metaFile.length() == 0) return null;
        try {
            return objectMapper.readValue(metaFile, ThemeMetadata.class);
        } catch (IOException e) {
            logger.warn("Theme metadata unreadable for {}: {}", themeDir, e.getMessage());
            return null;
        }
    }

    /**
     * 主题名合法性（包内共享：导出编排 {@link ThemeSeedExportService} 同一门槛）。
     */
    void validateThemeName(String themeName) {
        if (themeName == null || !THEME_NAME.matcher(themeName).matches()) {
            throw new ThemeInstallException("theme.install.error.invalidName",
                    "Invalid theme name. Use letters, digits, '.', '_', '-' only (max 50 chars).");
        }
        String lower = themeName.toLowerCase(Locale.ROOT);
        for (String reserved : RESERVED_NAMES) {
            if (reserved.equalsIgnoreCase(lower)) {
                throw new ThemeInstallException("theme.install.error.reservedName",
                        "Theme name '" + themeName + "' is reserved.");
            }
        }
    }

    /**
     * Extract the validated archive entry-by-entry. Re-validates path safety as a
     * defense-in-depth measure even though the validator has already accepted the archive.
     */
    private void extractTo(InputStream zipInput, File themeDir) throws IOException {
        if (!themeDir.mkdirs() && !themeDir.isDirectory()) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to create theme directory: " + themeDir);
        }
        Path themeRoot = themeDir.toPath().toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(zipInput, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.contains("..") || entryName.startsWith("/") || entryName.contains("\\")) {
                    // Defense-in-depth — validator already rejected these.
                    throw new ThemeInstallException("theme.install.error.zipSlip",
                            "Refusing to extract suspect entry: " + entryName);
                }
                Path target = themeRoot.resolve(entryName).normalize();
                if (!target.startsWith(themeRoot)) {
                    throw new ThemeInstallException("theme.install.error.zipSlip",
                            "Entry escapes theme root: " + entryName);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (FileOutputStream out = new FileOutputStream(target.toFile())) {
                        IOUtils.copyLarge(zis, out);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    Optional<File> peekThemeDir(Long siteId, String themeName) {
        File f = themeDir(siteId, themeName);
        return f.exists() ? Optional.of(f) : Optional.empty();
    }
}
