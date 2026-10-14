package com.ujcms.cms.core.composition;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.RegistryReload;
import com.ujcms.cms.core.mapper.RegistryReloadMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 注册表服务。持有当前 {@link RegistrySnapshot} 的 {@code AtomicReference}（无锁读取），
 * 负责扫描（已安装主题目录）、
 * validate-before-swap 原子替换，以及向 {@code ujcms_registry_reload} 记录审计。
 *
 * <p>失败行为（§9.3）：启动扫描失败 → 启动失败；运行期重载失败 → 保留旧快照并记录审计。
 *
 * @author PONY
 */
@Service
public class ManifestService {
    private static final Logger logger = LoggerFactory.getLogger(ManifestService.class);

    /**
     * §19.2 重载计数指标名（标签：{@code source} 触发来源、{@code result} 结果）
     */
    public static final String METRIC_RELOAD = "ujcms.registry.reload.count";
    /**
     * §19.2 快照最近换入时间 gauge（epoch 秒）。计划原名 {@code ujcms.registry.snapshot.version}——
     * 快照“版本”是内容散列（字符串），不可作 gauge；以最近成功换入时间为变更信号（记录在案的偏差）。
     */
    public static final String METRIC_SNAPSHOT_LOADED = "ujcms.registry.snapshot.loaded.time";
    /**
     * 重载审计保留条数上限。每次写入后按上限裁剪（自动保留，无操作员清理入口）；
     * 健康页仅展示最近 20 条，此上限只为「查上周变更」类排查留出余量。
     */
    public static final int RELOAD_RETENTION_ROWS = 100;

    private final SnapshotValidator snapshotValidator;
    private final ShapeCompatibilityClassifier compatibilityClassifier;
    private final RegistryReloadMapper reloadMapper;
    private final SnowflakeSequence snowflakeSequence;
    private final ThemeDirScanner themeDirScanner;
    private final MeterRegistry meterRegistry;

    private final AtomicReference<RegistrySnapshot> snapshotRef = new AtomicReference<>(RegistrySnapshot.empty());

    public ManifestService(SnapshotValidator snapshotValidator,
                           ShapeCompatibilityClassifier compatibilityClassifier,
                           RegistryReloadMapper reloadMapper, SnowflakeSequence snowflakeSequence,
                           ThemeDirScanner themeDirScanner, MeterRegistry meterRegistry) {
        this.snapshotValidator = snapshotValidator;
        this.compatibilityClassifier = compatibilityClassifier;
        this.reloadMapper = reloadMapper;
        this.snowflakeSequence = snowflakeSequence;
        this.themeDirScanner = themeDirScanner;
        this.meterRegistry = meterRegistry;
        Gauge.builder(METRIC_SNAPSHOT_LOADED, snapshotRef,
                        ref -> ref.get().getLoadedAt().toEpochSecond())
                .description("最近一次成功换入快照的时间（epoch 秒）")
                .register(meterRegistry);
    }

    /**
     * 当前快照。请求入口处取一次并在请求内传递（进行中的请求在旧快照上完成）。
     */
    public RegistrySnapshot getSnapshot() {
        return snapshotRef.get();
    }

    /**
     * 当前快照版本（内容散列）
     */
    public String currentVersion() {
        return snapshotRef.get().getContentHash();
    }

    /**
     * 全量扫描并替换快照：已安装主题目录（{@code pages/}、{@code shapes/}）。
     * Shape 只存在于主题命名空间（theme-owned-shapes D15）。
     *
     * @param source      触发来源（{@link RegistryReload} 的 SOURCE_* 常量）
     * @param triggeredBy 触发人（自动触发为 {@code null}）
     * @return 生效的快照
     */
    public RegistrySnapshot scanAll(String source, @Nullable String triggeredBy) {
        RegistrySnapshot next;
        try {
            RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
            themeDirScanner.scanInto(builder);
            next = builder.build();
        } catch (CompositionLoadException e) {
            logReload(source, triggeredBy, RegistryReload.RESULT_FAILURE, 0, 0, null, e.getMessage());
            throw e;
        }
        applySnapshot(next, source, triggeredBy);
        return next;
    }

    /**
     * 校验并原子替换快照（validate-then-swap）。校验失败保留旧快照、记录审计并抛出异常。
     */
    public void applySnapshot(RegistrySnapshot next, String source, @Nullable String triggeredBy) {
        List<String> errors = new ArrayList<>(snapshotValidator.validate(next));
        // §13.1 兼容性分级：相对当前快照判定 Shape 变更等级。无迁移块覆盖的破坏性移除→拒绝重载（旧快照继续服务）。
        errors.addAll(classifyAgainstCurrent(snapshotRef.get(), next));
        if (!errors.isEmpty()) {
            logReload(source, triggeredBy, RegistryReload.RESULT_FAILURE,
                    next.getShapeCount(), next.getManifestCount(), null, String.join("\n", errors));
            throw new CompositionLoadException("snapshot", errors);
        }
        // 差异摘要须在替换前相对旧快照计算（首次加载无基线为 null）
        String diffSummary = SnapshotDiff.summarize(snapshotRef.get(), next);
        snapshotRef.set(next);
        // 替换成功后清空渲染诊断（模板绑定可能已修复，§11.5）
        logReload(source, triggeredBy, RegistryReload.RESULT_SUCCESS,
                next.getShapeCount(), next.getManifestCount(), diffSummary, null);
        logger.info("Composition registry reloaded: source={}, shapes={}, manifests={}, hash={}",
                source, next.getShapeCount(), next.getManifestCount(), next.getContentHash());
    }

    /**
     * 安装期构成制品校验门槛（§12.1）。构造仅含该主题的候选快照并整体校验，
     * 在不触动当前快照的前提下判定新主题的 shapes/pages/settings 是否自洽
     * （Shape 引用可解析、字段类型已注册、模板绑定唯一等）。错误列表非空即安装应回滚。
     *
     * @param themeKey 规范化主题键（{@code {siteId}/{themeName}}）
     * @param themeDir 已解包的主题目录
     * @return 错误列表，空列表表示通过
     */
    public List<String> validateThemeArtifacts(String themeKey, java.io.File themeDir) {
        try {
            RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
            themeDirScanner.loadTheme(themeKey, themeDir, builder);
            return snapshotValidator.validate(builder.build());
        } catch (CompositionLoadException e) {
            return List.of(e.getMessage());
        }
    }

    /**
     * 相对当前快照对候选快照做兼容性分级（§13.1）。逐主题命名空间（theme:&lt;key&gt;）对比同名 Shape：
     * 无迁移块覆盖的破坏性移除返回错误（→拒绝重载）；破坏性必填仅记日志（受影响卡片在健康页标记，不拒绝）。
     * 启动时当前快照为空，所有 Shape 视作新增、不分级。
     */
    private List<String> classifyAgainstCurrent(RegistrySnapshot current, RegistrySnapshot next) {
        List<String> errors = new ArrayList<>();
        next.getThemeShapes().forEach((theme, shapes) -> compareShapes("theme:" + theme,
                current.getThemeShapes().getOrDefault(theme, Map.of()), shapes, errors));
        return errors;
    }

    private void compareShapes(String namespace, Map<String, Shape> oldShapes, Map<String, Shape> newShapes,
                               List<String> errors) {
        newShapes.forEach((name, newShape) -> {
            Shape oldShape = oldShapes.get(name);
            if (oldShape == null) {
                return;
            }
            ShapeCompatibilityClassifier.Result result = compatibilityClassifier.classify(oldShape, newShape);
            if (result.level() == ShapeCompatibility.BREAKING_REMOVAL) {
                errors.add(String.format("[%s] Shape %s: 破坏性移除未被迁移块覆盖: %s"
                                + "（须在 migrations 中以 remove/rename/transform 声明，或回退该 Shape 版本）",
                        namespace, name, result.uncoveredRemovals()));
            } else if (result.level() == ShapeCompatibility.BREAKING_REQUIRED) {
                logger.warn("Composition compat: [{}] Shape {} 新增必填字段 {}，旧版本卡片将在健康页标记待处理（§13.4）",
                        namespace, name, result.newRequiredFields());
            }
        });
    }

    /**
     * 审计写入为尽力而为：审计失败不应使重载本身失败（启动扫描时表可能尚未就绪）。
     * §19.2 重载计数指标（{@code source}/{@code result} 标签）在同一漏斗记录。
     */
    private void logReload(String source, @Nullable String triggeredBy, String result,
                           int shapesCount, int manifestsCount, @Nullable String diffSummaryJson,
                           @Nullable String errorDetail) {
        meterRegistry.counter(METRIC_RELOAD, "source", source, "result", result).increment();
        try {
            RegistryReload bean = new RegistryReload();
            bean.setId(snowflakeSequence.nextId());
            bean.setSource(source);
            bean.setTriggeredBy(triggeredBy);
            bean.setResult(result);
            bean.setShapesCount(shapesCount);
            bean.setManifestsCount(manifestsCount);
            bean.setDiffSummaryJson(diffSummaryJson);
            bean.setErrorDetail(errorDetail);
            reloadMapper.insert(bean);
            pruneReloadJournal();
        } catch (RuntimeException e) {
            logger.warn("Cannot write registry reload audit: {}", e.getMessage());
        }
    }

    /**
     * 按保留数上限裁剪审计（{@link #RELOAD_RETENTION_ROWS}）：取第 N+1 新的记录，删除其ID及更早
     * （雪花ID随时间单调）。与审计写入同为尽力而为。
     */
    private void pruneReloadJournal() {
        List<RegistryReload> overflow = PageMethod.offsetPage(RELOAD_RETENTION_ROWS, 1, false)
                .doSelectPage(reloadMapper::selectAllOrderByLoadedAtDesc);
        if (!overflow.isEmpty()) {
            reloadMapper.deleteByIdLessThanOrEqual(overflow.get(0).getId());
        }
    }
}
