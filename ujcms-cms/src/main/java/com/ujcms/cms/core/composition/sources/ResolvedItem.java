package com.ujcms.cms.core.composition.sources;

import java.util.Map;

/**
 * 解析期条目载体（component-settings §4.2）。查询/区块/手工条目经投影引擎产出的内存态条目，
 * 由 {@code PanelResolver} 做读取期装饰（调色板、引用展开）后平铺为契约 {@code items}。
 * 取代卡片目录退役前借用的 {@code Card} 实体承载（Phase D）：契约条目不携带生命周期、
 * 标签或来源实体标注，只有条目数据本身。
 *
 * @param id    来源实体ID（查询/区块条目）或列表序号（手工条目）；契约条目不输出，仅供调试
 * @param shape 目标组件名（投影所按的 item 字段结构归属）
 * @param data  按 item 字段类型包装后的条目数据
 * @author PONY
 */
public record ResolvedItem(Long id, String shape, Map<String, Object> data) {
}
