package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.ujcms.cms.core.domain.generated.GeneratedUserExt;
import com.ujcms.cms.core.support.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户扩展数据实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class UserExt extends GeneratedUserExt {
    /**
     * 控制台偏好 JSON（列 preferences_，MEDIUMTEXT）。手写列，不在生成器输出中（generatorCoreConfig 已 ignoreColumn）。
     * 控制台只经 {@link #getPreferences()} / {@link #setPreferences(Preferences)} 读写；
     * 两个访问器均不参与用户 JSON 序列化，偏好由个人接口与 backend-state 显式返回。
     */
    @Nullable
    private String preferencesJson;
    @Nullable
    private transient Preferences preferences;

    @JsonIgnore
    @Nullable
    public String getPreferencesJson() {
        return preferencesJson;
    }

    public void setPreferencesJson(@Nullable String preferencesJson) {
        this.preferencesJson = preferencesJson;
        this.preferences = null;
    }

    @JsonIgnore
    public Preferences getPreferences() {
        if (preferences != null) {
            return preferences;
        }
        String json = getPreferencesJson();
        if (StringUtils.isBlank(json)) {
            preferences = new Preferences();
            return preferences;
        }
        try {
            preferences = Constants.MAPPER.readValue(json, Preferences.class);
            return preferences;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read value of Preferences: " + json, e);
        }
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
        try {
            this.preferencesJson = Constants.MAPPER.writeValueAsString(preferences);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write value of Preferences", e);
        }
    }

    /**
     * 控制台偏好（docs/spec/console-shell/plan.md）。未知字段忽略，便于控制台先于后端增删偏好项；
     * 取值范围由校验注解约束，控制台再按自身预设归一。外观两项（侧栏风格、主题色）为空即跟随
     * 全局设置的控制台默认外观（{@link Config.Console}，D7）——有效值由控制台按“自选值 ?? 默认值”合成，服务端不合成。
     */
    @Schema(name = "UserExt.Preferences", description = "控制台偏好")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Preferences implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 侧栏风格：light（浅色）| dark（深色）；空为跟随控制台默认外观
         */
        @Nullable
        @Pattern(regexp = "^(light|dark)$")
        @Schema(description = "侧栏风格：light | dark；空为跟随默认")
        private String sidebarTheme;
        /**
         * 主题色，#rrggbb，预设色板由控制台维护；空为跟随控制台默认外观
         */
        @Nullable
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$")
        @Schema(description = "主题色 #rrggbb；空为跟随默认")
        private String themeColor;
        /**
         * 首页偏好
         */
        @Valid
        @NotNull
        @Schema(description = "首页偏好")
        private Home home = new Home();

        @Nullable
        public String getSidebarTheme() {
            return sidebarTheme;
        }

        public void setSidebarTheme(@Nullable String sidebarTheme) {
            this.sidebarTheme = sidebarTheme;
        }

        @Nullable
        public String getThemeColor() {
            return themeColor;
        }

        public void setThemeColor(@Nullable String themeColor) {
            this.themeColor = themeColor;
        }

        public Home getHome() {
            return home;
        }

        public void setHome(Home home) {
            this.home = home;
        }

        /**
         * 首页偏好：隐藏的区块与常用功能（均为控制台侧的标识——区块键、路由名）
         */
        @Schema(name = "UserExt.Preferences.Home", description = "首页偏好")
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Home implements Serializable {
            @Serial
            private static final long serialVersionUID = 1L;

            public static final int HIDDEN_MAX = 20;
            public static final int SHORTCUTS_MAX = 12;

            /**
             * 首页隐藏的区块键
             */
            @Size(max = HIDDEN_MAX)
            @Schema(description = "首页隐藏的区块键")
            private List<@NotBlank @Size(max = 64) String> hidden = new ArrayList<>();
            /**
             * 常用功能（控制台路由名，有序）
             */
            @Size(max = SHORTCUTS_MAX)
            @Schema(description = "常用功能（路由名，有序）")
            private List<@NotBlank @Size(max = 64) String> shortcuts = new ArrayList<>();

            public List<String> getHidden() {
                return hidden;
            }

            public void setHidden(List<String> hidden) {
                this.hidden = hidden;
            }

            public List<String> getShortcuts() {
                return shortcuts;
            }

            public void setShortcuts(List<String> shortcuts) {
                this.shortcuts = shortcuts;
            }
        }
    }
}
