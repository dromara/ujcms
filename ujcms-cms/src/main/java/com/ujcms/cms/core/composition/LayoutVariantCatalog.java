package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 布局变体目录（§21 Q4；theme-marketplace R1 统一注册表）。声明各 surface（{@code channel}/{@code article}/
 * {@code topic}）下存在哪些布局变体及其展示元数据。变体对应的渲染是代码（主题的
 * {@code lib/layouts/registry.ts}，或主题包的模板文件），故目录是与代码强耦合、由开发者编写、随制品/主题包
 * 携带的数据。声明来源：主题清单的
 * {@code layoutVariants} 块（manifest 2.0，Phase A1）装入 {@link RegistrySnapshot#getThemeLayoutVariants}。
 * 读取按站点（活动主题声明），见 {@link com.ujcms.cms.core.service.LayoutVariantService}。
 *
 * <p>操作员的<b>每页选择</b>经模板列表达（{@code channel_template_}/{@code article_template_}/{@code topic_template_}），
 * 经 {@link com.ujcms.cms.core.service.LayoutVariantService#isValid} 据本目录校验——目录为空（纯模板站点 /
 * 结构与解析方式镜像 {@link ThemeSettingsSchema}。
 * 注意：这些列<b>仅 SSR 轨道</b>读取——模板轨道渲染器从不读它们，模板轨的变体即「生效模板的反查」
 * （见 docs/spec/template-track-variants/plan.md 与 docs/decisions/template-track-variants/01）。
 *
 * @author PONY
 */
public final class LayoutVariantCatalog {
    /**
     * 适用对象：栏目
     */
    public static final String SURFACE_CHANNEL = "channel";
    /**
     * 适用对象：文章
     */
    public static final String SURFACE_ARTICLE = "article";
    /**
     * 适用对象：专题
     */
    public static final String SURFACE_TOPIC = "topic";
    /**
     * 全部合法 surface
     */
    public static final List<String> SURFACES = List.of(SURFACE_CHANNEL, SURFACE_ARTICLE, SURFACE_TOPIC);

    /**
     * 空目录（清单无 {@code layoutVariants} 块）
     */
    public static final LayoutVariantCatalog EMPTY = new LayoutVariantCatalog(Map.of());

    /**
     * surface → 有序变体列表（含禁用项；按制品声明顺序）
     */
    private final Map<String, List<Item>> bySurface;

    private LayoutVariantCatalog(Map<String, List<Item>> bySurface) {
        Map<String, List<Item>> map = new LinkedHashMap<>(bySurface.size());
        bySurface.forEach((surface, items) -> map.put(surface, List.copyOf(items)));
        this.bySurface = Collections.unmodifiableMap(map);
    }

    /**
     * 解析 {@code layoutVariants} 块（{@code {surface: [{value,label,enabled,template,settings}, ...]}}，
     * {@code null}/缺失/非对象 → 空目录）。次序即数组顺序；{@code enabled} 缺省为 {@code true}。
     * 主题清单（manifest 2.0，§4.1）声明；{@code template}（变体绑定的未组装模板名）与
     * {@code settings}（变体作用域设置结构，§4.2 语法）。
     *
     * @param node {@code layoutVariants} 节点（已转为 {@code Map}）
     * @return 布局变体目录
     */
    public static LayoutVariantCatalog parse(@Nullable Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return EMPTY;
        }
        Map<String, List<Item>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String surface) || surface.isBlank()
                    || !(entry.getValue() instanceof List<?> list)) {
                continue;
            }
            List<Item> items = new ArrayList<>(list.size());
            int order = 0;
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> itemMap)) {
                    continue;
                }
                String value = itemMap.get("value") instanceof String s ? s : null;
                if (value == null || value.isBlank()) {
                    continue;
                }
                Map<String, String> label = ShapeLoader.toStringMap(itemMap.get("label"));
                boolean enabled = !(itemMap.get("enabled") instanceof Boolean b) || b;
                String template = itemMap.get("template") instanceof String t && !t.isBlank() ? t : null;
                ThemeSettingsSchema settings = ThemeSettingsSchema.parse(itemMap.get("settings"));
                items.add(new Item(value, label, order++, enabled, template, settings));
            }
            if (!items.isEmpty()) {
                result.put(surface, items);
            }
        }
        return result.isEmpty() ? EMPTY : new LayoutVariantCatalog(result);
    }

    /**
     * 某 surface 下的全部变体（含禁用，按声明顺序）。未知 surface → 空列表。
     */
    public List<Item> forSurface(String surface) {
        return bySurface.getOrDefault(surface, List.of());
    }

    public boolean isEmpty() {
        return bySurface.isEmpty();
    }

    public Map<String, List<Item>> getBySurface() {
        return bySurface;
    }

    /**
     * 单个布局变体声明项
     */
    public static final class Item {
        private final String value;
        private final Map<String, String> label;
        private final int order;
        private final boolean enabled;
        /**
         * 变体绑定的未组装模板名（§4.1，主题清单声明）
         */
        @Nullable
        private final String template;
        /**
         * 变体作用域设置结构（§4.2 语法，存值键 {@code variants.<surface>:<value>}；未声明 → 空结构）
         */
        private final ThemeSettingsSchema settings;

        Item(String value, Map<String, String> label, int order, boolean enabled,
             @Nullable String template, ThemeSettingsSchema settings) {
            this.value = value;
            this.label = Collections.unmodifiableMap(new LinkedHashMap<>(label));
            this.order = order;
            this.enabled = enabled;
            this.template = template;
            this.settings = settings;
        }

        public String getValue() {
            return value;
        }

        public Map<String, String> getLabel() {
            return label;
        }

        public int getOrder() {
            return order;
        }

        public boolean isEnabled() {
            return enabled;
        }

        @Nullable
        public String getTemplate() {
            return template;
        }

        public ThemeSettingsSchema getSettings() {
            return settings;
        }

        /**
         * 扁平展示名：优先 {@code zh-CN}，否则首个非空，再否则回退到 {@code value}。
         * （旧 {@code ujcms_layout_variant.label_} 为单一中文串，控制台据此渲染。）
         */
        public String displayLabel() {
            String zh = label.get("zh-CN");
            if (zh != null && !zh.isBlank()) {
                return zh;
            }
            for (String v : label.values()) {
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
            return value;
        }
    }
}
