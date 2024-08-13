package com.ujcms.commons.db.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * 树形结构排序类
 *
 * @author PONY
 */
public class TreeSortEntity {
    private TreeEntity entity;
    private List<TreeSortEntity> children = new ArrayList<>();

    /**
     * 获取所有后代的ID，包括自身。用于树形结构闭包表关系。
     */
    public void addToRelations(List<TreeRelationEntity> relations) {
        relations.add(new TreeRelationEntity(entity.getId(), entity.getId()));
        handleDescendantIds(relations, entity.getId(), children);
    }

    public void handleDescendantIds(List<TreeRelationEntity> relations, Long ancestorId, List<TreeSortEntity> list) {
        for (TreeSortEntity item : list) {
            relations.add(new TreeRelationEntity(ancestorId, item.getEntity().getId()));
            handleDescendantIds(relations, ancestorId, item.getChildren());
        }
    }

    public TreeSortEntity(TreeEntity entity) {
        this.entity = entity;
    }

    public TreeEntity getEntity() {
        return entity;
    }

    public void setEntity(TreeEntity entity) {
        this.entity = entity;
    }

    public List<TreeSortEntity> getChildren() {
        return children;
    }

    public void setChildren(List<TreeSortEntity> children) {
        this.children = children;
    }
}
