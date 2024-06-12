package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.base.RoleBase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;

/**
 * 角色实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Role extends RoleBase implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否全局共享
     */
    @Schema(description = "是否全局共享")
    public boolean isGlobal() {
        return getScope() == SCOPE_GLOBAL;
    }

    // Associations
    /**
     * 站点
     */
    @JsonIncludeProperties({"id", "name", "url"})
    private Site site = new Site();

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }
    // endregion

    // StaticField
    /**
     * 后台管理员权限名
     */
    public static final String PERMISSION_BACKEND = "backend";

    public static final List<String> PERMISSION_FIELDS = Collections.unmodifiableList(Arrays.asList(
            "permission", "allPermission", "grantPermission", "allGrantPermission", "globalPermission",
            "allArticlePermission", "allChannelPermission", "allStatusPermission", "dataScope"));

    /**
     * 数据范围：所有
     */
    public static final short DATA_SCOPE_ALL = 1;
    /**
     * 数据范围：有权限的组织
     */
    public static final short DATA_SCOPE_PERM_ORG = 2;
    /**
     * 数据范围：所属组织
     */
    public static final short DATA_SCOPE_ORG = 3;
    /**
     * 数据范围：自身
     */
    public static final short DATA_SCOPE_SELF = 4;
    // endregion
}