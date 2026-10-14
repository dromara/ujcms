package com.ujcms.cms.core.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ujcms.cms.core.component.ThemeInstallException;
import com.ujcms.cms.core.composition.ExportPlan;
import com.ujcms.cms.core.composition.SeedExportBundle;
import com.ujcms.cms.core.composition.SeedExportRequest;
import com.ujcms.cms.core.composition.SeedOriginAdopter;
import com.ujcms.cms.core.composition.SeedPackageWriter;
import com.ujcms.cms.core.composition.ThemeLifecycleLock;
import com.ujcms.cms.core.composition.ThemeSeedExporter;
import com.ujcms.cms.core.composition.ThemeSeedImporter;
import com.ujcms.cms.core.composition.ThemeSettingsService;
import com.ujcms.cms.core.composition.ThemeSettingsSnapshotter;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.ThemeState;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.file.WebFile;
import com.ujcms.common.web.PathResolver;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 主题种子导出编排（theme-seed-export E2）：把 E1 引擎的只读文件束（{@link SeedExportBundle}）
 * 落成三种产物之一（§7.1）——
 * <ul>
 *   <li>{@code refreshSeedDir}：经模板存储 {@link FileHandler} 重写活主题目录的 {@code seed/**}
 *       （站点生命周期锁内，§8；仅限本次导出范围内的种子族——content/regions——之外的旧种子文件
 *       原样保留），并按发射类别更新清单 {@code seed.content} 标志；</li>
 *   <li>{@code dataOnlyZip}：合成 {@code kind: "data-only"} 清单 + {@code seed/**}，
 *       镜像 {@code build-seed-package.mjs --data-only}（只读、免锁）；</li>
 *   <li>{@code themeZip}：活主题目录（开发工件排除同 CLI）+ 刷新后的 {@code seed/**}（只读、免锁）。</li>
 * </ul>
 * ZIP 输出确定性（D4）且与 CLI 打包器字节一致（Q5——{@code SeedPackageWriterParityTest} 钉死）。
 * 大小预算（§7.3）：束或包超过 {@code ujcms.theme.install.maxSizeMb} 即快速失败并点名最大资源，
 * 绝不产出装不上的包。{@code adoptOrigin=true} 额外经 {@link SeedOriginAdopter} 认领第 3 类行
 * （D2 唯一豁免；对模式正交）。
 *
 * @author PONY
 */
@Service
public class ThemeSeedExportService {
    private static final Logger logger = LoggerFactory.getLogger(ThemeSeedExportService.class);

    private static final String MANIFEST_FILE = "theme.manifest.json";
    private static final String SEED_PREFIX_CONTENT = "content/";
    private static final String SEED_PREFIX_REGIONS = "regions/";
    private static final String DEFAULT_VERSION = "0.0.0";

    private final ThemeSeedExporter exporter;
    private final SeedOriginAdopter adopter;
    private final ThemeInstallService installService;
    private final ThemeLifecycleLock lifecycleLock;
    private final SiteService siteService;
    private final ConfigService configService;
    private final ThemeSettingsService settingsService;
    private final PathResolver pathResolver;
    private final ObjectMapper objectMapper;

    public ThemeSeedExportService(ThemeSeedExporter exporter, SeedOriginAdopter adopter,
                                  ThemeInstallService installService, ThemeLifecycleLock lifecycleLock,
                                  SiteService siteService, ConfigService configService,
                                  ThemeSettingsService settingsService,
                                  PathResolver pathResolver, ObjectMapper objectMapper) {
        this.exporter = exporter;
        this.adopter = adopter;
        this.installService = installService;
        this.lifecycleLock = lifecycleLock;
        this.siteService = siteService;
        this.configService = configService;
        this.settingsService = settingsService;
        this.pathResolver = pathResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * 导出干跑预览（§8 {@code export/preview}）：跑完整引擎导出、只返回计划报告。只读、免锁。
     * 设置快照报告恒附（§7.4：向导第 2 步先于第 3 步开关展示「若快照会写什么」）。
     */
    public ExportPlan preview(Long siteId, String themeName, SeedExportRequest request) {
        installService.validateThemeName(themeName);
        ExportPlan plan = exporter.export(siteId, themeName, request).plan();
        return plan.withSettingsSnapshot(computeSnapshotReport(siteId, themeName));
    }

    /**
     * ZIP 导出（§7.1 {@code dataOnlyZip} / {@code themeZip}）：只读、免锁（§8）。
     *
     * @param dataOnly         true = 纯数据包（清单合成 + seed/**）；false = 完整主题包（活目录 + 刷新种子）
     * @param adoptOrigin      是否认领第 3 类行（§4.2）
     * @param snapshotSettings 是否把已存设置值写入包内清单已声明字段的 {@code default}（§7.4；仅烘焙
     *                         入包、磁盘不动；纯数据包合成清单无设置结构——按「只写声明字段」自然为空写）
     * @param version          操作员指定版本（空 = 沿用现值；不自动递增，§7.1）
     */
    public ZipPackage exportZip(Long siteId, String themeName, SeedExportRequest request,
                                boolean dataOnly, boolean adoptOrigin, boolean snapshotSettings,
                                @Nullable String version) {
        installService.validateThemeName(themeName);
        SeedExportBundle bundle = exporter.export(siteId, themeName, request);
        requireWithinBudget(bundle.plan());

        ObjectNode liveManifest = readLiveManifest(siteId, themeName);
        List<SeedPackageWriter.Entry> entries;
        String resolvedVersion;
        ExportPlan plan = bundle.plan();
        if (dataOnly) {
            resolvedVersion = resolveVersion(version, liveManifest, DEFAULT_VERSION);
            byte[] manifest = manifestBytes(dataOnlyManifest(themeName, liveManifest, resolvedVersion,
                    bundle, request));
            entries = new ArrayList<>(bundle.files().size() + 1);
            bundle.files().forEach((relpath, bytes) ->
                    entries.add(new SeedPackageWriter.Entry("seed/" + relpath, bytes)));
            entries.add(new SeedPackageWriter.Entry(MANIFEST_FILE, manifest));
            entries.sort(Comparator.comparing(SeedPackageWriter.Entry::name));
        } else {
            File themeDir = requireThemeDir(siteId, themeName);
            resolvedVersion = resolveVersion(version, liveManifest, null);
            ObjectNode manifest = liveManifest != null ? liveManifest
                    : synthesizedManifest(themeName, resolvedVersion == null ? DEFAULT_VERSION : resolvedVersion);
            applyManifestUpdates(manifest, resolvedVersion, bundle, request);
            ThemeSettingsSnapshotter snapshotter = snapshotSettings ? snapshotter(siteId, themeName) : null;
            if (snapshotter != null) {
                snapshotter.applyThemeManifest(manifest);
            }
            entries = SeedPackageWriter.walkThemeDir(themeDir,
                    mergedSeed(siteId, themeName, bundle, request), manifestBytes(manifest));
            if (snapshotter != null) {
                snapshotPackagedPageManifests(entries, snapshotter);
                plan = plan.withSettingsSnapshot(snapshotter.report());
            }
            List<String> violations = SeedPackageWriter.compositionJsonViolations(entries);
            if (!violations.isEmpty()) {
                throw new ThemeInstallException("theme.export.error.compositionNonJson",
                        "Theme dir has non-JSON files in composition dirs (only seed/content/assets/** "
                                + "may carry media): " + String.join(", ", violations));
            }
        }
        byte[] zip = SeedPackageWriter.zip(entries);
        requirePackageWithinBudget(entries, zip, bundle.plan());
        int adopted = adoptIfRequested(themeName, bundle, adoptOrigin);
        String filename = themeName + (resolvedVersion != null ? "-" + resolvedVersion : "") + ".zip";
        logger.info("Theme seed export zip: site={} theme={} dataOnly={} entries={} zipBytes={} adopted={}",
                siteId, themeName, dataOnly, entries.size(), zip.length, adopted);
        return new ZipPackage(filename, zip, plan, adopted);
    }

    /**
     * 就地刷新活主题目录的 {@code seed/**}（§7.1 {@code refreshSeedDir}）：站点生命周期锁内
     * （与安装/更新/监听器重扫互斥）。仅重写本次导出范围内种子族（content/regions）的文件并
     * 删除其中不再存在的旧文件；范围外的种子文件与主题其余文件不触碰。清单按发射类别更新
     * {@code seed.content} 标志（语义无变化则不写盘——保留操作员手写格式）。
     *
     * <p>Q1（§12）：活动主题允许刷新——只写 {@code seed/**}（渲染器文件不动，安装侧会再校验），
     * 但必须显式 {@code confirmActive}（控制台确认框的服务端形）。
     *
     * <p>§7.4：{@code snapshotSettings=true} 额外把已存设置值写入主题清单与页面清单已声明字段的
     * {@code default}（同样语义无变化不写盘）。
     */
    public RefreshReport refreshSeedDir(Long siteId, String themeName, SeedExportRequest request,
                                        boolean adoptOrigin, boolean snapshotSettings,
                                        @Nullable String version, boolean confirmActive) {
        installService.validateThemeName(themeName);
        requireThemeDir(siteId, themeName);
        Site site = siteService.select(siteId);
        boolean active = site != null
                && (themeName.equals(site.getTheme()) || themeName.equals(site.getMobileTheme()));
        if (active && !confirmActive) {
            throw new ThemeInstallException("theme.export.error.activeThemeConfirm",
                    "Theme '" + themeName + "' is the active theme of this site. Refreshing seed/** "
                            + "is allowed but must be confirmed (confirmActive=true).");
        }
        return lifecycleLock.withSiteLock(siteId, () -> {
            SeedExportBundle bundle = exporter.export(siteId, themeName, request);
            requireWithinBudget(bundle.plan());
            FileHandler fileHandler = templateFileHandler();
            String themePath = "/" + siteId + "/" + themeName;
            String seedPath = themePath + "/seed";
            // 先列旧、再写新、后删陈——任何时刻新文件先于旧文件消失
            List<String> existing = new ArrayList<>();
            if (fileHandler.isDirectory(seedPath)) {
                listFilesRecursive(fileHandler, seedPath, existing);
            }
            bundle.files().forEach((relpath, bytes) ->
                    fileHandler.store(seedPath + "/" + relpath, new ByteArrayInputStream(bytes)));
            int removed = 0;
            String seedFilePrefix = seedPath + "/";
            for (String path : existing) {
                String relpath = path.substring(seedFilePrefix.length());
                if (managedPrefix(relpath, request) && !bundle.files().containsKey(relpath)) {
                    if (fileHandler.deleteFileAndEmptyParentDir(path)) {
                        removed++;
                    }
                }
            }
            ThemeSettingsSnapshotter snapshotter = snapshotSettings ? snapshotter(siteId, themeName) : null;
            boolean manifestUpdated = refreshManifest(fileHandler, themePath, themeName, version,
                    bundle, request, snapshotter);
            ExportPlan plan = bundle.plan();
            if (snapshotter != null) {
                manifestUpdated |= refreshPageManifests(fileHandler, themePath, snapshotter) > 0;
                plan = plan.withSettingsSnapshot(snapshotter.report());
            }
            int adopted = adoptIfRequested(themeName, bundle, adoptOrigin);
            logger.info("Theme seed refresh: site={} theme={} written={} removed={} manifestUpdated={} adopted={}",
                    siteId, themeName, bundle.files().size(), removed, manifestUpdated, adopted);
            return new RefreshReport(bundle.files().size(), removed, manifestUpdated, adopted, plan);
        });
    }

    // ----- 认领（§4.2）-----

    private int adoptIfRequested(String themeName, SeedExportBundle bundle, boolean adoptOrigin) {
        if (!adoptOrigin || bundle.adoptions().isEmpty()) {
            return 0;
        }
        return adopter.adopt(ThemeSeedImporter.originOf(themeName), bundle.adoptions());
    }

    // ----- 大小预算（§7.3）-----

    /**
     * 束级预算：文件束（种子 JSON + 资源）超限即快速失败，点名最大资源文件。
     */
    private void requireWithinBudget(ExportPlan plan) {
        if (plan.oversize()) {
            throw new ThemeInstallException("theme.export.error.oversize", oversizeDetail(plan));
        }
    }

    /**
     * 包级预算（镜像 CLI）：未压缩总量与 ZIP 字节数都不得超过安装上限——完整主题包的
     * 渲染器文件也计入。
     */
    private void requirePackageWithinBudget(List<SeedPackageWriter.Entry> entries, byte[] zip,
                                            ExportPlan plan) {
        long uncompressed = entries.stream().mapToLong(entry -> entry.data().length).sum();
        if (uncompressed > plan.maxBytes() || zip.length > plan.maxBytes()) {
            throw new ThemeInstallException("theme.export.error.oversize",
                    "Package exceeds the install cap (uncompressed " + uncompressed + " bytes, zip "
                            + zip.length + " bytes, cap " + plan.maxBytes() + " bytes). " + oversizeDetail(plan));
        }
    }

    private static String oversizeDetail(ExportPlan plan) {
        StringBuilder sb = new StringBuilder("Export exceeds ujcms.theme.install.maxSizeMb (total ")
                .append(plan.totalBytes()).append(" bytes, cap ").append(plan.maxBytes())
                .append(" bytes). Largest assets: ");
        if (plan.largestAssets().isEmpty()) {
            sb.append("(none)");
        } else {
            sb.append(plan.largestAssets().stream()
                    .map(asset -> asset.relpath() + " (" + asset.bytes() + " bytes)")
                    .reduce((a, b) -> a + ", " + b).orElse(""));
        }
        return sb.toString();
    }

    // ----- 清单（§7.1：seed.content 标志 + 操作员版本，不自动递增）-----

    /**
     * 数据包清单合成（镜像 CLI {@code --data-only} 的合成形，键序一致：name/themeName/version/
     * templateSpecVersion/kind；展示名沿用活清单的 {@code name}）。
     */
    private ObjectNode dataOnlyManifest(String themeName, @Nullable ObjectNode liveManifest,
                                        String version, SeedExportBundle bundle, SeedExportRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        String display = liveManifest != null && liveManifest.path("name").isTextual()
                && !liveManifest.path("name").asText().isBlank()
                ? liveManifest.path("name").asText() : themeName;
        root.put("name", display);
        root.put("themeName", themeName);
        root.put("version", version);
        root.put("templateSpecVersion", "2.0");
        root.put("kind", "data-only");
        applyManifestUpdates(root, null, bundle, request);
        return root;
    }

    private ObjectNode synthesizedManifest(String themeName, String version) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("name", themeName);
        root.put("themeName", themeName);
        root.put("version", version);
        root.put("templateSpecVersion", "2.0");
        return root;
    }

    /**
     * 清单更新（§7.1）：操作员版本（非空才写——<b>不自动递增</b>）+ {@code seed.content} 声明标志
     * 按本次发射的类别落实（真假都写——过期的 true 声明与缺失的声明同样误导）。构成范围不含内容时
     * 不触碰 {@code seed.content}（内容种子未刷新，声明照旧）。
     */
    private void applyManifestUpdates(ObjectNode manifest, @Nullable String version,
                                      SeedExportBundle bundle, SeedExportRequest request) {
        if (version != null && !version.isBlank()) {
            manifest.put("version", version);
        }
        if (!request.includeContent()) {
            return;
        }
        JsonNode seedNode = manifest.get("seed");
        ObjectNode seed = seedNode instanceof ObjectNode node ? node : manifest.putObject("seed");
        JsonNode contentNode = seed.get("content");
        ObjectNode content = contentNode instanceof ObjectNode node ? node : seed.putObject("content");
        content.put("channels", bundle.files().containsKey("content/channels.channels.json"));
        content.put("articles", hasFileUnder(bundle, "content/articles/"));
        content.put("topics", hasFileUnder(bundle, "content/topics/"));
        content.put("blocks", hasFileUnder(bundle, "content/blocks/"));
        content.put("votes", hasFileUnder(bundle, "content/votes/"));
        content.put("surveys", hasFileUnder(bundle, "content/surveys/"));
        content.put("models", hasFileUnder(bundle, "content/models/"));
        content.put("dicts", hasFileUnder(bundle, "content/dicts/"));
        content.put("forms", hasFileUnder(bundle, "content/forms/"));
        content.put("assets", hasFileUnder(bundle, "content/assets/"));
        // 新键置尾：既有清单的 content 节点按原键序更新，新增键恒追加——检入清单与写入器同形（D4）
        content.put("linkGroups", hasFileUnder(bundle, "content/link-groups/"));
    }

    private static boolean hasFileUnder(SeedExportBundle bundle, String prefix) {
        SortedMap<String, byte[]> tail = bundle.files().tailMap(prefix);
        return !tail.isEmpty() && tail.firstKey().startsWith(prefix);
    }

    /**
     * 就地清单刷新：读活清单（缺失则合成最小形）→ 落实更新（+可选设置快照，§7.4）→
     * 语义无变化则不写盘（保留操作员手写格式；首次刷新会规范化为两空格缩进 + LF）。
     */
    private boolean refreshManifest(FileHandler fileHandler, String themePath, String themeName,
                                    @Nullable String version, SeedExportBundle bundle,
                                    SeedExportRequest request, @Nullable ThemeSettingsSnapshotter snapshotter) {
        String manifestPath = themePath + "/" + MANIFEST_FILE;
        ObjectNode existing = readManifest(fileHandler, manifestPath);
        ObjectNode updated = existing != null ? existing.deepCopy()
                : synthesizedManifest(themeName, version != null && !version.isBlank()
                        ? version : DEFAULT_VERSION);
        applyManifestUpdates(updated, version, bundle, request);
        if (snapshotter != null) {
            snapshotter.applyThemeManifest(updated);
        }
        if (updated.equals(existing)) {
            return false;
        }
        fileHandler.store(manifestPath, new ByteArrayInputStream(manifestBytes(updated)));
        return true;
    }

    // ----- 设置快照（§7.4 / D21）-----

    /**
     * 本次导出的设置快照器：已存值取 {@code theme_state.settings_json_}（无行 = 空——快照零写入），
     * URL 可移植性识别用上传存储展示前缀 + 主题目录前缀（与反向资源遍历同口径）。
     */
    private ThemeSettingsSnapshotter snapshotter(Long siteId, String themeName) {
        ThemeState state = settingsService.findState(siteId, themeName);
        ThemeState.SettingValues stored = state != null ? state.getSettings() : new ThemeState.SettingValues();
        Config config = configService.getUnique();
        String uploadPrefix = config.getUploadStorage().getFileHandler(pathResolver).getDisplayPrefix();
        String templateUrl = config.getTemplateStorage().getUrl();
        String base = templateUrl.endsWith("/") ? templateUrl.substring(0, templateUrl.length() - 1)
                : templateUrl;
        return new ThemeSettingsSnapshotter(stored, uploadPrefix, base + "/" + siteId + "/" + themeName);
    }

    /**
     * 干跑快照报告（§7.4：预览恒计算——对活清单与页面清单模拟应用、不落盘）。
     */
    private ExportPlan.SettingsSnapshot computeSnapshotReport(Long siteId, String themeName) {
        ThemeSettingsSnapshotter snapshotter = snapshotter(siteId, themeName);
        ObjectNode manifest = readLiveManifest(siteId, themeName);
        snapshotter.applyThemeManifest(manifest != null ? manifest : objectMapper.createObjectNode());
        FileHandler fileHandler = templateFileHandler();
        for (String path : pageManifestPaths(fileHandler, "/" + siteId + "/" + themeName)) {
            ObjectNode node = readManifest(fileHandler, path);
            if (node != null) {
                snapshotter.applyPageManifest("pages/" + path.substring(path.lastIndexOf('/') + 1), node);
            }
        }
        return snapshotter.report();
    }

    /**
     * 就地页面清单快照（{@code refreshSeedDir}）：逐个 {@code pages/*.manifest.json} 应用页面作用域
     * 已存值，语义有变才写盘（与主题清单同约定）。
     *
     * @return 实际写盘的页面清单数
     */
    private int refreshPageManifests(FileHandler fileHandler, String themePath,
                                     ThemeSettingsSnapshotter snapshotter) {
        int updated = 0;
        for (String path : pageManifestPaths(fileHandler, themePath)) {
            ObjectNode node = readManifest(fileHandler, path);
            if (node != null && snapshotter.applyPageManifest(
                    "pages/" + path.substring(path.lastIndexOf('/') + 1), node)) {
                fileHandler.store(path, new ByteArrayInputStream(manifestBytes(node)));
                updated++;
            }
        }
        return updated;
    }

    /**
     * 包内页面清单快照（{@code themeZip}）：语义有变的条目以规范化字节替换，无变化的条目
     * 原字节随包（不产生格式噪音）。磁盘不动。
     */
    private void snapshotPackagedPageManifests(List<SeedPackageWriter.Entry> entries,
                                               ThemeSettingsSnapshotter snapshotter) {
        for (int i = 0; i < entries.size(); i++) {
            SeedPackageWriter.Entry entry = entries.get(i);
            if (!entry.name().startsWith("pages/") || !entry.name().endsWith(".manifest.json")) {
                continue;
            }
            ObjectNode node;
            try {
                JsonNode parsed = objectMapper.readTree(entry.data());
                node = parsed instanceof ObjectNode object ? object : null;
            } catch (IOException e) {
                node = null;
            }
            if (node != null && snapshotter.applyPageManifest(entry.name(), node)) {
                entries.set(i, new SeedPackageWriter.Entry(entry.name(), manifestBytes(node)));
            }
        }
    }

    /**
     * 主题目录 {@code pages/} 下的页面清单存储路径（直接子文件、按名排序——扫描器同口径 + 确定序）。
     */
    private List<String> pageManifestPaths(FileHandler fileHandler, String themePath) {
        String pagesPath = themePath + "/pages";
        if (!fileHandler.isDirectory(pagesPath)) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (WebFile file : fileHandler.listFiles(pagesPath)) {
            if (!file.isDirectory() && file.getId().endsWith(".manifest.json")) {
                paths.add(file.getId());
            }
        }
        paths.sort(String::compareTo);
        return paths;
    }

    @Nullable
    private ObjectNode readLiveManifest(Long siteId, String themeName) {
        return readManifest(templateFileHandler(), "/" + siteId + "/" + themeName + "/" + MANIFEST_FILE);
    }

    @Nullable
    private ObjectNode readManifest(FileHandler fileHandler, String manifestPath) {
        if (!fileHandler.isFile(manifestPath)) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        fileHandler.writeOutputStream(manifestPath, out);
        try {
            JsonNode node = objectMapper.readTree(out.toByteArray());
            return node instanceof ObjectNode object ? object : null;
        } catch (IOException e) {
            logger.warn("Theme manifest unreadable, treated as absent: {} ({})", manifestPath, e.getMessage());
            return null;
        }
    }

    /**
     * 清单字节（两空格缩进 + LF + 结尾换行；<b>保留键序</b>——与规范化种子序列化不同，
     * 清单是操作员文档，字典序重排徒增噪音）。
     */
    private byte[] manifestBytes(ObjectNode manifest) {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(
                Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER));
        printer.indentObjectsWith(new DefaultIndenter("  ", "\n"));
        printer.indentArraysWith(new DefaultIndenter("  ", "\n"));
        try {
            String json = objectMapper.writer(printer).writeValueAsString(manifest);
            return (json + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize theme manifest", e);
        }
    }

    // ----- 种子族合并（themeZip：范围外的磁盘旧种子随包保留）-----

    /**
     * themeZip 的 {@code seed/**} 替换图：导出束 ∪ 磁盘上范围外的既有种子文件
     * （如 E1 不发射的 {@code placements/}/{@code cards/} 种子——导出器无从再生，不得丢弃）。
     */
    private SortedMap<String, byte[]> mergedSeed(Long siteId, String themeName, SeedExportBundle bundle,
                                                 SeedExportRequest request) {
        TreeMap<String, byte[]> merged = new TreeMap<>(bundle.files());
        FileHandler fileHandler = templateFileHandler();
        String seedPath = "/" + siteId + "/" + themeName + "/seed";
        if (!fileHandler.isDirectory(seedPath)) {
            return merged;
        }
        List<String> existing = new ArrayList<>();
        listFilesRecursive(fileHandler, seedPath, existing);
        String seedFilePrefix = seedPath + "/";
        for (String path : existing) {
            String relpath = path.substring(seedFilePrefix.length());
            if (!managedPrefix(relpath, request) && !merged.containsKey(relpath)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                fileHandler.writeOutputStream(path, out);
                merged.put(relpath, out.toByteArray());
            }
        }
        return merged;
    }

    /**
     * 本次导出范围管辖的种子族前缀（刷新只增删自己范围内的文件）：内容范围管 {@code content/**}，
     * 构成范围管 {@code regions/**}；E1 不发射的其余种子目录恒不管辖。
     */
    private static boolean managedPrefix(String relpath, SeedExportRequest request) {
        return (request.includeContent() && relpath.startsWith(SEED_PREFIX_CONTENT))
                || (request.includeComposition() && relpath.startsWith(SEED_PREFIX_REGIONS));
    }

    // ----- helpers -----

    private FileHandler templateFileHandler() {
        return configService.getUnique().getTemplateStorage().getFileHandler(pathResolver);
    }

    private void listFilesRecursive(FileHandler fileHandler, String dir, List<String> out) {
        for (WebFile file : fileHandler.listFiles(dir)) {
            if (file.isDirectory()) {
                listFilesRecursive(fileHandler, file.getId(), out);
            } else {
                out.add(file.getId());
            }
        }
    }

    private File requireThemeDir(Long siteId, String themeName) {
        File themeDir = installService.themeDir(siteId, themeName);
        if (!themeDir.exists()) {
            throw new ThemeInstallException("theme.install.error.notFound",
                    "Theme '" + themeName + "' not found.");
        }
        return themeDir;
    }

    @Nullable
    private static String resolveVersion(@Nullable String version, @Nullable ObjectNode liveManifest,
                                         @Nullable String fallback) {
        if (version != null && !version.isBlank()) {
            return version;
        }
        if (liveManifest != null && liveManifest.path("version").isTextual()
                && !liveManifest.path("version").asText().isBlank()) {
            return liveManifest.path("version").asText();
        }
        return fallback;
    }

    /**
     * ZIP 导出产物（控制台以附件下载；{@code adopted} 为本次认领行数——认领与打包同请求完成）
     *
     * @param filename 下载文件名（{@code <themeName>-<version>.zip}，镜像 CLI 命名）
     * @param bytes    ZIP 字节
     * @param plan     导出计划报告
     * @param adopted  实际认领行数（{@code adoptOrigin=false} 时为 0）
     */
    public record ZipPackage(String filename, byte[] bytes, ExportPlan plan, int adopted) {
    }

    /**
     * {@code refreshSeedDir} 报告（§8：export 端点的 refresh 形响应）
     *
     * @param written         写入的种子文件数（导出束全量）
     * @param removed         删除的陈旧种子文件数（束外 + 本次范围内）
     * @param manifestUpdated 清单是否更新（主题清单或设置快照涉及的页面清单，§7.4；
     *                        语义无变化则不写盘）
     * @param adopted         实际认领行数（{@code adoptOrigin=false} 时为 0）
     * @param plan            导出计划报告（快照开启时附 {@code settingsSnapshot}）
     */
    public record RefreshReport(int written, int removed, boolean manifestUpdated, int adopted,
                                ExportPlan plan) {
    }
}
