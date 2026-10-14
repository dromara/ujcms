package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ujcms.cms.core.domain.generated.GeneratedPlacement;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 投放实体类（页面构成）。一行 = 页面区域内的一个面板实例：组件（{@code panel_shape_}）+
 * 设置块（{@code settings_json_}，component-settings §3），树位置在
 * {@code ujcms_page_region.layout_json_}。
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Placement extends GeneratedPlacement {
    /**
     * 显示设备：全部（缺省；design-review P2-5 / docs/decisions/visual-builder/04）
     */
    public static final String VISIBILITY_ALL = "all";
    /**
     * 显示设备：仅桌面
     */
    public static final String VISIBILITY_DESKTOP = "desktop";
    /**
     * 显示设备：仅移动
     */
    public static final String VISIBILITY_MOBILE = "mobile";

    public static final String NOT_FOUND = "Placement not found. ID: ";

    /**
     * 归一显示设备值：{@code null}/空白/未知值按 {@link #VISIBILITY_ALL}（宽容读取；
     * 写入合法性由 {@code PlacementService} 校验）。
     */
    public static String normalizeVisibility(@Nullable String visibility) {
        if (VISIBILITY_DESKTOP.equals(visibility) || VISIBILITY_MOBILE.equals(visibility)) {
            return visibility;
        }
        return VISIBILITY_ALL;
    }

    /**
     * 面板设置值（解析自 settingsJson 的暂存，component-settings §3）
     */
    @Nullable
    private transient Map<String, Object> settings;

    /**
     * 面板设置值（组件实例的一个配置块，component-settings §3）
     */
    @Schema(description = "面板设置值")
    public Map<String, Object> getSettings() {
        if (settings != null) {
            return settings;
        }
        settings = parseJson(getSettingsJson(), "Placement settings json invalid. ID: ");
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    /**
     * 设置块中的标题（{@code title} 设置值，操作员辨识组件实例用，如「热点新闻」）。
     * 设置 JSON 损坏时返回 {@code null}——损坏本身由构成健康暴露，此处不阻断报告类读取。
     */
    @JsonIgnore
    @Nullable
    public String getSettingsTitle() {
        Object title;
        try {
            title = getSettings().get("title");
        } catch (IllegalStateException e) {
            return null;
        }
        if (title instanceof String str && !str.isBlank()) {
            return str;
        }
        return null;
    }

    /**
     * 将 settings 序列化回 JSON 列（保存前调用）
     */
    public void disassemble() {
        try {
            setSettingsJson(getSettings().isEmpty() ? null : MAPPER.writeValueAsString(getSettings()));
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize placement json. ID: " + getId(), e);
        }
    }

    private Map<String, Object> parseJson(@Nullable String json, String errorPrefix) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>(8);
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException e) {
            throw new IllegalStateException(errorPrefix + getId(), e);
        }
    }

    @Override
    @JsonIgnore
    @Nullable
    public String getSettingsJson() {
        return super.getSettingsJson();
    }

    /**
     * 是否已归档（主题切换孤立后的软归档状态）
     */
    @Schema(description = "是否已归档")
    public boolean isArchived() {
        return getArchivedAt() != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Placement bean = (Placement) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
