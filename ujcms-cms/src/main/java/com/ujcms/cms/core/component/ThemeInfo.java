package com.ujcms.cms.core.component;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Admin-facing description of a single installed theme directory under
 * {@code templates/{siteId}/}. Built by {@link ThemeInstallService#listThemes(Long)} and
 * consumed by the admin theme-picker UI.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThemeInfo {
    /** Directory name (machine identifier). */
    private final String themeName;
    /** Display name from {@code theme.manifest.json}.name, or directory name if absent. */
    private final String displayName;
    /** Whether this theme is the site's current desktop theme. */
    private final boolean active;
    /** Whether this theme is the site's current mobile theme. */
    private final boolean activeMobile;
    @Nullable
    private final String version;
    @Nullable
    private final ThemeMetadata metadata;
    /** Whether {@code theme.manifest.json} was present. */
    private final boolean hasManifest;
    /** Whether the theme bundles an importable seed ({@code seed/} directory present). */
    private final boolean seedAvailable;
    /** {@code ujcms_theme_state.seed_imported_} — historical "was imported at least once". */
    private final boolean seedImported;
    /** Whether origin rows of this theme (content or any composition table) are live in the DB. */
    private final boolean demoDataPresent;
    /**
     * 是否已登记（install-flow §14 / D43）：{@code ujcms_theme_state} 行存在即视为已登记——
     * 状态行是实例侧的来历标记（版本/设置所在）。无行的目录（备份恢复 / rsync 迁入 / 无清单
     * 历史安装）功能完好，缺的是来历与包库可移植性；控制台以「未登记」徽标 + 登记动作呈现。
     */
    private final boolean registered;

    public ThemeInfo(String themeName, String displayName,
                     boolean active, boolean activeMobile,
                     @Nullable String version,
                     @Nullable ThemeMetadata metadata,
                     boolean hasManifest,
                     boolean seedAvailable, boolean seedImported, boolean demoDataPresent,
                     boolean registered) {
        this.themeName = themeName;
        this.displayName = displayName;
        this.active = active;
        this.activeMobile = activeMobile;
        this.version = version;
        this.metadata = metadata;
        this.hasManifest = hasManifest;
        this.seedAvailable = seedAvailable;
        this.seedImported = seedImported;
        this.demoDataPresent = demoDataPresent;
        this.registered = registered;
    }

    public String getThemeName() { return themeName; }
    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }
    public boolean isActiveMobile() { return activeMobile; }
    @Nullable public String getVersion() { return version; }
    @Nullable public ThemeMetadata getMetadata() { return metadata; }
    public boolean isHasManifest() { return hasManifest; }
    public boolean isSeedAvailable() { return seedAvailable; }
    public boolean isSeedImported() { return seedImported; }
    public boolean isDemoDataPresent() { return demoDataPresent; }
    public boolean isRegistered() { return registered; }
}
