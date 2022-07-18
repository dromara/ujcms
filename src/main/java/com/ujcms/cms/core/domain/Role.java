package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.base.RoleBase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;

/**
 * 角色 实体类
 *
 * @author PONY
 */
public class Role extends RoleBase implements Serializable {
    /**
     * 是否全局共享
     */
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

    // TempFields
    /**
     * 文章权限，栏目ID列表。非数据库属性，用于接收前台请求。
     */
    private List<Integer> articlePermissions = new ArrayList<>();

    public List<Integer> getArticlePermissions() {
        return articlePermissions;
    }

    public void setArticlePermissions(List<Integer> articlePermissions) {
        this.articlePermissions = articlePermissions;
    }
    // endregion

    // StaticField
    /**
     * 后台管理员权限名
     */
    public static final String PERMISSION_BACKEND = "backend";

    public static final String[] PERMISSION_FIELDS = {"permission", "allPermission",
            "grantPermission", "allGrantPermission", "globalPermission", "allArticlePermission", "dataScope"};
    /**
     * 数据范围：所有
     */
    public static final short DATA_SCOPE_ALL = 1;
    /**
     * 数据范围：组织
     */
    public static final short DATA_SCOPE_ORG = 2;
    /**
     * 数据范围：自身
     */
    public static final short DATA_SCOPE_SELF = 3;
    // endregion
}