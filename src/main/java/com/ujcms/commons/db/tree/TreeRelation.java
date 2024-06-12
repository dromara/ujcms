package com.ujcms.commons.db.tree;

/**
 * 树形关系
 *
 * @author PONY
 */
public interface TreeRelation {
    /**
     * 获取祖先ID
     *
     * @return 祖先ID
     */
    Long getAncestorId();

    /**
     * 设置祖先ID
     *
     * @param ancestorId 祖先ID
     */
    void setAncestorId(Long ancestorId);

    /**
     * 获取后代ID
     *
     * @return 后代ID
     */
    Long getDescendantId();

    /**
     * 设置后代ID
     *
     * @param descendantId 后代ID
     */
    void setDescendantId(Long descendantId);
}
