package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.OrgBase;
import com.ujcms.util.db.tree.TreeEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 组织实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Org extends OrgBase implements TreeEntity, Serializable {
    private static final long serialVersionUID = 1L;

    @JsonIgnore
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
    public List<String> getNames() {
        return getPaths().stream().map(OrgBase::getName).collect(Collectors.toList());
    }

    public List<Org> getChildren() {
        return children;
    }

    public void setChildren(List<Org> children) {
        this.children = children;
    }

    @Nullable
    @Override
    public Org getParent() {
        return parent;
    }

    public void setParent(Org parent) {
        this.parent = parent;
    }

    public List<Integer> getDescendants() {
        return descendants;
    }

    public void setDescendants(List<Integer> descendants) {
        this.descendants = descendants;
    }

    /**
     * 下级组织列表
     */
    @JsonIgnore
    private List<Org> children = new ArrayList<>();
    /**
     * 上级组织
     */
    @Nullable
    private Org parent;
    /**
     * 所有下级组织ID列表
     */
    @JsonIgnore
    private List<Integer> descendants = new ArrayList<>();
    /**
     * 前台会员默认组织ID
     */
    public static final int MEMBER_ORG_ID = 0;
}