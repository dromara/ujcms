package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JacksonException;
import com.ujcms.cms.core.domain.generated.GeneratedThemeState;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 主题构成状态实体类（页面构成）。每 {@code (站点, 主题)} 一行（桌面 / 移动主题各自一行，
 * 按需懒创建，§6.3）：记录主题版本、种子是否已导入、操作员设置值（设置结构在主题包的
 * {@code theme.manifest.json#settings} 内，此处只存值）。
 *
 * <p>{@code settings_json_} 为结构化存储（theme-marketplace R3）：
 * <pre>{@code
 * { "global":   { "<group>": { "<field>": <value> } },
 *   "pages":    { "<pageName>": { "<group>": { "<field>": <value> } } },
 *   "variants": { "<surface>:<value>": { "<group>": { "<field>": <value> } } } }
 * }</pre>
 * 全局值即原扁平分组映射（迁移时整体包入 {@code global}）；页面 / 变体作用域值随 manifest 2.0
 * 的页面设置（Phase A1）写入，避免保留前缀键的杂技。
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ThemeState extends GeneratedThemeState {
    public static final String NOT_FOUND = "ThemeState not found. ID: ";

    /**
     * 设置值（解析自 settingsJson 的暂存）
     */
    @Nullable
    private transient SettingValues settings;

    /**
     * 设置值（R3 结构化：global / pages / variants）。操作员编辑的设置值；声明默认值不存于此
     * （合并在读取期完成）。
     */
    @Schema(description = "设置值")
    public SettingValues getSettings() {
        if (settings != null) {
            return settings;
        }
        String json = getSettingsJson();
        if (StringUtils.isBlank(json)) {
            settings = new SettingValues();
            return settings;
        }
        try {
            settings = MAPPER.readValue(json, SettingValues.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("ThemeState settings json invalid. ID: " + getId(), e);
        }
        return settings;
    }

    public void setSettings(SettingValues settings) {
        this.settings = settings;
    }

    /**
     * 将 settings 序列化回 settingsJson（保存前调用）
     */
    public void disassemble() {
        try {
            setSettingsJson(getSettings().isEmpty() ? null : MAPPER.writeValueAsString(getSettings()));
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize theme settings. ID: " + getId(), e);
        }
    }

    @Override
    @JsonIgnore
    @Nullable
    public String getSettingsJson() {
        return super.getSettingsJson();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ThemeState bean = (ThemeState) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    /**
     * R3 结构化设置值。三个作用域的嵌套深度不同：{@code global} 为 分组 → 字段 → 值；
     * {@code pages} / {@code variants} 各多一层作用域键（页面名 / {@code surface:value}）。
     * 空作用域不序列化（{@code NON_EMPTY}），存量 JSON 保持精简。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class SettingValues {
        private Map<String, Map<String, Object>> global = new LinkedHashMap<>(4);
        private Map<String, Map<String, Map<String, Object>>> pages = new LinkedHashMap<>(4);
        private Map<String, Map<String, Map<String, Object>>> variants = new LinkedHashMap<>(4);

        public Map<String, Map<String, Object>> getGlobal() {
            return global;
        }

        public void setGlobal(Map<String, Map<String, Object>> global) {
            this.global = global;
        }

        public Map<String, Map<String, Map<String, Object>>> getPages() {
            return pages;
        }

        public void setPages(Map<String, Map<String, Map<String, Object>>> pages) {
            this.pages = pages;
        }

        public Map<String, Map<String, Map<String, Object>>> getVariants() {
            return variants;
        }

        public void setVariants(Map<String, Map<String, Map<String, Object>>> variants) {
            this.variants = variants;
        }

        @JsonIgnore
        public boolean isEmpty() {
            return global.isEmpty() && pages.isEmpty() && variants.isEmpty();
        }
    }
}
