package com.ujcms.cms.core.web.backendapi;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.component.ThemeInstallReport;
import com.ujcms.cms.core.domain.ThemePackage;
import com.ujcms.cms.core.service.ThemeInstallService.ActivateTarget;
import com.ujcms.cms.core.service.ThemeInstallService.SeedScope;
import com.ujcms.cms.core.service.ThemePackageService;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * 主题包库 admin API（install-flow §6 P3 / D5）。全局面（包无站点维度）。Endpoints:
 * <ul>
 *   <li>{@code GET    /api/backend/core/theme-package}          — list packages</li>
 *   <li>{@code POST   /api/backend/core/theme-package/upload}   — upload ZIP into the library</li>
 *   <li>{@code POST   /api/backend/core/theme-package/install}  — install a library package to a site</li>
 *   <li>{@code DELETE /api/backend/core/theme-package/{id}}     — delete package (row + ZIP)</li>
 * </ul>
 *
 * <p>库面板可见性/管理走核心权限码 {@code theme:package}（Q6 决议：不设商业档位——
 * 「变现主题，不变现安装器」）；「安装到站点」沿用 {@code theme:install}（对站点的
 * 安装动作与上传安装同一权限）。
 *
 * @author PONY
 */
@RestController("backendThemePackageController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/theme-package")
public class ThemePackageController {
    private final ThemePackageService service;

    public ThemePackageController(ThemePackageService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('theme:package','*')")
    public List<ThemePackage> list() {
        return service.list();
    }

    /**
     * 上传 ZIP 入库（只入库不安装；安装是独立动作）。清单必须声明 {@code themeName}——
     * 库面板无兜底名（历史无清单包请走主题画廊的上传安装入径，操作员主题名即兜底）。
     */
    @PostMapping("upload")
    @PreAuthorize("hasAnyAuthority('theme:package','*')")
    @OperationLog(module = "themePackage", operation = "upload", type = OperationType.CREATE)
    public ThemePackage upload(@RequestParam("file") MultipartFile file) {
        return service.save(file, ThemePackage.SOURCE_UPLOAD, null);
    }

    /**
     * 从库安装到站点（§6：同一包装到任意站点，零重传）。
     */
    @PostMapping("install")
    @PreAuthorize("hasAnyAuthority('theme:install','*')")
    @OperationLog(module = "themePackage", operation = "install", type = OperationType.CREATE)
    public ThemeInstallReport install(@RequestBody @NotNull InstallParams params) {
        SeedScope seedScope = params.seedScope() != null ? parseSeedScope(params.seedScope()) : SeedScope.NONE;
        return service.install(params.packageId(), params.siteId(), params.themeName(),
                params.activate(), seedScope);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyAuthority('theme:package','*')")
    @OperationLog(module = "themePackage", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@PathVariable Long id) {
        service.delete(id);
        return Responses.ok();
    }

    /**
     * 表单/JSON 的 seedScope 显式解析（R5 小写 {@code none|composition|all}；未知值 400 而非 500）。
     */
    private static SeedScope parseSeedScope(String value) {
        try {
            return SeedScope.parse(value);
        } catch (IllegalArgumentException e) {
            throw new Http400Exception("seedScope must be none|composition|all: " + value);
        }
    }

    /**
     * Request body for {@code POST /install}.
     *
     * @param packageId      library package id
     * @param siteId         target site id
     * @param themeName      install directory name ({@code null} = package name)
     * @param activate       site slot(s) to activate the theme into after install:
     *                       {@code desktop} / {@code mobile} / {@code both}; absent = install only
     * @param seedScope      seed import scope: {@code none} (default) / {@code composition} / {@code all}
     */
    public record InstallParams(@NotNull Long packageId, @NotNull Long siteId, @Nullable String themeName,
                                @Nullable ActivateTarget activate, @Nullable String seedScope) {
    }
}
