package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 链接组展示位词汇表（link-groups D7）。主题清单顶层 {@code linkSlots} 块声明本主题模板会渲染
 * 哪些展示位（如页脚链接栏、友情链接展示条）；链接组的 {@code slot_} 取词汇表内名称，模板经
 * {@code [@LinkGroupList slot='<name>']} 按展示位取组渲染。空 slot 的组不自动展示（模板按别名引用）。
 * 结构与解析方式镜像 {@link LayoutVariantCatalog}（更轻：仅 name + label）。
 *
 * @author PONY
 */
public final class LinkSlotCatalog {
    /**
     * 空词汇表（清单无 {@code linkSlots} 块）
     */
    public static final LinkSlotCatalog EMPTY = new LinkSlotCatalog(List.of());

    /**
     * 有序展示位列表（按清单声明顺序）
     */
    private final List<Slot> slots;

    private LinkSlotCatalog(List<Slot> slots) {
        this.slots = List.copyOf(slots);
    }

    /**
     * 解析 {@code linkSlots} 节点（{@code [{name, label}, ...]}，{@code null}/缺失/非数组 → 空词汇表）。
     * 次序即数组顺序。
     *
     * @param node {@code linkSlots} 节点（已转为 {@code List}）
     * @return 展示位词汇表
     */
    public static LinkSlotCatalog parse(@Nullable Object node) {
        if (!(node instanceof List<?> list)) {
            return EMPTY;
        }
        List<Slot> slots = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> slotMap)) {
                continue;
            }
            String name = slotMap.get("name") instanceof String s ? s : null;
            if (name == null || name.isBlank()) {
                continue;
            }
            Map<String, String> label = ShapeLoader.toStringMap(slotMap.get("label"));
            slots.add(new Slot(name, label));
        }
        return slots.isEmpty() ? EMPTY : new LinkSlotCatalog(slots);
    }

    public List<Slot> getSlots() {
        return slots;
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /**
     * 单个展示位声明项
     *
     * @param name  展示位名（链接组 {@code slot_} 存值）
     * @param label 多语言标签（locale → 文本）
     */
    public record Slot(String name, Map<String, String> label) {
        public Slot(String name, Map<String, String> label) {
            this.name = name;
            this.label = Collections.unmodifiableMap(label);
        }
    }
}
