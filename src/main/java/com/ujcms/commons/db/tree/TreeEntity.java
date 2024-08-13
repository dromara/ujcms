package com.ujcms.commons.db.tree;

import org.springframework.lang.Nullable;

/**
 * 树形实体接口
 *
 * @author PONY
 */
public interface TreeEntity {
    /**
     * 获取ID
     *
     * @return ID
     */
    Long getId();

    /**
     * 设置ID
     *
     * @param id ID
     */
    void setId(Long id);

    /**
     * 获取上级节点ID
     *
     * @return 上级节点ID
     */
    @Nullable
    Long getParentId();

    /**
     * 设置上级节点ID
     *
     * @param parentId 上级节点ID
     */
    void setParentId(@Nullable Long parentId);

    /**
     * 获取上级节点
     *
     * @return 上级节点
     */
    @Nullable
    TreeEntity getParent();

    /**
     * 获取节点深度
     *
     * @return 节点深度
     */
    Short getDepth();

    /**
     * 设置节点深度
     *
     * @param depth 节点深度
     */
    void setDepth(Short depth);

    /**
     * 获取节点顺序
     *
     * @return 节点顺序
     */
    Integer getOrder();

    /**
     * 设置节点顺序
     *
     * @param order 节点顺序
     */
    void setOrder(Integer order);
}
