package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行布局目录（page-regions §6）。一个<b>固定的、代码内置的枚举</b>——这些是通用栅格模式，并非每主题可变，
 * 故是后端的唯一事实来源，经 {@code GET /api/backend/core/page-region/row-layouts} 暴露给编辑器；
 * React 侧 {@code rowLayouts} 注册表以相同键命名，并由 CI 校验为本目录的超集。
 *
 * <p>每种布局声明列数与各列权重（{@code widths}）。所有布局在 {@code md} 断点以下塌缩为单列
 * （移动端按树序自动堆叠：行从上到下、列从左到右）。具体的栅格 class（Tailwind）是 React 渲染器的关注点
 * （Phase 2），不在后端目录内。
 *
 * <p>未来（§14 Q3）：行布局可像布局变体目录那样移入 SSR 构建制品，由主题扩展；v1 保持固定全局枚举。
 *
 * @author PONY
 */
public final class RowLayoutCatalog {
    /**
     * 行布局键：整行（1 列）
     */
    public static final String FULL = "full";
    /**
     * 行布局键：两等分（1/2 + 1/2）
     */
    public static final String HALVES = "halves";
    /**
     * 行布局键：三等分（1/3 × 3）
     */
    public static final String THIRDS = "thirds";
    /**
     * 行布局键：左宽（2/3 + 1/3）
     */
    public static final String TWO_THIRDS_LEFT = "twoThirdsLeft";
    /**
     * 行布局键：右宽（1/3 + 2/3）
     */
    public static final String TWO_THIRDS_RIGHT = "twoThirdsRight";
    /**
     * 行布局键：四等分（1/4 × 4）
     */
    public static final String QUARTERS = "quarters";
    /**
     * 行布局键：左侧栏（1/4 + 3/4）
     */
    public static final String SIDEBAR_LEFT = "sidebarLeft";
    /**
     * 行布局键：右侧栏（3/4 + 1/4）
     */
    public static final String SIDEBAR_RIGHT = "sidebarRight";

    private static final Map<String, Layout> LAYOUTS = buildLayouts();

    private RowLayoutCatalog() {
    }

    private static Map<String, Layout> buildLayouts() {
        Map<String, Layout> map = new LinkedHashMap<>();
        put(map, FULL, List.of(1), "整行");
        put(map, HALVES, List.of(1, 1), "两等分");
        put(map, THIRDS, List.of(1, 1, 1), "三等分");
        put(map, TWO_THIRDS_LEFT, List.of(2, 1), "左宽 2:1");
        put(map, TWO_THIRDS_RIGHT, List.of(1, 2), "右宽 1:2");
        put(map, QUARTERS, List.of(1, 1, 1, 1), "四等分");
        put(map, SIDEBAR_LEFT, List.of(1, 3), "左侧栏 1:3");
        put(map, SIDEBAR_RIGHT, List.of(3, 1), "右侧栏 3:1");
        return Collections.unmodifiableMap(map);
    }

    private static void put(Map<String, Layout> map, String key, List<Integer> widths, String labelZh) {
        map.put(key, new Layout(key, widths.size(), widths, Map.of("zh-CN", labelZh)));
    }

    /**
     * 全部行布局键（声明顺序）。
     */
    public static List<String> keys() {
        return List.copyOf(LAYOUTS.keySet());
    }

    /**
     * 全部行布局（声明顺序，供编辑器目录端点）。
     */
    public static List<Layout> all() {
        return List.copyOf(LAYOUTS.values());
    }

    /**
     * 是否为已知行布局键。
     */
    public static boolean contains(@Nullable String key) {
        return key != null && LAYOUTS.containsKey(key);
    }

    /**
     * 行布局（{@code null} 表示未知键）。
     */
    @Nullable
    public static Layout get(@Nullable String key) {
        return key != null ? LAYOUTS.get(key) : null;
    }

    /**
     * 某行布局的列数；未知键返回 {@code -1}。
     */
    public static int columnCount(@Nullable String key) {
        Layout layout = get(key);
        return layout != null ? layout.columns() : -1;
    }

    /**
     * 单个行布局声明项。
     *
     * @param key     布局键
     * @param columns 列数
     * @param widths  各列权重（长度等于 {@code columns}）
     * @param label   本地化展示名
     */
    public record Layout(String key, int columns, List<Integer> widths, Map<String, String> label) {
        public Layout {
            widths = List.copyOf(widths);
            label = Map.copyOf(label);
        }
    }
}
