package com.ujcms.cms.core.component;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Parsed contents of a theme's optional {@code theme.manifest.json} metadata file. Descriptive only —
 * never used to trigger render-time behavior. See
 * <a href="docs/spec/template-spec/template-storage.md#thememanifestjson-optional-metadata">spec</a>.
 *
 * <p>Fields beyond the documented schema are silently ignored (Jackson configuration below)
 * so that future spec additions do not break older installers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThemeMetadata {
    /** 包类型：渲染器主题包（缺省） */
    public static final String KIND_THEME = "theme";
    /** 包类型：纯数据包（§8）——只含种子，可安装、不可激活 */
    public static final String KIND_DATA_ONLY = "data-only";

    @Nullable
    private String name;
    @Nullable
    private String themeName;
    @Nullable
    private String version;
    @Nullable
    private String templateSpecVersion;
    /** Package kind (manifest 2.0 §8): {@code theme} (default) or {@code data-only}. Consumed by the
     *  install validator (renderers rejected) and the activation gates (A5). */
    @Nullable
    private String kind;
    @Nullable
    private Author author;
    @Nullable
    private String description;
    @Nullable
    private String license;
    /** Theme homepage URL (manifest 2.0, display-only). */
    @Nullable
    private String homepage;
    /** Changelog URL (manifest 2.0, display-only). */
    @Nullable
    private String changelogUrl;
    @Nullable
    private String preview;
    /** Screenshot paths relative to the theme dir (manifest 2.0, gallery/detail display). */
    @Nullable
    private List<String> screenshots;
    @Nullable
    private List<String> tags;
    @Nullable
    private List<String> compatibleSiteTypes;
    @Nullable
    private String minUjcmsVersion;

    @Nullable public String getName() { return name; }
    public void setName(@Nullable String name) { this.name = name; }

    @Nullable public String getThemeName() { return themeName; }
    public void setThemeName(@Nullable String themeName) { this.themeName = themeName; }

    @Nullable public String getVersion() { return version; }
    public void setVersion(@Nullable String version) { this.version = version; }

    @Nullable public String getTemplateSpecVersion() { return templateSpecVersion; }
    public void setTemplateSpecVersion(@Nullable String templateSpecVersion) {
        this.templateSpecVersion = templateSpecVersion;
    }

    @Nullable public String getKind() { return kind; }
    public void setKind(@Nullable String kind) { this.kind = kind; }

    /** 是否纯数据包（kind 缺省视为 theme） */
    @JsonIgnore
    public boolean isDataOnly() {
        return KIND_DATA_ONLY.equals(kind);
    }

    @Nullable public Author getAuthor() { return author; }
    public void setAuthor(@Nullable Author author) { this.author = author; }

    @Nullable public String getDescription() { return description; }
    public void setDescription(@Nullable String description) { this.description = description; }

    @Nullable public String getLicense() { return license; }
    public void setLicense(@Nullable String license) { this.license = license; }

    @Nullable public String getHomepage() { return homepage; }
    public void setHomepage(@Nullable String homepage) { this.homepage = homepage; }

    @Nullable public String getChangelogUrl() { return changelogUrl; }
    public void setChangelogUrl(@Nullable String changelogUrl) { this.changelogUrl = changelogUrl; }

    @Nullable public String getPreview() { return preview; }
    public void setPreview(@Nullable String preview) { this.preview = preview; }

    @Nullable public List<String> getScreenshots() { return screenshots; }
    public void setScreenshots(@Nullable List<String> screenshots) { this.screenshots = screenshots; }

    @Nullable public List<String> getTags() { return tags; }
    public void setTags(@Nullable List<String> tags) { this.tags = tags; }

    @Nullable public List<String> getCompatibleSiteTypes() { return compatibleSiteTypes; }
    public void setCompatibleSiteTypes(@Nullable List<String> compatibleSiteTypes) {
        this.compatibleSiteTypes = compatibleSiteTypes;
    }

    @Nullable public String getMinUjcmsVersion() { return minUjcmsVersion; }
    public void setMinUjcmsVersion(@Nullable String minUjcmsVersion) {
        this.minUjcmsVersion = minUjcmsVersion;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        @Nullable private String name;
        @Nullable private String email;
        @Nullable private String url;

        @Nullable public String getName() { return name; }
        public void setName(@Nullable String name) { this.name = name; }

        @Nullable public String getEmail() { return email; }
        public void setEmail(@Nullable String email) { this.email = email; }

        @Nullable public String getUrl() { return url; }
        public void setUrl(@Nullable String url) { this.url = url; }
    }
}
