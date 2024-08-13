package com.ujcms.commons.db.tree;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 树形结构移动类型
 *
 * @author PONY
 */
public enum TreeMoveType {
    /**
     * 移动到元素之后
     */
    AFTER("after"),
    /**
     * 移动到元素之前
     */
    BEFORE("before"),
    /**
     * 移动到元素之内，并排到最后的位置
     */
    INNER("inner");

    @JsonValue
    private final String name;

    TreeMoveType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
