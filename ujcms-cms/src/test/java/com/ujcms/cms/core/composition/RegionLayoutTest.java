package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.PageManifest.SlotDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 区域布局树测试（page-regions §5）：解析 / 序列化往返、面板ID展开顺序，以及跨字段校验
 * （列数与行布局一致、行数 / 单列面板数上限、未知/不被允许的布局、面板归属、重复行ID）。
 */
class RegionLayoutTest {
    private static SlotDef region(List<String> allowedRowLayouts, Integer maxRows, Integer maxPanelsPerColumn) {
        return new SlotDef(false, List.of("RichHtml"), allowedRowLayouts, maxRows, maxPanelsPerColumn);
    }

    @Test
    void parseAndPanelIdsInTreeOrder() {
        String json = """
                { "rows": [
                  { "id": "r1", "layout": "full", "columns": [ { "panels": ["10"] } ] },
                  { "id": "r2", "layout": "halves",
                    "columns": [ { "panels": ["20", "50"] }, { "panels": ["30"] } ] }
                ] }
                """;
        RegionLayout layout = RegionLayout.parse(json);
        assertEquals(2, layout.rows().size());
        assertEquals(List.of("10", "20", "50", "30"), layout.panelIds());
        // 往返序列化后仍可解析、面板序不变
        assertEquals(List.of("10", "20", "50", "30"), RegionLayout.parse(layout.toJson()).panelIds());
    }

    @Test
    void blankParsesToEmpty() {
        assertTrue(RegionLayout.parse(null).rows().isEmpty());
        assertTrue(RegionLayout.parse("  ").rows().isEmpty());
        assertEquals(0, RegionLayout.EMPTY.panelIds().size());
    }

    @Test
    void validateAcceptsWellFormedTree() {
        RegionLayout layout = RegionLayout.parse("""
                { "rows": [ { "id": "r1", "layout": "halves",
                  "columns": [ { "panels": ["10"] }, { "panels": ["20"] } ] } ] }
                """);
        assertTrue(layout.validate(region(List.of("full", "halves"), 5, 6), Set.of("10", "20")).isEmpty());
    }

    @Test
    void validateRejectsColumnCountMismatch() {
        // halves 需 2 列，只给 1 列
        RegionLayout layout = RegionLayout.parse("""
                { "rows": [ { "id": "r1", "layout": "halves", "columns": [ { "panels": [] } ] } ] }
                """);
        List<String> errors = layout.validate(region(List.of(), null, null), null);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("需 2 列")), errors.toString());
    }

    @Test
    void validateRejectsUnknownAndDisallowedLayout() {
        assertFalse(RegionLayout.parse("""
                { "rows": [ { "id": "r1", "layout": "quintuple", "columns": [ { "panels": [] } ] } ] }
                """).validate(region(List.of(), null, null), null).isEmpty());
        // thirds 是已知布局，但区域只允许 full/halves
        List<String> errors = RegionLayout.parse("""
                { "rows": [ { "id": "r1", "layout": "thirds",
                  "columns": [ { "panels": [] }, { "panels": [] }, { "panels": [] } ] } ] }
                """).validate(region(List.of("full", "halves"), null, null), null);
        assertTrue(errors.stream().anyMatch(e -> e.contains("不被区域允许")), errors.toString());
    }

    @Test
    void validateEnforcesMaxRowsAndMaxPanelsPerColumn() {
        RegionLayout tooManyRows = RegionLayout.parse("""
                { "rows": [
                  { "id": "r1", "layout": "full", "columns": [ { "panels": [] } ] },
                  { "id": "r2", "layout": "full", "columns": [ { "panels": [] } ] }
                ] }
                """);
        assertTrue(tooManyRows.validate(region(List.of(), 1, null), null).stream()
                .anyMatch(e -> e.contains("行数")));

        RegionLayout tooManyPanels = RegionLayout.parse("""
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["1","2","3"] } ] } ] }
                """);
        assertTrue(tooManyPanels.validate(region(List.of(), null, 2), Set.of("1", "2", "3")).stream()
                .anyMatch(e -> e.contains("单列上限")));
    }

    @Test
    void validateRejectsForeignPanelAndDuplicateRowId() {
        List<String> foreign = RegionLayout.parse("""
                { "rows": [ { "id": "r1", "layout": "full", "columns": [ { "panels": ["99"] } ] } ] }
                """).validate(region(List.of(), null, null), Set.of("10"));
        assertTrue(foreign.stream().anyMatch(e -> e.contains("不属于本区域")), foreign.toString());

        List<String> dupRow = RegionLayout.parse("""
                { "rows": [
                  { "id": "r1", "layout": "full", "columns": [ { "panels": [] } ] },
                  { "id": "r1", "layout": "full", "columns": [ { "panels": [] } ] }
                ] }
                """).validate(region(List.of(), null, null), null);
        assertTrue(dupRow.stream().anyMatch(e -> e.contains("行ID重复")), dupRow.toString());
    }
}
