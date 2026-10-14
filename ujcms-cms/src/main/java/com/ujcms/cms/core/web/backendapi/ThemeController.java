package com.ujcms.cms.core.web.backendapi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.component.ThemeInfo;
import com.ujcms.cms.core.component.ThemeInstallReport;
import com.ujcms.cms.core.composition.ExportPlan;
import com.ujcms.cms.core.composition.ManifestService;
import com.ujcms.cms.core.composition.RegistrySnapshot;
import com.ujcms.cms.core.composition.SeedExportRequest;
import com.ujcms.cms.core.composition.SeedOverwrite;
import com.ujcms.cms.core.composition.SeedPlan;
import com.ujcms.cms.core.composition.ThemeContentSeedRemover.RemoveResult;
import com.ujcms.cms.core.composition.ThemeImpactReport;
import com.ujcms.cms.core.composition.ThemeSeedOrchestrator.SeedRunResult;
import com.ujcms.cms.core.composition.ThemeSettingsSchema;
import com.ujcms.cms.core.composition.ThemeSettingsService;
import com.ujcms.cms.core.domain.ThemePackage;
import com.ujcms.cms.core.service.ThemeInstallService;
import com.ujcms.cms.core.service.ThemeInstallService.ActivateTarget;
import com.ujcms.cms.core.service.ThemeInstallService.SeedScope;
import com.ujcms.cms.core.service.ThemeInstallService.UninstallDataPolicy;
import com.ujcms.cms.core.service.ThemeInstallService.UpdateReport;
import com.ujcms.cms.core.service.ThemePackageService;
import com.ujcms.cms.core.service.ThemeSeedExportService;
import com.ujcms.cms.core.service.ThemeSeedExportService.ZipPackage;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Theme marketplace + lifecycle admin API (Phase 5). Endpoints:
 * <ul>
 *   <li>{@code GET    /api/backend/core/theme/{siteId}}                       — list themes</li>
 *   <li>{@code GET    /api/backend/core/theme/limits}                         — config (max ZIP MB)</li>
 *   <li>{@code GET    /api/backend/core/theme/{siteId}/unregistered-dirs}     — 未登记目录检测
 *       (install-flow §14)</li>
 *   <li>{@code POST   /api/backend/core/theme/{siteId}/{themeName}/register}  — 目录登记入库
 *       (install-flow §14)</li>
 *   <li>{@code POST   /api/backend/core/theme/install}                        — install ZIP (+ seed choice)</li>
 *   <li>{@code POST   /api/backend/core/theme/{siteId}/{themeName}/update}    — update to a new version</li>
 *   <li>{@code DELETE /api/backend/core/theme/{siteId}/{themeName}}           — uninstall (+ data policy)</li>
 *   <li>{@code POST   /api/backend/core/theme/{siteId}/preview-activate}      — impact report</li>
 *   <li>{@code PUT    /api/backend/core/theme/{siteId}/active}                — activate / switch</li>
 *   <li>{@code GET/PUT /api/backend/core/theme/{siteId}/{themeName}/settings} — theme settings</li>
 *   <li>{@code POST   /api/backend/core/theme/{siteId}/{themeName}/export/preview} — export dry-run
 *       (theme-seed-export §8)</li>
 *   <li>{@code POST   /api/backend/core/theme/{siteId}/{themeName}/export}         — export: refresh
 *       seed dir / data-only ZIP / theme ZIP</li>
 * </ul>
 *
 * <p>Activation stays {@code Site.theme}/{@code mobileTheme}. The lifecycle (validation, impact
 * analysis, seed import, orphan archival, settings) extends the existing theme feature — see
 * {@link ThemeInstallService} and {@link ThemeSettingsService}.
 *
 * @author PONY
 */
@RestController("backendThemeController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/theme")
public class ThemeController {
    private final ThemeInstallService service;
    private final ThemeSettingsService settingsService;
    private final ManifestService manifestService;
    private final ThemeSeedExportService exportService;
    private final ThemePackageService packageService;

    public ThemeController(ThemeInstallService service, ThemeSettingsService settingsService,
                           ManifestService manifestService, ThemeSeedExportService exportService,
                           ThemePackageService packageService) {
        this.service = service;
        this.settingsService = settingsService;
        this.manifestService = manifestService;
        this.exportService = exportService;
        this.packageService = packageService;
    }

    @GetMapping("limits")
    @PreAuthorize("hasAnyAuthority('theme:list','*')")
    public Map<String, Object> limits() {
        Map<String, Object> out = new HashMap<>(4);
        out.put("maxSizeMb", service.getMaxSizeMb());
        // 模板存储公开 URL 前缀：画廊按 {templatesUrl}/{siteId}/{themeName}/{preview} 拼截图地址（A4）
        out.put("templatesUrl", service.templateStorageUrl());
        return out;
    }

    @GetMapping("{siteId}")
    @PreAuthorize("hasAnyAuthority('theme:list','*')")
    public List<ThemeInfo> list(@PathVariable Long siteId) {
        return service.listThemes(siteId);
    }

    /**
     * 未登记主题目录清单（install-flow §14 检测面，运维/脚本用）：站点目录减 {@code theme_state}
     * 行。画廊徽标走 {@code list} 的 {@code registered} 字段，不依赖本端点。
     */
    @GetMapping("{siteId}/unregistered-dirs")
    @PreAuthorize("hasAnyAuthority('theme:list','*')")
    public List<String> unregisteredDirs(@PathVariable Long siteId) {
        return service.listUnregisteredThemeDirs(siteId);
    }

    /**
     * 目录登记（install-flow §14 / D41-D43）：把无来历的已装主题目录确定性重打包过真校验器后
     * 入库（来源 {@code registered}）并补 {@code theme_state} 行。种子从不随登记导入；权限沿用
     * {@code theme:install}（对站点主题实例的登记与安装同级授权）。
     */
    @PostMapping("{siteId}/{themeName}/register")
    @PreAuthorize("hasAnyAuthority('theme:install','*')")
    @OperationLog(module = "theme", operation = "registerDir", type = OperationType.CREATE)
    public ThemePackageService.DirRegisterReport register(@PathVariable Long siteId,
                                                          @PathVariable String themeName) {
        return packageService.registerFromDirectory(siteId, themeName);
    }

    /**
     * 主题页面清单（A4 详情页「页面与插槽」页签，只读）：该主题声明的全部页面清单
     * 及其插槽/区域定义摘要。文件即事实——直接读注册表快照，非活动主题同样可查。
     */
    @GetMapping("{siteId}/{themeName}/pages")
    @PreAuthorize("hasAnyAuthority('theme:list','*')")
    public List<ThemePageResult> pages(@PathVariable Long siteId, @PathVariable String themeName) {
        String themeKey = ThemeSettingsService.themeKey(siteId, themeName);
        return manifestService.getSnapshot().getThemeManifests().getOrDefault(themeKey, Map.of())
                .values().stream()
                .map(manifest -> new ThemePageResult(manifest.getName(), manifest.getLabel(),
                        manifest.getTemplates(), manifest.getRoute(),
                        manifest.getSlots().entrySet().stream()
                                .map(entry -> new ThemePageSlotResult(entry.getKey(),
                                        entry.getValue().getAllowedShapes(), entry.getValue().isRequired()))
                                .toList(),
                        !manifest.getSettings().isEmpty()))
                .toList();
    }

    /**
     * 上传安装（install-flow §6 P3 起写穿主题包库：先入库、再从库内副本安装——同一 ZIP
     * 装到第二个站点从库面板一键完成、零重传）。
     */
    @PostMapping("install")
    @PreAuthorize("hasAnyAuthority('theme:install','*')")
    @OperationLog(module = "theme", operation = "install", type = OperationType.CREATE)
    public ThemeInstallReport install(@RequestParam("file") MultipartFile file,
                                      @RequestParam("siteId") @NotNull Long siteId,
                                      @RequestParam("themeName") @NotBlank String themeName,
                                      @RequestParam(value = "activate", required = false)
                                      @Nullable String activate,
                                      @RequestParam(value = "seedScope", defaultValue = "none")
                                      String seedScope) {
        return packageService.installFromUpload(file, siteId, themeName, parseActivateTarget(activate),
                parseSeedScope(seedScope));
    }

    @PostMapping("{siteId}/{themeName}/update")
    @PreAuthorize("hasAnyAuthority('theme:install','*')")
    @OperationLog(module = "theme", operation = "update", type = OperationType.UPDATE)
    public UpdateReport update(@PathVariable Long siteId, @PathVariable String themeName,
                               @RequestParam("file") MultipartFile file,
                               @RequestParam(value = "seedScope", defaultValue = "none") String seedScope,
                               @RequestParam(value = "overwrite", defaultValue = "fillOnly") String overwrite) {
        return packageService.updateFromUpload(file, siteId, themeName, parseSeedScope(seedScope),
                parseOverwrite(overwrite));
    }

    /**
     * 表单参数的 seedScope 解析（R5：小写 {@code none|composition|all}）。显式解析——MVC 的枚举
     * 转换按常量名大小写敏感，未知值应答 400 而非 500。JSON 体经 {@code @JsonCreator} 同规则解析。
     */
    private static SeedScope parseSeedScope(String value) {
        try {
            return SeedScope.parse(value);
        } catch (IllegalArgumentException e) {
            throw new Http400Exception("seedScope must be none|composition|all: " + value);
        }
    }

    /**
     * 表单参数的装后激活目标解析（小写 {@code desktop|mobile|both}；缺省 / 空 = 只安装不激活）。
     */
    @Nullable
    private static ActivateTarget parseActivateTarget(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ActivateTarget.parse(value);
        } catch (IllegalArgumentException e) {
            throw new Http400Exception("activate must be desktop|mobile|both: " + value);
        }
    }

    /**
     * 表单参数的 overwrite 解析（§6.1：{@code fillOnly|syncUnmodified|resetAllOrigin}，同上显式解析）。
     */
    private static SeedOverwrite parseOverwrite(String value) {
        try {
            return SeedOverwrite.parse(value);
        } catch (IllegalArgumentException e) {
            throw new Http400Exception(e.getMessage());
        }
    }

    // ----- seed lifecycle (§6.2, A3) -----

    /**
     * 种子导入干跑计划（只读，供各确认对话框计数；§6.2）。
     */
    @PostMapping("{siteId}/{themeName}/seed/preview")
    @PreAuthorize("hasAnyAuthority('theme:list','*')")
    public SeedPlan previewSeed(@PathVariable Long siteId, @PathVariable String themeName) {
        return service.previewSeed(siteId, themeName);
    }

    /**
     * 手动种子导入（与安装/激活解耦：先装码后导数、纯数据包、A2 专题类失败的续传路径；§6.2）。
     * 专属权限码 {@code theme:seed}（A4）：演示数据管理可单独授权，不再搭乘 {@code theme:install}；
     * 安装/更新/激活随带的种子导入仍走各自的生命周期权限码。
     */
    @PostMapping("{siteId}/{themeName}/seed/import")
    @PreAuthorize("hasAnyAuthority('theme:seed','*')")
    @OperationLog(module = "theme", operation = "seedImport", type = OperationType.UPDATE)
    public SeedRunResult importSeed(@PathVariable Long siteId, @PathVariable String themeName,
                                    @RequestBody @NotNull SeedImportParams params) {
        SeedScope scope = params.scope() != null ? params.scope() : SeedScope.ALL;
        if (scope == SeedScope.NONE) {
            throw new Http400Exception("seed import scope must be composition|all");
        }
        SeedOverwrite overwrite = params.overwrite() != null ? params.overwrite() : SeedOverwrite.FILL_ONLY;
        return service.importSeed(siteId, themeName, scope, overwrite);
    }

    /**
     * 移除演示数据（独立显式操作，内容本源行限定，物理删除不进回收站；{@code dryRun=true}
     * 只返回计数——破坏性操作总是先出示干跑计数，§6.1 安全不变量 2）。{@code theme:uninstall}
     * 同样放行：卸载对话框的内容计数取自本端点干跑，可卸载者必可干跑（A4）。
     */
    @DeleteMapping("{siteId}/{themeName}/seed")
    @PreAuthorize("hasAnyAuthority('theme:seed','theme:uninstall','*')")
    @OperationLog(module = "theme", operation = "seedRemove", type = OperationType.DELETE)
    public RemoveResult removeSeed(@PathVariable Long siteId, @PathVariable String themeName,
                                   @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        return service.removeSeed(siteId, themeName, dryRun);
    }

    // ----- seed export (theme-seed-export E2, §8) -----

    /**
     * 导出读出站点的全部内容（文章正文、草稿、栏目、附件），所以只能导出当前站点：路径里的站点ID须与之相同。
     * 否则持有 {@code theme:export} 的人换个ID就能把别的站点的内容整包带走
     * （docs/spec/article-management/plan.md Phase 2.7）。站点之内不再按文章数据权限过滤——这个权限码本身就是
     * “可以读出整站内容”，应当只授给站点管理员一级的角色
     */
    private static void requireCurrentSite(Long siteId) {
        ValidUtils.dataInSite(siteId, Contexts.getCurrentSiteId());
    }

    /**
     * 导出干跑预览（只读，向导第一步；专属权限码 {@code theme:export}——核心版全版本可用，D5）。
     */
    @PostMapping("{siteId}/{themeName}/export/preview")
    @PreAuthorize("hasAnyAuthority('theme:export','*')")
    public ExportPlan previewExport(@PathVariable Long siteId, @PathVariable String themeName,
                                    @RequestBody @NotNull ExportParams params) {
        requireCurrentSite(siteId);
        return exportService.preview(siteId, themeName, toExportRequest(params));
    }

    /**
     * 导出（§7.1 三模式）：{@code refreshSeedDir} → 刷新报告（JSON）；{@code dataOnlyZip} /
     * {@code themeZip} → ZIP 附件下载，或 {@code destination=library} 存入主题包库
     * （install-flow §6 P3：导出与浏览器下载并列的第二去向——入库即可装到任意站点）。
     * {@code adoptOrigin=true} 额外认领操作员行（§4.2，写库——向导须明示警告）；
     * {@code snapshotSettings=true} 把已存设置值写入发射清单的声明默认值（§7.4，只写导出产物、
     * 不写库）；刷新活动主题须 {@code confirmActive=true}（Q1）。
     */
    @PostMapping("{siteId}/{themeName}/export")
    @PreAuthorize("hasAnyAuthority('theme:export','*')")
    @OperationLog(module = "theme", operation = "export", type = OperationType.UPDATE)
    public ResponseEntity<Object> export(@PathVariable Long siteId, @PathVariable String themeName,
                                         @RequestBody @NotNull ExportParams params) {
        requireCurrentSite(siteId);
        SeedExportRequest request = toExportRequest(params);
        boolean adoptOrigin = Boolean.TRUE.equals(params.adoptOrigin());
        boolean snapshotSettings = Boolean.TRUE.equals(params.snapshotSettings());
        String mode = params.mode() != null ? params.mode() : "";
        switch (mode) {
            case "refreshSeedDir" -> {
                return ResponseEntity.ok(exportService.refreshSeedDir(siteId, themeName, request,
                        adoptOrigin, snapshotSettings, params.version(),
                        Boolean.TRUE.equals(params.confirmActive())));
            }
            case "dataOnlyZip", "themeZip" -> {
                ZipPackage zip = exportService.exportZip(siteId, themeName, request,
                        "dataOnlyZip".equals(mode), adoptOrigin, snapshotSettings, params.version());
                if ("library".equals(params.destination())) {
                    ThemePackage saved = packageService.saveBytes(zip.bytes(),
                            ThemePackage.SOURCE_EXPORT, themeName);
                    return ResponseEntity.ok(new ExportToLibraryResult(saved.getId(), saved.getName(),
                            saved.getVersion(), saved.getSha256(), saved.getSize(), zip.plan(), zip.adopted()));
                }
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/zip"))
                        .header("Content-Disposition", ContentDisposition.attachment()
                                .filename(zip.filename(), StandardCharsets.UTF_8).build().toString())
                        .body(zip.bytes());
            }
            default -> throw new Http400Exception(
                    "mode must be refreshSeedDir|dataOnlyZip|themeZip: " + mode);
        }
    }

    /**
     * 请求体 → 引擎选择形（§8 请求体的 {@code scope: content|composition|all} 显式解析，
     * 未知值 400；selection 缺省 = 引擎缺省选择）。
     */
    private static SeedExportRequest toExportRequest(ExportParams params) {
        String scope = params.scope() != null ? params.scope() : "all";
        boolean includeContent;
        boolean includeComposition;
        switch (scope) {
            case "content" -> {
                includeContent = true;
                includeComposition = false;
            }
            case "composition" -> {
                includeContent = false;
                includeComposition = true;
            }
            case "all" -> {
                includeContent = true;
                includeComposition = true;
            }
            default -> throw new Http400Exception("scope must be content|composition|all: " + scope);
        }
        ExportSelectionParams selection = params.selection();
        Integer cap = selection != null ? selection.articleCapPerChannel() : null;
        return new SeedExportRequest(
                selection != null ? selection.channelIds() : null,
                selection != null ? selection.topicIds() : null,
                selection != null ? selection.surveyIds() : null,
                selection != null ? selection.formTypeIds() : null,
                selection != null ? selection.linkGroupIds() : null,
                cap != null ? cap : SeedExportRequest.DEFAULT_ARTICLE_CAP,
                selection != null && Boolean.TRUE.equals(selection.includeDrafts()),
                includeContent, includeComposition);
    }

    @DeleteMapping("{siteId}/{themeName}")
    @PreAuthorize("hasAnyAuthority('theme:uninstall','*')")
    @OperationLog(module = "theme", operation = "uninstall", type = OperationType.DELETE)
    public ResponseEntity<Body> uninstall(@PathVariable Long siteId, @PathVariable String themeName,
                                          @RequestParam(value = "dataPolicy", defaultValue = "KEEP")
                                          UninstallDataPolicy dataPolicy) {
        service.uninstall(siteId, themeName, dataPolicy);
        return Responses.ok();
    }

    /** Impact report (orphans + new seed) for a prospective activate / switch (§12.3). */
    @PostMapping("{siteId}/preview-activate")
    @PreAuthorize("hasAnyAuthority('theme:activate','*')")
    public ThemeImpactReport previewActivate(@PathVariable Long siteId,
                                             @RequestBody @NotNull ActivateRequest request) {
        return service.previewActivate(siteId, request.themeName(), request.targetOrDefault());
    }

    /**
     * 激活/切换（§12.3）。返回本次随激活执行的种子导入结果（{@code seedScope=none} 时为 null）——
     * 失败明细要到得了控制台（theme-seed-export §8.1 / D22）。
     */
    @PutMapping("{siteId}/active")
    @PreAuthorize("hasAnyAuthority('theme:activate','*')")
    @OperationLog(module = "theme", operation = "activate", type = OperationType.UPDATE)
    public ActivateResult activate(@PathVariable Long siteId,
                                   @RequestBody @NotNull ActivateRequest request) {
        SeedScope seedScope = request.seedScope() != null ? request.seedScope() : SeedScope.NONE;
        SeedOverwrite overwrite = request.overwrite() != null ? request.overwrite() : SeedOverwrite.FILL_ONLY;
        return new ActivateResult(service.activate(siteId, request.themeName(), request.targetOrDefault(),
                seedScope, overwrite));
    }

    /**
     * Theme settings: schema (groups + field defs) + merged values, all three scopes (§8.2 / §4.2).
     * {@code pages} lists every theme page manifest declaring a {@code settings} block;
     * {@code variants} lists every manifest-declared layout variant with one (key {@code surface:value}).
     */
    @GetMapping("{siteId}/{themeName}/settings")
    @PreAuthorize("hasAnyAuthority('theme:settings','*')")
    public SettingsResult settings(@PathVariable Long siteId, @PathVariable String themeName) {
        String themeKey = ThemeSettingsService.themeKey(siteId, themeName);
        ThemeSettingsSchema schema = settingsService.schema(themeKey);
        RegistrySnapshot snapshot = manifestService.getSnapshot();
        Map<String, Map<String, Map<String, Object>>> pageValues =
                settingsService.mergedPageConfigs(siteId, themeName);
        List<PageSettingsResult> pages = new ArrayList<>();
        snapshot.getThemeManifests().getOrDefault(themeKey, Map.of()).forEach((pageName, manifest) -> {
            if (!manifest.getSettings().isEmpty()) {
                pages.add(new PageSettingsResult(pageName, manifest.getLabel(),
                        manifest.getSettings().rawGroups(), pageValues.getOrDefault(pageName, Map.of())));
            }
        });
        Map<String, Map<String, Map<String, Object>>> variantValues =
                settingsService.mergedVariantConfigs(siteId, themeName);
        List<VariantSettingsResult> variants = new ArrayList<>();
        snapshot.getThemeLayoutVariants(themeKey).getBySurface().forEach((surface, items) ->
                items.forEach(item -> {
                    if (!item.getSettings().isEmpty()) {
                        String variantKey = surface + ":" + item.getValue();
                        variants.add(new VariantSettingsResult(surface, item.getValue(), item.getLabel(),
                                item.getSettings().rawGroups(), variantValues.getOrDefault(variantKey, Map.of())));
                    }
                }));
        return new SettingsResult(schema.rawGroups(), settingsService.mergedConfig(siteId, themeName),
                pages, variants);
    }

    @PutMapping("{siteId}/{themeName}/settings")
    @PreAuthorize("hasAnyAuthority('theme:settings','*')")
    @OperationLog(module = "theme", operation = "settings", type = OperationType.UPDATE)
    public SettingsSaveResult saveSettings(@PathVariable Long siteId, @PathVariable String themeName,
                                           @RequestBody SettingsParams params) {
        return new SettingsSaveResult(
                settingsService.save(siteId, themeName, params.values(), params.pages(), params.variants()));
    }

    /**
     * Request body for {@code PUT /{siteId}/active} and {@code POST /{siteId}/preview-activate}.
     *
     * @param target       which site slot(s) the theme goes into: {@code desktop} (default) /
     *                     {@code mobile} / {@code both} — mobile-only leaves the desktop theme untouched
     * @param seedScope    seed import scope after activating: {@code none} (default) / {@code composition} / {@code all} (R5)
     * @param overwrite    how the seed treats existing origin rows (§6.1): {@code fillOnly} (default) /
     *                     {@code syncUnmodified} / {@code resetAllOrigin}
     */
    public record ActivateRequest(@NotBlank String themeName, @Nullable ActivateTarget target,
                                  @Nullable SeedScope seedScope,
                                  @Nullable SeedOverwrite overwrite) {
        ActivateTarget targetOrDefault() {
            return target != null ? target : ActivateTarget.DESKTOP;
        }
    }

    /**
     * Response for {@code PUT /{siteId}/active} (theme-seed-export §8.1 / D22).
     *
     * @param seed the seed run executed with the activation ({@code null} when {@code seedScope=none}) —
     *             per-failure detail rides {@code seed.content.failures} and
     *             {@code seed.composition.failures} (R2 channel, both halves)
     */
    public record ActivateResult(@Nullable SeedRunResult seed) {
    }

    /**
     * Request body for {@code POST …/seed/import} (§6.2).
     *
     * @param scope     {@code composition} / {@code all} (default; {@code none} → 400)
     * @param overwrite {@code fillOnly} (default) / {@code syncUnmodified} / {@code resetAllOrigin}
     */
    public record SeedImportParams(@Nullable SeedScope scope, @Nullable SeedOverwrite overwrite) {
    }

    /**
     * Request body for {@code POST …/export/preview} and {@code POST …/export}
     * (theme-seed-export §8).
     *
     * @param selection        selection tree result ({@code null} = default selection: this theme's
     *                         origin rows + composition references + closure)
     * @param scope            {@code content} / {@code composition} / {@code all} (default)
     * @param mode             export only: {@code refreshSeedDir} / {@code dataOnlyZip} / {@code themeZip}
     * @param adoptOrigin      write minted origin triples back to operator rows (§4.2, default false)
     * @param snapshotSettings write stored theme-settings values into the emitted manifests'
     *                         declared field defaults (§7.4, default false; emitted manifests only,
     *                         never the DB — data-only packages have no declared settings)
     * @param version          operator-editable package version (default: current manifest value;
     *                         never auto-bumped, §7.1)
     * @param confirmActive    required {@code true} to refresh the seed dir of the site's active
     *                         theme (Q1)
     * @param destination      ZIP modes only: {@code download} (default, browser attachment) /
     *                         {@code library} (save into the theme-package library, install-flow §6 P3)
     */
    public record ExportParams(@Nullable ExportSelectionParams selection, @Nullable String scope,
                               @Nullable String mode, @Nullable Boolean adoptOrigin,
                               @Nullable Boolean snapshotSettings,
                               @Nullable String version, @Nullable Boolean confirmActive,
                               @Nullable String destination) {
    }

    /**
     * Response for {@code POST …/export} with {@code destination=library} (install-flow §6 P3):
     * the package registered in the theme-package library instead of a browser download.
     *
     * @param packageId library row id
     * @param name      package (theme) name from the emitted manifest
     * @param version   package version from the emitted manifest
     * @param sha256    ZIP digest (hex, lowercase)
     * @param size      ZIP bytes
     * @param plan      export plan report (same as the download path)
     * @param adopted   origin rows adopted in this request ({@code adoptOrigin=false} → 0)
     */
    public record ExportToLibraryResult(Long packageId, String name, String version, String sha256,
                                        Long size, ExportPlan plan, int adopted) {
    }

    /**
     * Export selection (§3): operator-adjustable subset of the default selection.
     *
     * @param channelIds           checked channel ids ({@code null} = default selection)
     * @param topicIds             checked topic ids ({@code null} = default selection)
     * @param surveyIds            checked survey ids ({@code null} = default selection: theme-origin
     *                             surveys; surveys have no reference surface, so the explicit
     *                             multi-select is the only way operator surveys ride, E4d / D35)
     * @param formTypeIds          checked form-type ids ({@code null} = default selection: theme-origin
     *                             form types; form types have no reference surface, so the explicit
     *                             multi-select is the only way operator forms ride, E4e / D40)
     * @param linkGroupIds    checked link-group ids ({@code null} = default selection:
     *                             theme-origin groups; link groups have no reference surface, so the
     *                             explicit multi-select is the only way operator groups ride, E4f / D43)
     * @param articleCapPerChannel per-channel article cap (default 20; composition-referenced
     *                             and origin rows are exempt, D6)
     * @param includeDrafts        export draft articles too (default false)
     */
    public record ExportSelectionParams(@Nullable Set<Long> channelIds, @Nullable Set<Long> topicIds,
                                        @Nullable Set<Long> surveyIds, @Nullable Set<Long> formTypeIds,
                                        @Nullable Set<Long> linkGroupIds,
                                        @Nullable Integer articleCapPerChannel,
                                        @Nullable Boolean includeDrafts) {
    }

    /**
     * Theme settings save body (all three scopes, each a full replace; §4.2 / R3).
     *
     * @param values   global scope: group → field → value
     * @param pages    page scope: pageName → group → field → value (undeclared pages silently dropped)
     * @param variants variant scope: {@code surface:value} → group → field → value
     */
    public record SettingsParams(@Nullable Map<String, Map<String, Object>> values,
                                 @Nullable Map<String, Map<String, Map<String, Object>>> pages,
                                 @Nullable Map<String, Map<String, Map<String, Object>>> variants) {
    }

    /**
     * Theme settings save response (H5): undeclared keys are dropped (never rejected) —
     * the console surfaces them so a schema/manifest drift stops being silent.
     *
     * @param dropped dropped undeclared keys as dotted paths ({@code palette.badField},
     *                {@code pages.undeclared}, {@code variants.channel:grid.grid.gone})
     */
    public record SettingsSaveResult(List<String> dropped) {
    }

    /**
     * Theme settings response (§8.2 / §4.2).
     *
     * @param groups   global settings group definitions (raw field defs; console renders the form)
     * @param values   global merged values (declared defaults ⊕ stored)
     * @param pages    page-scope settings (each page manifest declaring a settings block)
     * @param variants variant-scope settings (each manifest-declared layout variant with one)
     */
    public record SettingsResult(List<Map<String, Object>> groups, Map<String, Map<String, Object>> values,
                                 List<PageSettingsResult> pages, List<VariantSettingsResult> variants) {
    }

    /**
     * Page-scope settings entry.
     *
     * @param name   page name (R3 value key {@code pages.<name>})
     * @param label  localized page label (from the page manifest)
     * @param groups settings group definitions
     * @param values merged values (declared defaults ⊕ stored)
     */
    public record PageSettingsResult(String name, Map<String, String> label, List<Map<String, Object>> groups,
                                     Map<String, Map<String, Object>> values) {
    }

    /**
     * Variant-scope settings entry.
     *
     * @param surface {@code channel} / {@code article} / {@code topic}
     * @param value   variant value (R3 value key {@code variants.<surface>:<value>})
     * @param label   localized variant label (from the manifest declaration)
     * @param groups  settings group definitions
     * @param values  merged values (declared defaults ⊕ stored)
     */
    public record VariantSettingsResult(String surface, String value, Map<String, String> label,
                                        List<Map<String, Object>> groups,
                                        Map<String, Map<String, Object>> values) {
    }

    /**
     * Theme page-manifest summary (A4 detail "Pages &amp; slots" tab, read-only).
     *
     * @param name        page name ({@code placement.page_ref_})
     * @param label       localized page label
     * @param templates   bound unassembled template names
     * @param route       SSR route pattern (template-track pages: null)
     * @param slots       slot / region definition summaries
     * @param hasSettings whether the page manifest declares a settings block (§4.2)
     */
    public record ThemePageResult(String name, Map<String, String> label, List<String> templates,
                                  @Nullable String route, List<ThemePageSlotResult> slots, boolean hasSettings) {
    }

    /**
     * Region definition summary line.
     */
    public record ThemePageSlotResult(String name, List<String> allowedShapes, boolean required) {
    }
}
