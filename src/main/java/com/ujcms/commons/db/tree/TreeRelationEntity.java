package com.ujcms.commons.db.tree;

/**
 * 树形关系实体类
 *
 * @author PONY
 */
public class TreeRelationEntity implements TreeRelation {
    private Long ancestorId;
    private Long descendantId;

    public TreeRelationEntity(Long ancestorId, Long descendantId) {
        this.ancestorId = ancestorId;
        this.descendantId = descendantId;
    }

    @Override
    public Long getAncestorId() {
        return ancestorId;
    }

    @Override
    public void setAncestorId(Long ancestorId) {
        this.ancestorId = ancestorId;
    }

    @Override
    public Long getDescendantId() {
        return descendantId;
    }

    @Override
    public void setDescendantId(Long descendantId) {
        this.descendantId = descendantId;
    }
}
