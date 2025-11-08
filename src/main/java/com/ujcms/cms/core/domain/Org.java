package com.ujcms.cms.core.domain;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ujcms.cms.core.domain.generated.GeneratedOrg;
import com.ujcms.commons.db.tree.TreeEntity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 组织实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Org extends GeneratedOrg implements TreeEntity {

    @Schema(description = "组织层级。从一级组织到当前组织的列表。只有在单独查询组织对象时，才有此属性；查询组织列表时，此属性只包含当前组织")
    @JsonIncludeProperties({"id", "name"})
    public List<Org> getPaths() {
        LinkedList<Org> parents = new LinkedList<>();
        Org bean = this;
        while (bean != null) {
            parents.addFirst(bean);
            bean = bean.getParent();
        }
        return parents;
    }

    @Schema(description = "名称层级列表")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public List<String> getNames() {
        return getPaths().stream().map(GeneratedOrg::getName).toList();
    }

    @Nullable
    @Override
    public Org getParent() {
        return parent;
    }

    public void setParent(Org parent) {
        this.parent = parent;
    }

    @Nullable
    public List<Org> getChildren() {
        return children;
    }

    public void setChildren(List<Org> children) {
        this.children = children;
    }

    /**
     * 是否有子组织
     */
    @Nullable
    private Boolean hasChildren;

    @Nullable
    public Boolean getHasChildren() {
        return hasChildren;
    }

    public void setHasChildren(@Nullable Boolean hasChildren) {
        this.hasChildren = hasChildren;
    }

    /**
     * 上级组织
     */
    @JsonIgnore
    @Nullable
    private Org parent;
    /**
     * 子组织列表
     */
    @JsonIgnore
    private List<Org> children = new ArrayList<>();
    /**
     * 前台会员默认组织ID
     */
    public static final Long MEMBER_ORG_ID = 0L;
    /**
     * 根组织ID
     */
    public static final Long ROOT_ORG_ID = 1L;
}