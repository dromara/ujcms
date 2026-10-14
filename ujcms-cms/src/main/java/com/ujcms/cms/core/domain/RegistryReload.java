package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedRegistryReload;

import java.util.Objects;

/**
 * 注册表重载审计实体类（页面构成）
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class RegistryReload extends GeneratedRegistryReload {
    /**
     * 触发来源：启动扫描
     */
    public static final String SOURCE_BOOT = "boot";
    /**
     * 触发来源：文件监听
     */
    public static final String SOURCE_WATCHER = "watcher";
    /**
     * 触发来源：后台手动重扫
     */
    public static final String SOURCE_RESCAN = "rescan";
    /**
     * 触发来源：主题安装
     */
    public static final String SOURCE_INSTALL = "install";

    /**
     * 结果：成功
     */
    public static final String RESULT_SUCCESS = "success";
    /**
     * 结果：失败
     */
    public static final String RESULT_FAILURE = "failure";

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RegistryReload bean = (RegistryReload) o;
        return Objects.equals(getId(), bean.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
