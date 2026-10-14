package com.ujcms.cms.core.composition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 区域解析结果（page-regions §7.1 / component-settings §4.2）。结构树（行/列）保留，各面板ID被替换为
 * 已解析的面板内容（组件、契约设置、条目）。渲染器据此按行布局铺栅格、按面板组件分派渲染。
 *
 * @param scope 命中的区域范围（visual-builder §9：{@code default} / {@code channel:<id>} /
 *              {@code variant:<surface>:<value>}；无区域行时为 {@code default}）——供
 *              验证脚本区分覆盖命中与默认回退
 * @author PONY
 */
public record ResolvedRegion(String region, String scope, boolean draft, Layout layout) {
    /**
     * 空区域（未声明区域 / 无区域行 / 无布局——渲染期永不破坏页面）。
     */
    public static ResolvedRegion empty(String region) {
        return new ResolvedRegion(region, com.ujcms.cms.core.domain.PageRegion.SCOPE_DEFAULT,
                false, new Layout(List.of()));
    }

    /**
     * 布局（行集合，镜像 §7.2 响应的 {@code layout} 字段）。
     */
    public record Layout(List<Row> rows) {
        public Layout {
            rows = List.copyOf(rows);
        }
    }

    /**
     * 行
     *
     * @param id      稳定行ID
     * @param layout  行布局键（{@link RowLayoutCatalog}）
     * @param columns 列
     */
    public record Row(String id, String layout, List<Column> columns) {
        public Row {
            columns = List.copyOf(columns);
        }
    }

    /**
     * 列
     *
     * @param panels 已解析面板
     */
    public record Column(List<Panel> panels) {
        public Column {
            panels = List.copyOf(panels);
        }
    }

    /**
     * 已解析面板 v2（component-settings §4.2）：组件实例 = 组件名 + 契约设置 + 条目。
     *
     * @param id         面板投放ID（字符串，JS 精度）
     * @param component  组件（Shape）名
     * @param version    组件版本（不可解析时为空串）
     * @param settings   契约设置值：缺省已补齐、引用已展开为摘要对象（栏目 {@code {id,alias,name,url}}、
     *                   文章 {@code {id,title,url,image}}），未设值为显式 {@code null}
     * @param items      条目（item 字段结构的平铺对象；查询/手工/置顶+填充统一形态）
     * @param enabled    面板是否启用
     * @param visibility 显示设备（design-review P2-5：{@code all}/{@code desktop}/{@code mobile}）——
     *                   缓存后过滤的元数据：模板轨按 UA（{@code Contexts.isMobile}）服务端跳过，
     *                   预览一律渲染
     *                   （构建器按视口预设经桥隐藏）
     */
    public record Panel(String id, String component, String version, Map<String, Object> settings,
                        List<Map<String, Object>> items, boolean enabled, String visibility) {
        public Panel {
            // settings 含显式 null 值（§4.2），不可用 Map.copyOf
            settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
            items = List.copyOf(items);
            visibility = com.ujcms.cms.core.domain.Placement.normalizeVisibility(visibility);
        }

        /**
         * 面板在给定设备类别是否显示（{@code all} 恒显示）。
         */
        public boolean visibleOn(boolean mobile) {
            return switch (visibility) {
                case com.ujcms.cms.core.domain.Placement.VISIBILITY_DESKTOP -> !mobile;
                case com.ujcms.cms.core.domain.Placement.VISIBILITY_MOBILE -> mobile;
                default -> true;
            };
        }
    }
}
