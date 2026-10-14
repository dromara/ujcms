package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.core.JacksonException;
import com.ujcms.cms.core.composition.PageManifest.SlotDef;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 页面区域结构树（page-regions §5）。纯结构：rows → columns → 面板投放ID。面板内容是投放（Panel = Placement），
 * 由树以投放ID引用。{@link #validate} 据 {@link RowLayoutCatalog} 与区域 {@link SlotDef} 校验跨字段规则
 * （列数等于行布局列数、行数/单列面板数上限、面板ID属于本区域）；结构 well-formed 由
 * {@code page-region-layout.schema.json} 在装载/保存前保证。
 *
 * @author PONY
 */
public record RegionLayout(List<Row> rows) {
    /**
     * 空树（无行）
     */
    public static final RegionLayout EMPTY = new RegionLayout(List.of());

    public RegionLayout {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    /**
     * 行
     *
     * @param id      客户端生成的稳定行ID
     * @param layout  行布局键（{@link RowLayoutCatalog}）
     * @param columns 列（数量须等于行布局列数）
     */
    public record Row(String id, String layout, List<Column> columns) {
        public Row {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    /**
     * 列
     *
     * @param panels 有序面板投放ID
     */
    public record Column(List<String> panels) {
        public Column {
            panels = panels == null ? List.of() : List.copyOf(panels);
        }
    }

    /**
     * 解析布局树 JSON（{@code null}/空白 → 空树）。结构非法抛 {@link IllegalArgumentException}。
     */
    public static RegionLayout parse(@Nullable String json) {
        if (StringUtils.isBlank(json)) {
            return EMPTY;
        }
        try {
            RegionLayout layout = MAPPER.readValue(json, RegionLayout.class);
            return layout != null ? layout : EMPTY;
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid region layout json", e);
        }
    }

    /**
     * 序列化为 JSON。
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize region layout", e);
        }
    }

    /**
     * 全部面板投放ID（按树序：行从上到下、列从左到右、列内自上而下）。
     */
    public List<String> panelIds() {
        List<String> ids = new ArrayList<>();
        for (Row row : rows) {
            for (Column column : row.columns()) {
                ids.addAll(column.panels());
            }
        }
        return ids;
    }

    /**
     * 据行布局目录与区域定义校验跨字段规则。
     *
     * @param regionSlotDef 区域 SlotDef（取 allowedRowLayouts / maxRows / maxPanelsPerColumn）
     * @param panelIdsInRegion 本区域已知面板投放ID集合（树引用须为其子集）；{@code null} 跳过该项校验
     * @return 错误列表，空列表表示通过
     */
    public List<String> validate(SlotDef regionSlotDef, @Nullable Set<String> panelIdsInRegion) {
        List<String> errors = new ArrayList<>();
        Integer maxRows = regionSlotDef.getMaxRows();
        if (maxRows != null && rows.size() > maxRows) {
            errors.add(String.format("行数 %d 超过上限 %d", rows.size(), maxRows));
        }
        List<String> allowedRowLayouts = regionSlotDef.getAllowedRowLayouts();
        Integer maxPanels = regionSlotDef.getMaxPanelsPerColumn();
        Set<String> seenRowIds = new LinkedHashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            String where = "行[" + i + "]";
            if (StringUtils.isBlank(row.id())) {
                errors.add(where + ": 缺少行ID");
            } else if (!seenRowIds.add(row.id())) {
                errors.add(where + ": 行ID重复: " + row.id());
            }
            if (!RowLayoutCatalog.contains(row.layout())) {
                errors.add(where + ": 未知行布局: " + row.layout());
                continue;
            }
            if (!allowedRowLayouts.isEmpty() && !allowedRowLayouts.contains(row.layout())) {
                errors.add(where + ": 行布局不被区域允许: " + row.layout());
            }
            int expectedColumns = RowLayoutCatalog.columnCount(row.layout());
            if (row.columns().size() != expectedColumns) {
                errors.add(String.format("%s: 行布局 %s 需 %d 列，实际 %d 列",
                        where, row.layout(), expectedColumns, row.columns().size()));
            }
            for (int c = 0; c < row.columns().size(); c++) {
                Column column = row.columns().get(c);
                if (maxPanels != null && column.panels().size() > maxPanels) {
                    errors.add(String.format("%s 列[%d]: 面板数 %d 超过单列上限 %d",
                            where, c, column.panels().size(), maxPanels));
                }
                for (String panelId : column.panels()) {
                    if (StringUtils.isBlank(panelId)) {
                        errors.add(where + " 列[" + c + "]: 面板ID为空");
                    } else if (panelIdsInRegion != null && !panelIdsInRegion.contains(panelId)) {
                        errors.add(where + " 列[" + c + "]: 面板ID不属于本区域: " + panelId);
                    }
                }
            }
        }
        return errors;
    }
}
