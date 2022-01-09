package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ujcms.core.domain.base.OrgBase;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 组织 实体类
 *
 * @author PONY
 */
public class Org extends OrgBase implements Serializable {
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
    public Org getParent() {
        return parent;
    }

    public void setParent(Org parent) {
        this.parent = parent;
    }

    @JsonIgnore
    private List<Org> children = new ArrayList<>();
    @Nullable
    private Org parent;
}