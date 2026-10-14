package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面清单。声明页面的稳定标识（{@code name}，即 {@code placement.page_ref_}）、
 * 绑定的模板名（模板轨道，精确匹配、无隐式回退）、目录排序权重（{@code order}）与区域集合
 * （清单键沿用 {@code slots}；经典插槽已随插槽子系统于 component-settings Phase D 退役，
 * 每个条目都是区域）。不可变；由 {@link ManifestLoader} 构建。
 *
 * @author PONY
 */
public final class PageManifest {
    /**
     * 插槽种类：区域（操作员拖拽编排的行/列/面板结构，page-regions §4）——唯一合法值，
     * 清单条目必须显式声明 {@code "kind": "region"}
     */
    public static final String KIND_REGION = "region";
    /**
     * 页面目录序：按 {@link #getOrder()} 升序，未声明者排在已声明者之后，同权重按 {@code name}。
     * 注册表快照按此序固定每个主题的清单顺序，页面目录/区域目录/主题详情同序。
     */
    public static final Comparator<PageManifest> DECLARED_ORDER = Comparator
            .comparingInt((PageManifest manifest) -> manifest.order == null ? Integer.MAX_VALUE : manifest.order)
            .thenComparing(PageManifest::getName);

    private final String name;
    private final Map<String, String> label;
    private final List<String> templates;
    @Nullable
    private final String route;
    /**
     * 目录排序权重（升序）。未声明 → {@code null}，排在所有已声明页面之后。
     */
    @Nullable
    private final Integer order;
    private final Map<String, SlotDef> slots;
    /**
     * 页面作用域设置结构（manifest 2.0 §4.2；与全局 settings 同语法，存值键 {@code pages.<pageName>}）。
     * 未声明 → {@link ThemeSettingsSchema#EMPTY}。
     */
    private final ThemeSettingsSchema settings;
    private final Map<String, Object> raw;

    public PageManifest(String name, Map<String, String> label, List<String> templates, @Nullable String route,
                        @Nullable Integer order, Map<String, SlotDef> slots, ThemeSettingsSchema settings,
                        Map<String, Object> raw) {
        this.name = name;
        this.label = Collections.unmodifiableMap(new LinkedHashMap<>(label));
        this.templates = List.copyOf(templates);
        this.route = route;
        this.order = order;
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        this.settings = settings;
        this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getLabel() {
        return label;
    }

    /**
     * 绑定的未组装模板名（如 {@code index}、{@code channel_video}）。可为空（纯 SSR 页面）。
     */
    public List<String> getTemplates() {
        return templates;
    }

    @Nullable
    public String getRoute() {
        return route;
    }

    /**
     * 目录排序权重（升序；见 {@link #DECLARED_ORDER}）。未声明 → {@code null}。
     */
    @Nullable
    public Integer getOrder() {
        return order;
    }

    public Map<String, SlotDef> getSlots() {
        return slots;
    }

    /**
     * 页面作用域设置结构（§4.2）。未声明 → {@link ThemeSettingsSchema#EMPTY}。
     */
    public ThemeSettingsSchema getSettings() {
        return settings;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    /**
     * 以替换后的区域集合复制清单（其余字段不变）。注册表快照构建时用于把共享引用解引用为宿主定义
     * （page-regions §15）。
     */
    PageManifest withSlots(Map<String, SlotDef> replaced) {
        return new PageManifest(name, label, templates, route, order, replaced, settings, raw);
    }

    /**
     * 区域定义（page-regions §4）：以 {@code allowedShapes}/{@code allowedRowLayouts}/
     * {@code maxRows}/{@code maxPanelsPerColumn} 约束操作员编排的行/列/面板结构。
     */
    public static final class SlotDef {
        private final boolean required;
        private final List<String> allowedShapes;
        private final List<String> allowedRowLayouts;
        @Nullable
        private final Integer maxRows;
        @Nullable
        private final Integer maxPanelsPerColumn;
        /**
         * 区域显示名（按 locale；清单 {@code label}，未声明为空映射）——构建器检查器/结构视图/顶栏据此
         * 显示「主区域」而非原始区域名。共享区域引用解引用时随宿主定义复制。
         */
        private final Map<String, String> label;
        /**
         * 区域描述（清单 {@code description}；未声明为 {@code null}）。
         */
        @Nullable
        private final String description;
        /**
         * 共享区域的宿主页面名（page-regions §15）：清单以 {@code {"kind":"region","shared":"<page>"}}
         * 引用同主题另一页面的同名完整声明。{@code null} = 本页自有定义。注册表快照构建时把引用
         * 解引用为宿主定义的副本并保留此标记；无法解引用（宿主不存在 / 未完整声明 / 链式或自引用）
         * 则 {@code allowedShapes} 保持为空，由 {@link SnapshotValidator} 报错。
         */
        @Nullable
        private final String sharedFrom;

        public SlotDef(boolean required, List<String> allowedShapes, List<String> allowedRowLayouts,
                       @Nullable Integer maxRows, @Nullable Integer maxPanelsPerColumn) {
            this(required, allowedShapes, allowedRowLayouts, maxRows, maxPanelsPerColumn, Map.of(), null, null);
        }

        public SlotDef(boolean required, List<String> allowedShapes, List<String> allowedRowLayouts,
                       @Nullable Integer maxRows, @Nullable Integer maxPanelsPerColumn,
                       Map<String, String> label, @Nullable String description, @Nullable String sharedFrom) {
            this.required = required;
            this.allowedShapes = List.copyOf(allowedShapes);
            this.allowedRowLayouts = List.copyOf(allowedRowLayouts);
            this.maxRows = maxRows;
            this.maxPanelsPerColumn = maxPanelsPerColumn;
            this.label = Collections.unmodifiableMap(new LinkedHashMap<>(label));
            this.description = description;
            this.sharedFrom = sharedFrom;
        }

        /**
         * 未解引用的共享引用（装载期形态）：仅记宿主页面名，约束为空。
         */
        public static SlotDef reference(String sharedFrom) {
            return new SlotDef(false, List.of(), List.of(), null, null, Map.of(), null, sharedFrom);
        }

        /**
         * 以本定义（宿主的完整声明）生成带 {@code sharedFrom} 标记的副本。
         */
        public SlotDef asSharedFrom(String owner) {
            return new SlotDef(required, allowedShapes, allowedRowLayouts, maxRows, maxPanelsPerColumn, label, description, owner);
        }

        /**
         * 共享区域的宿主页面名；{@code null} = 本页自有定义。
         */
        @Nullable
        public String getSharedFrom() {
            return sharedFrom;
        }

        /**
         * 是否为已解引用的共享区域（定义来自宿主页面）。
         */
        public boolean isShared() {
            return sharedFrom != null && !allowedShapes.isEmpty();
        }

        /**
         * 是否为无法解引用的共享引用（快照校验报错）。
         */
        public boolean isUnresolvedReference() {
            return sharedFrom != null && allowedShapes.isEmpty();
        }

        /**
         * “至少一个布局变体需要”。对应的空区域健康检查为提示性而非错误。
         */
        public boolean isRequired() {
            return required;
        }

        /**
         * 区域面板可选的组件（Shape）列表（每个须能在 Shape 注册表中解析）。
         */
        public List<String> getAllowedShapes() {
            return allowedShapes;
        }

        /**
         * 区域可用的行布局键（{@link RowLayoutCatalog} 的子集）。
         */
        public List<String> getAllowedRowLayouts() {
            return allowedRowLayouts;
        }

        /**
         * 区域行数上限（{@code null} 不限）。
         */
        @Nullable
        public Integer getMaxRows() {
            return maxRows;
        }

        /**
         * 区域单列面板数上限（{@code null} 不限）。
         */
        @Nullable
        public Integer getMaxPanelsPerColumn() {
            return maxPanelsPerColumn;
        }

        /**
         * 区域显示名（按 locale）；未声明为空映射。
         */
        public Map<String, String> getLabel() {
            return label;
        }

        /**
         * 区域描述；未声明为 {@code null}。
         */
        @Nullable
        public String getDescription() {
            return description;
        }
    }
}
