package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedThemePackage;

import java.util.Objects;

/**
 * 主题包实体类（install-flow §6 P3 / D5）：全局行（无站点维度）——包是站点无关的版本化制品，
 * 实例（解包目录 + {@code theme_state}）才按站点落地。ZIP 本体按
 * {@code <name>/<version>.zip} 存于主题包存储点（{@code Config.packageStorage}）。
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ThemePackage extends GeneratedThemePackage {
    public static final String NOT_FOUND = "ThemePackage not found. ID: ";

    /**
     * 来源：应用市场下载
     */
    public static final String SOURCE_MARKETPLACE = "marketplace";
    /**
     * 来源：控制台上传
     */
    public static final String SOURCE_UPLOAD = "upload";
    /**
     * 来源：站点导出入库
     */
    public static final String SOURCE_EXPORT = "export";
    /**
     * 来源：随发行内置（构建期打包的默认主题）
     */
    public static final String SOURCE_BUNDLED = "bundled";
    /**
     * 来源：目录登记（install-flow §14 / D42-D43——已装目录确定性重打包入库；恢复/迁移场景的
     * 来历补录，与导出侧「认领 adoptOrigin」及切换孤儿归档均无关）
     */
    public static final String SOURCE_REGISTERED = "registered";

    /**
     * ZIP 在包存储点内的存储路径（{@code /<name>/<version>.zip}）
     */
    public String getStorePath() {
        return "/" + getName() + "/" + getVersion() + ".zip";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ThemePackage bean = (ThemePackage) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
