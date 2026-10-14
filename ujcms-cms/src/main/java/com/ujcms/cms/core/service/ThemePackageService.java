package com.ujcms.cms.core.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ujcms.cms.core.component.ThemeInstallException;
import com.ujcms.cms.core.component.ThemeInstallReport;
import com.ujcms.cms.core.component.ThemeInstallValidator;
import com.ujcms.cms.core.component.ThemeMetadata;
import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.SeedOverwrite;
import com.ujcms.cms.core.composition.SeedPackageWriter;
import com.ujcms.cms.core.composition.ThemeLifecycleLock;
import com.ujcms.cms.core.composition.ThemeSettingsService;
import com.ujcms.cms.core.domain.ThemePackage;
import com.ujcms.cms.core.mapper.ThemePackageMapper;
import com.ujcms.cms.core.service.ThemeInstallService.ActivateTarget;
import com.ujcms.cms.core.service.ThemeInstallService.SeedScope;
import com.ujcms.cms.core.service.ThemeInstallService.UpdateReport;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.web.PathResolver;

/**
 * 主题包库（install-flow §6 P3 / D5）：站点无关的版本化 ZIP 制品库——包全局共享，
 * 实例严格按站点落地（决议 03 维持；永无 {@code /themes/global/*}）。
 *
 * <h2>一条管线</h2>
 * 四个写入源（市场下载 / 控制台上传 / 站点导出 / 构建期内置包）全部写穿本库：
 * 校验（{@link ThemeInstallValidator}，同安装口径）→ ZIP 落包存储点
 * （{@code Config.packageStorage}，Q1 独立存储点，布局 {@code <name>/<version>.zip}）→
 * 元数据行 upsert（同名同版本唯一，最后写入生效——库是包的家，不是不可变仓）。
 * 安装恒从库内副本读取：同一包装到第二个站点一键完成、零重传。
 *
 * <h2>内置默认包（D6）</h2>
 * 构建期以确定性打包器（{@code BundledThemePackager} over {@code SeedPackageWriter}，Q2
 * Java-in-build）把默认主题授权目录打成类路径资源 {@code bundled-themes/*.zip}；
 * 引导时注册入库（{@link #ensureBundledRegistered}，按 sha256 幂等）。webapp 预展开目录
 * 自此不再是首装的承重结构（裸 JAR 部署由 {@link InstallService#installTheme} 回退到
 * 库内内置包），但仍是开发期授权源——目录存在时绝不覆盖。
 *
 * @author PONY
 */
@Service
public class ThemePackageService {
    private static final Logger logger = LoggerFactory.getLogger(ThemePackageService.class);

    /**
     * 版本串合法性（入库即入存储路径 {@code <name>/<version>.zip}，与主题名同一字符集约束）
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,49}$");
    /**
     * 清单未声明版本时的入库版本（与导出侧 {@code DEFAULT_VERSION} 同值）
     */
    private static final String DEFAULT_VERSION = "0.0.0";
    /**
     * 构建期内置包的类路径位置（{@code ujcms-starter} 构建期由 {@code BundledThemePackager} 产出）
     */
    private static final String BUNDLED_LOCATION_PATTERN = "classpath*:bundled-themes/*.zip";

    private final ThemePackageMapper mapper;
    private final ThemeInstallValidator validator;
    private final ThemeInstallService themeInstallService;
    private final ConfigService configService;
    private final PathResolver pathResolver;
    private final ResourceLoader resourceLoader;
    private final SnowflakeSequence snowflakeSequence;
    private final ThemeLifecycleLock lifecycleLock;
    private final ManifestService manifestService;
    private final ThemeSettingsService themeSettingsService;

    public ThemePackageService(ThemePackageMapper mapper, ThemeInstallValidator validator,
                               ThemeInstallService themeInstallService, ConfigService configService,
                               PathResolver pathResolver, ResourceLoader resourceLoader,
                               SnowflakeSequence snowflakeSequence, ThemeLifecycleLock lifecycleLock,
                               ManifestService manifestService, ThemeSettingsService themeSettingsService) {
        this.mapper = mapper;
        this.validator = validator;
        this.themeInstallService = themeInstallService;
        this.configService = configService;
        this.pathResolver = pathResolver;
        this.resourceLoader = resourceLoader;
        this.snowflakeSequence = snowflakeSequence;
        this.lifecycleLock = lifecycleLock;
        this.manifestService = manifestService;
        this.themeSettingsService = themeSettingsService;
    }

    // ----- 查询 -----

    public List<ThemePackage> list() {
        return mapper.list();
    }

    @Nullable
    public ThemePackage select(Long id) {
        return mapper.select(id);
    }

    public ThemePackage require(Long id) {
        ThemePackage bean = mapper.select(id);
        if (bean == null) {
            throw new ThemeInstallException("theme.package.error.notFound", ThemePackage.NOT_FOUND + id);
        }
        return bean;
    }

    @Nullable
    public ThemePackage findByNameAndVersion(String name, String version) {
        return mapper.selectByNameAndVersion(name, version);
    }

    /**
     * 按名称取最新入库版本（创建时间降序首条；{@link InstallService#installTheme} 裸 JAR 回退用）。
     */
    @Nullable
    public ThemePackage findLatestByName(String name) {
        List<ThemePackage> list = mapper.listByName(name);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按市场条目标识 + 版本取包（市场安装的零重传命中路径）。
     */
    @Nullable
    public ThemePackage findBySlugAndVersion(String slug, String version) {
        return mapper.listBySlug(slug).stream()
                .filter(pkg -> version.equals(pkg.getVersion()))
                .findFirst().orElse(null);
    }

    /**
     * 按市场条目标识 + sha256 取包（未指定版本的市场安装：授权带权威散列，命中即免下载）。
     */
    @Nullable
    public ThemePackage findBySlugAndSha256(String slug, String sha256) {
        return mapper.listBySlug(slug).stream()
                .filter(pkg -> sha256.equalsIgnoreCase(pkg.getSha256()))
                .findFirst().orElse(null);
    }

    /**
     * 包 ZIP 是否确在存储点（行与文件可能失同步——安装/命中判定以文件在场为准）。
     */
    public boolean packageFileExists(ThemePackage bean) {
        return packageFileHandler().isFile(bean.getStorePath());
    }

    // ----- 入库（写穿管线的汇聚点） -----

    /**
     * ZIP 入库：校验（与安装同口径，含大小上限）→ 名称/版本取自包内清单（{@code themeName} /
     * {@code version}）→ ZIP 落存储点 {@code <name>/<version>.zip} → 元数据行 upsert。
     * {@code zipFile} 的生命周期归调用方（本方法只读）。
     *
     * @param zipFile      本地 ZIP 文件
     * @param source       来源（{@link ThemePackage#SOURCE_MARKETPLACE} 等四值之一）
     * @param slug         市场条目标识（非市场来源为 {@code null}）
     * @param fallbackName 清单未声明 {@code themeName} 时的兜底名（上传安装入径以操作员主题名
     *                     兜底，历史无清单包不因入库而装不上；库面板上传无兜底——清单缺失即拒）
     * @return 入库的包行
     */
    @Transactional(rollbackFor = Exception.class)
    public ThemePackage save(Path zipFile, String source, @Nullable String slug, @Nullable String fallbackName) {
        File file = zipFile.toFile();
        ThemeInstallReport report;
        try (InputStream in = new FileInputStream(file)) {
            report = validator.validate(in, file.length(), maxSizeBytes());
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to read package file: " + e.getMessage(), e);
        }
        ThemeMetadata metadata = report.getMetadata();
        String name = metadata != null && StringUtils.isNotBlank(metadata.getThemeName())
                ? metadata.getThemeName() : fallbackName;
        if (StringUtils.isBlank(name)) {
            throw new ThemeInstallException("theme.package.error.nameMissing",
                    "Package manifest declares no themeName; cannot register in the library.");
        }
        themeInstallService.validateThemeName(name);
        String version = metadata != null && StringUtils.isNotBlank(metadata.getVersion())
                ? metadata.getVersion() : DEFAULT_VERSION;
        if (!VERSION_PATTERN.matcher(version).matches()) {
            throw new ThemeInstallException("theme.package.error.invalidVersion",
                    "Invalid package version '" + version + "'. Use letters, digits, '.', '_', '-' only "
                            + "(max 50 chars).");
        }
        String sha256 = sha256Hex(file);

        ThemePackage bean = mapper.selectByNameAndVersion(name, version);
        boolean fresh = bean == null;
        if (fresh) {
            bean = new ThemePackage();
            bean.setId(snowflakeSequence.nextId());
            bean.setName(name);
            bean.setVersion(version);
        }
        bean.setSha256(sha256);
        bean.setSize(file.length());
        bean.setSource(source);
        bean.setMarketplaceSlug(slug);
        bean.setCreated(OffsetDateTime.now());
        packageFileHandler().store(bean.getStorePath(), file);
        if (fresh) {
            mapper.insert(bean);
        } else {
            mapper.update(bean);
        }
        logger.info("Theme package saved: name={} version={} source={} sha256={} size={} fresh={}",
                name, version, source, sha256, file.length(), fresh);
        return bean;
    }

    /**
     * Multipart 入库（库面板上传）：落临时文件后走 {@link #save}。
     */
    public ThemePackage save(MultipartFile zipPart, String source, @Nullable String fallbackName) {
        File tempZip = createTempZip();
        try {
            zipPart.transferTo(tempZip);
            return save(tempZip.toPath(), source, null, fallbackName);
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to read uploaded file: " + e.getMessage(), e);
        } finally {
            FileUtils.deleteQuietly(tempZip);
        }
    }

    /**
     * 内存字节入库（导出「存入包库」去向：{@code ThemeSeedExportService.exportZip} 的产物字节）。
     */
    public ThemePackage saveBytes(byte[] zipBytes, String source, @Nullable String fallbackName) {
        File tempZip = createTempZip();
        try {
            FileUtils.writeByteArrayToFile(tempZip, zipBytes);
            return save(tempZip.toPath(), source, null, fallbackName);
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to write package bytes: " + e.getMessage(), e);
        } finally {
            FileUtils.deleteQuietly(tempZip);
        }
    }

    // ----- 库内安装（安装恒读库内副本） -----

    /**
     * 从库安装到站点（库面板「安装到站点」，及各写穿入径的安装步）：物化库内 ZIP →
     * {@link ThemeInstallService#install(Path, Long, String, ActivateTarget, SeedScope)}
     * 全套门槛（校验 / 解包 / 构成 / 种子生命周期）原样生效。
     *
     * @param themeName 安装目录名（空 = 包名）
     * @param activate  装后激活到的槽位（桌面 / 手机 / 两者）；{@code null} = 只安装
     */
    public ThemeInstallReport install(Long packageId, Long siteId, @Nullable String themeName,
                                      @Nullable ActivateTarget activate, SeedScope seedScope) {
        ThemePackage bean = require(packageId);
        String name = StringUtils.isNotBlank(themeName) ? themeName : bean.getName();
        return withPackageFile(bean, path ->
                themeInstallService.install(path, siteId, name, activate, seedScope));
    }

    /**
     * 上传安装（写穿：控制台既有「上传 ZIP 安装」入径先入库、再从库内副本安装）。
     * 清单无 {@code themeName} 的历史包以操作员主题名入库兜底。
     */
    public ThemeInstallReport installFromUpload(MultipartFile zipPart, Long siteId, String themeName,
                                                @Nullable ActivateTarget activate, SeedScope seedScope) {
        File tempZip = createTempZip();
        try {
            zipPart.transferTo(tempZip);
            ThemePackage bean = save(tempZip.toPath(), ThemePackage.SOURCE_UPLOAD, null, themeName);
            return withPackageFile(bean, path ->
                    themeInstallService.install(path, siteId, themeName, activate, seedScope));
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to read uploaded file: " + e.getMessage(), e);
        } finally {
            FileUtils.deleteQuietly(tempZip);
        }
    }

    /**
     * 上传更新（写穿：更新包与安装包同为库藏品——入库后从库内副本走
     * {@link ThemeInstallService#update(Path, Long, String, SeedScope, SeedOverwrite)} 的
     * 暂存校验 + 原子替换 + 种子差异管线）。
     */
    public UpdateReport updateFromUpload(MultipartFile zipPart, Long siteId, String themeName,
                                         SeedScope seedScope, SeedOverwrite overwrite) {
        File tempZip = createTempZip();
        try {
            zipPart.transferTo(tempZip);
            ThemePackage bean = save(tempZip.toPath(), ThemePackage.SOURCE_UPLOAD, null, themeName);
            return withPackageFile(bean, path ->
                    themeInstallService.update(path, siteId, themeName, seedScope, overwrite));
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to read uploaded file: " + e.getMessage(), e);
        } finally {
            FileUtils.deleteQuietly(tempZip);
        }
    }

    /**
     * 物化库内 ZIP 为本地文件并执行动作：LOCAL 存储直接用存储点内文件（零拷贝）；
     * 远端存储（MinIO/FTP）取到临时文件、用毕即清。
     */
    public <T> T withPackageFile(ThemePackage bean, Function<Path, T> action) {
        FileHandler fileHandler = packageFileHandler();
        String storePath = bean.getStorePath();
        if (!fileHandler.isFile(storePath)) {
            throw new ThemeInstallException("theme.package.error.fileMissing",
                    "Package file missing from package storage: " + storePath
                            + ". Re-upload or re-download the package.");
        }
        File local = fileHandler.getFile(storePath);
        if (local != null && local.isFile()) {
            return action.apply(local.toPath());
        }
        File tempZip = createTempZip();
        try {
            try (OutputStream out = Files.newOutputStream(tempZip.toPath())) {
                fileHandler.writeOutputStream(storePath, out);
            }
            return action.apply(tempZip.toPath());
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to materialize package file: " + e.getMessage(), e);
        } finally {
            FileUtils.deleteQuietly(tempZip);
        }
    }

    // ----- 删除 -----

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ThemePackage bean = require(id);
        mapper.delete(id);
        packageFileHandler().deleteFileAndEmptyParentDir(bean.getStorePath());
        logger.info("Theme package deleted: name={} version={} source={}",
                bean.getName(), bean.getVersion(), bean.getSource());
    }

    // ----- 目录摄取（install-flow §7：ujcms.install.library-dir，Docker 挂载 ZIP 目录离线供包） -----

    /**
     * 把目录下的 {@code *.zip} 逐个登记入库（按文件名排序，来源 {@code upload}）：与库面板
     * 上传同一入库口径——校验 + 清单 {@code themeName} 必需（无清单兜底名，挂载目录是包库
     * 供给面）。按（名称, 版本, sha256, 文件在场）幂等——已登记且未漂移则零写入。单个 ZIP
     * 失败不阻断其余摄取（失败行入报告；引用该包的站点主题步骤自会 fail-loud）。
     *
     * @return 摄取结果（登记数 / 幂等跳过数 / 失败行）
     */
    public IngestResult ingestDirectory(Path dir) {
        File dirFile = dir.toFile();
        File[] zips = dirFile.isDirectory()
                ? dirFile.listFiles((d, name) -> name.toLowerCase().endsWith(".zip")) : null;
        if (zips == null) {
            return new IngestResult(0, 0, List.of("not a directory: " + dirFile));
        }
        Arrays.sort(zips, Comparator.comparing(File::getName));
        int registered = 0;
        int skipped = 0;
        List<String> failures = new ArrayList<>();
        for (File zip : zips) {
            try {
                ThemeInstallReport report;
                try (InputStream in = new FileInputStream(zip)) {
                    report = validator.validate(in, zip.length(), maxSizeBytes());
                }
                ThemeMetadata metadata = report.getMetadata();
                String name = metadata != null ? StringUtils.trimToNull(metadata.getThemeName()) : null;
                String version = metadata != null && StringUtils.isNotBlank(metadata.getVersion())
                        ? metadata.getVersion() : DEFAULT_VERSION;
                if (name == null) {
                    failures.add(zip.getName() + ": package manifest declares no themeName");
                    continue;
                }
                String sha256 = sha256Hex(zip);
                ThemePackage existing = mapper.selectByNameAndVersion(name, version);
                if (existing != null && sha256.equalsIgnoreCase(existing.getSha256())
                        && packageFileExists(existing)) {
                    skipped++;
                    continue;
                }
                save(zip.toPath(), ThemePackage.SOURCE_UPLOAD, null, null);
                registered++;
            } catch (IOException | RuntimeException e) {
                logger.error("Library-dir ingest failed for {} (continuing)", zip.getName(), e);
                failures.add(zip.getName() + ": " + e.getMessage());
            }
        }
        logger.info("Library-dir ingest: dir={} registered={} skipped={} failed={}",
                dirFile, registered, skipped, failures.size());
        return new IngestResult(registered, skipped, failures);
    }

    /**
     * 目录摄取结果
     *
     * @param registered 本次登记（新包或漂移重登）数
     * @param skipped    幂等跳过（已登记且未漂移）数
     * @param failures   失败行（{@code 文件名: 原因}）
     */
    public record IngestResult(int registered, int skipped, List<String> failures) {
    }

    // ----- 目录登记（install-flow §14 / D41-D43：D14 adopt-orphan 的窄例） -----

    /**
     * 把已装主题目录登记入库（恢复/迁移场景的来历补录）：目录与所属站点行俱在、唯独来历缺失
     * （无 {@code theme_state} 行、无库行与 ZIP、从未过校验器）时补齐——目录本身功能完好
     * （画廊/激活/种子/预览均不依赖来历），登记补的是<b>来历 + 校验 + 包库可移植性</b>。
     *
     * <p>站点生命周期锁内：确定性重打包（{@link SeedPackageWriter}——{@code BundledThemePackager}
     * 同款遍历，开发工件排除生效，库内 ZIP 是目录的规范打包子集）→ 真校验器
     * {@link ThemeInstallValidator}（口径分毫不动——不合规目录响亮报错，绝不放宽规则）+
     * 构成制品校验（安装 §12.1 同款，只读）→ {@link #save}（来源
     * {@link ThemePackage#SOURCE_REGISTERED}；无清单目录按 D27 上传安装兜底口径以目录名入库）→
     * {@link ThemeSettingsService#recordVersion}（补 {@code theme_state} 来历行；无清单则版本为
     * {@code null}）→ 注册表重扫。幂等：同（名, 版本）行、同 sha256、ZIP 在场、状态行在场
     * 即零写入跳过（重打包确定性保证未变目录的 sha 稳定，D25）。
     *
     * <p>种子<b>从不</b>随登记导入（恢复站点的内容不得凭空多出演示行——种子端点仍是显式
     * 自选）；既有内容行也不在此补来源三元组——那是导出侧「认领 {@code adoptOrigin}」的职责
     * （theme-seed-export §4.2）。
     */
    public DirRegisterReport registerFromDirectory(Long siteId, String themeName) {
        themeInstallService.validateThemeName(themeName);
        themeInstallService.requireSite(siteId);
        return lifecycleLock.withSiteLock(siteId, () -> {
            File themeDir = themeInstallService.themeDir(siteId, themeName);
            if (!themeDir.isDirectory()) {
                throw new ThemeInstallException("theme.install.error.notFound",
                        "Theme '" + themeName + "' not found on site " + siteId + ".");
            }
            byte[] zip;
            long uncompressed;
            try {
                List<SeedPackageWriter.Entry> entries = SeedPackageWriter.walkThemeDir(themeDir, null, null);
                zip = SeedPackageWriter.zip(entries);
                uncompressed = entries.stream().mapToLong(entry -> entry.data().length).sum();
            } catch (java.io.UncheckedIOException e) {
                throw new ThemeInstallException("theme.install.error.io",
                        "Failed to repack theme directory: " + e.getMessage(), e);
            }
            // 安装上限预检（BundledThemePackager 同款口径）：超限目录清晰报错，不进校验器
            if (uncompressed > maxSizeBytes() || zip.length > maxSizeBytes()) {
                throw new ThemeInstallException("theme.install.error.tooLarge",
                        "Repacked theme exceeds the install cap: uncompressed " + uncompressed
                                + " bytes, zip " + zip.length + " bytes, cap " + maxSizeBytes()
                                + " bytes. Clean the theme directory or raise ujcms.theme.install.maxSizeMb.");
            }
            File tempZip = createTempZip();
            try {
                FileUtils.writeByteArrayToFile(tempZip, zip);
                // 真校验器（不合规目录 = 响亮的逐项报错；规则零改动——无市场侧 lockstep）
                ThemeInstallReport report;
                try (InputStream in = new FileInputStream(tempZip)) {
                    report = validator.validate(in, tempZip.length(), maxSizeBytes());
                }
                // 构成制品校验（安装 §12.1 同款门槛，对在地目录只读执行）
                List<String> errors = manifestService.validateThemeArtifacts(siteId + "/" + themeName, themeDir);
                if (errors != null && !errors.isEmpty()) {
                    throw new ThemeInstallException("theme.install.error.compositionInvalid",
                            "Theme composition artifacts are invalid: " + String.join("; ", errors));
                }
                ThemeMetadata metadata = report.getMetadata();
                String name = metadata != null && StringUtils.isNotBlank(metadata.getThemeName())
                        ? metadata.getThemeName() : themeName;
                String version = metadata != null && StringUtils.isNotBlank(metadata.getVersion())
                        ? metadata.getVersion() : DEFAULT_VERSION;
                String sha256 = sha256Hex(tempZip);
                // 幂等（D43）：库行 + ZIP + 状态行俱在且未漂移 → 零写入
                ThemePackage existing = mapper.selectByNameAndVersion(name, version);
                if (existing != null && sha256.equalsIgnoreCase(existing.getSha256())
                        && packageFileExists(existing)
                        && themeSettingsService.findState(siteId, themeName) != null) {
                    logger.info("Theme dir already registered, skip: site={} name={} package={}@{}",
                            siteId, themeName, name, version);
                    return new DirRegisterReport(existing.getId(), existing.getName(), existing.getVersion(),
                            existing.getSha256(), existing.getSize(), true, report.getWarnings());
                }
                ThemePackage saved = save(tempZip.toPath(), ThemePackage.SOURCE_REGISTERED, null, themeName);
                themeSettingsService.recordVersion(siteId, themeName,
                        metadata != null ? metadata.getVersion() : null);
                themeInstallService.reloadRegistry();
                logger.info("Theme dir registered: site={} name={} package={}@{} sha256={}",
                        siteId, themeName, saved.getName(), saved.getVersion(), saved.getSha256());
                return new DirRegisterReport(saved.getId(), saved.getName(), saved.getVersion(),
                        saved.getSha256(), saved.getSize(), false, report.getWarnings());
            } catch (IOException e) {
                throw new ThemeInstallException("theme.install.error.io",
                        "Failed to register theme directory: " + e.getMessage(), e);
            } finally {
                FileUtils.deleteQuietly(tempZip);
            }
        });
    }

    /**
     * 目录登记报告（install-flow §14）
     *
     * @param packageId         库行ID
     * @param name              入库包名（清单 {@code themeName}，无清单为目录名）
     * @param version           入库版本（无清单为 {@code 0.0.0}；{@code theme_state} 行按清单实况记录）
     * @param sha256            重打包 ZIP 摘要（确定性——未变目录重登记摘要恒同）
     * @param size              ZIP 字节数
     * @param alreadyRegistered 幂等跳过（库行 + ZIP + 状态行俱在且未漂移，本次零写入）
     * @param warnings          校验器警告（弃警不拒口径原样出仓）
     */
    public record DirRegisterReport(Long packageId, String name, String version, String sha256,
                                    Long size, boolean alreadyRegistered, List<String> warnings) {
    }

    // ----- 内置包注册（D6） -----

    /**
     * 把类路径内置包注册入库（{@code SystemCoreBootstrap} 于未安装状态调用）：按
     * （清单名, 版本, sha256, 文件在场）幂等——已注册且未漂移则零写入；漂移（重建后包字节变化 /
     * 存储点丢文件）则重新入库。失败不阻断启动（记日志；裸 JAR 首装回退将报告主题缺失）。
     *
     * @return 本次实际入库的包数
     */
    public int ensureBundledRegistered() {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver(resourceLoader)
                    .getResources(BUNDLED_LOCATION_PATTERN);
        } catch (IOException e) {
            logger.warn("Bundled theme package scan failed: {}", e.getMessage());
            return 0;
        }
        int registered = 0;
        for (Resource resource : resources) {
            File tempZip = createTempZip();
            try (InputStream in = resource.getInputStream()) {
                FileUtils.copyInputStreamToFile(in, tempZip);
                ThemeInstallReport report;
                try (InputStream zipIn = new FileInputStream(tempZip)) {
                    report = validator.validate(zipIn, tempZip.length(), maxSizeBytes());
                }
                ThemeMetadata metadata = report.getMetadata();
                String name = metadata != null ? StringUtils.trimToNull(metadata.getThemeName()) : null;
                String version = metadata != null && StringUtils.isNotBlank(metadata.getVersion())
                        ? metadata.getVersion() : DEFAULT_VERSION;
                if (name == null) {
                    logger.warn("Bundled theme package has no manifest themeName, skipped: {}",
                            resource.getDescription());
                    continue;
                }
                String sha256 = sha256Hex(tempZip);
                ThemePackage existing = mapper.selectByNameAndVersion(name, version);
                if (existing != null && sha256.equalsIgnoreCase(existing.getSha256())
                        && packageFileExists(existing)) {
                    continue;
                }
                save(tempZip.toPath(), ThemePackage.SOURCE_BUNDLED, null, null);
                registered++;
            } catch (IOException | RuntimeException e) {
                logger.error("Bundled theme package registration failed (boot continues): {}",
                        resource.getDescription(), e);
            } finally {
                FileUtils.deleteQuietly(tempZip);
            }
        }
        if (registered > 0) {
            logger.info("Bundled theme packages registered in the library: {}", registered);
        }
        return registered;
    }

    // ----- helpers -----

    private FileHandler packageFileHandler() {
        return configService.getUnique().getPackageStorage().getFileHandler(pathResolver);
    }

    private long maxSizeBytes() {
        return ThemeInstallValidator.megabytesToBytes(themeInstallService.getMaxSizeMb());
    }

    private static File createTempZip() {
        try {
            return Files.createTempFile("ujcms-package-", ".zip").toFile();
        } catch (IOException e) {
            throw new ThemeInstallException("theme.install.error.io",
                    "Failed to create temp file: " + e.getMessage(), e);
        }
    }

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
                    "Failed to hash package file: " + e.getMessage(), e);
        }
    }

    /**
     * 便捷判定：库内是否存在可用的指定名称包（{@link Optional} 版 {@link #findLatestByName}，
     * 文件在场才算可用）。
     */
    public Optional<ThemePackage> findUsableByName(String name) {
        return mapper.listByName(name).stream().filter(this::packageFileExists).findFirst();
    }
}
