package com.ujcms.cms.core.composition;

/**
 * Shape 变更的兼容性分级（§13.1）。决定重载行为与卡片处理方式：
 * <ul>
 *   <li>{@link #COMPATIBLE_ADDITIVE} 新增可选字段 / 加默认值 / 放宽约束——静默重载，旧卡片以 null/默认读取</li>
 *   <li>{@link #COMPATIBLE_RENAME} 带 {@code rename} 别名的改名——静默重载，投影期套别名</li>
 *   <li>{@link #BREAKING_REQUIRED} 新增必填 / 收紧约束——重载并在健康页<b>标记</b>受影响卡片，仍可读</li>
 *   <li>{@link #BREAKING_REMOVAL} 删除/改类型/无别名改名——除非迁移块描述，否则<b>拒绝</b>重载</li>
 * </ul>
 *
 * @author PONY
 */
public enum ShapeCompatibility {
    /**
     * 兼容：纯新增
     */
    COMPATIBLE_ADDITIVE,
    /**
     * 兼容：带别名的改名
     */
    COMPATIBLE_RENAME,
    /**
     * 破坏：新增必填 / 收紧约束（加载并标记，不拒绝）
     */
    BREAKING_REQUIRED,
    /**
     * 破坏：删除/改类型/无迁移描述的改名（拒绝重载）
     */
    BREAKING_REMOVAL;

    /**
     * 是否为破坏性变更
     */
    public boolean breaking() {
        return this == BREAKING_REQUIRED || this == BREAKING_REMOVAL;
    }
}
