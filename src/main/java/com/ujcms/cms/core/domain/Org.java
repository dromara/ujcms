package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.OrgBase;
import com.ujcms.commons.db.tree.TreeEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.io.Serializable;
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

    @Nullable
    @Override
    public Org getParent() {
        return parent;
    }

    public void setParent(Org parent) {
        this.parent = parent;
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
     * 前台会员默认组织ID
     */
    public static final int MEMBER_ORG_ID = 0;
}