/**
 * 页面构成系统（Region/Row/Column/Panel）。
 *
 * <p>与 Block/BlockItem（简单层）并存的高级层页面构成机制：区域（Region）是 layout-as-data 结构树，
 * 按行（Row）/列（Column）组织面板（Panel）；面板即投放行的实例（Placement row = panel instance），
 * 内容形态由设置驱动的 Shape 组件决定（代码定义结构 + 操作员设置，Shopify 式）。
 * 详见 docs/spec/component-settings/plan.md。
 */
@NullMarked
package com.ujcms.cms.core.composition;

import org.jspecify.annotations.NullMarked;
