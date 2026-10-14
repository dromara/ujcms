package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 行布局目录测试（page-regions §6）：固定枚举的键、列数、各列权重，及与 Schema 枚举的一致性。
 */
class RowLayoutCatalogTest {
    @Test
    void catalogHasTheEightLayoutsInDeclaredOrder() {
        assertEquals(List.of("full", "halves", "thirds", "twoThirdsLeft", "twoThirdsRight",
                        "quarters", "sidebarLeft", "sidebarRight"),
                RowLayoutCatalog.keys());
        assertEquals(8, RowLayoutCatalog.all().size());
    }

    @Test
    void columnCountsAndWidthsMatchSpec() {
        assertEquals(1, RowLayoutCatalog.columnCount("full"));
        assertEquals(2, RowLayoutCatalog.columnCount("halves"));
        assertEquals(3, RowLayoutCatalog.columnCount("thirds"));
        assertEquals(2, RowLayoutCatalog.columnCount("twoThirdsLeft"));
        assertEquals(2, RowLayoutCatalog.columnCount("twoThirdsRight"));
        assertEquals(4, RowLayoutCatalog.columnCount("quarters"));
        assertEquals(2, RowLayoutCatalog.columnCount("sidebarLeft"));
        assertEquals(2, RowLayoutCatalog.columnCount("sidebarRight"));

        assertEquals(List.of(1), RowLayoutCatalog.get("full").widths());
        assertEquals(List.of(1, 1), RowLayoutCatalog.get("halves").widths());
        assertEquals(List.of(1, 1, 1), RowLayoutCatalog.get("thirds").widths());
        assertEquals(List.of(2, 1), RowLayoutCatalog.get("twoThirdsLeft").widths());
        assertEquals(List.of(1, 2), RowLayoutCatalog.get("twoThirdsRight").widths());
        assertEquals(List.of(1, 1, 1, 1), RowLayoutCatalog.get("quarters").widths());
        assertEquals(List.of(1, 3), RowLayoutCatalog.get("sidebarLeft").widths());
        assertEquals(List.of(3, 1), RowLayoutCatalog.get("sidebarRight").widths());
        // widths 长度恒等于列数
        for (RowLayoutCatalog.Layout layout : RowLayoutCatalog.all()) {
            assertEquals(layout.columns(), layout.widths().size(), layout.key());
        }
    }

    @Test
    void unknownKeyIsRejected() {
        assertFalse(RowLayoutCatalog.contains("quintuple"));
        assertFalse(RowLayoutCatalog.contains(null));
        assertNull(RowLayoutCatalog.get("quintuple"));
        assertEquals(-1, RowLayoutCatalog.columnCount("quintuple"));
        assertTrue(RowLayoutCatalog.contains("thirds"));
        assertTrue(RowLayoutCatalog.contains("quarters"));
    }

    /**
     * Schema 枚举与目录一致：区域布局树 Schema 的 row.layout 枚举、页面清单 Schema 的 rowLayout 枚举，
     * 均须与 {@link RowLayoutCatalog#keys()} 完全一致（目录扩展时四处镜像靠本测试兜底）。
     */
    @Test
    void schemaEnumsMatchCatalogKeys() throws IOException {
        assertEquals(RowLayoutCatalog.keys(),
                schemaEnum("/composition/schema/page-region-layout.schema.json", "$defs", "row", "properties", "layout"),
                "page-region-layout.schema.json row.layout enum");
        assertEquals(RowLayoutCatalog.keys(),
                schemaEnum("/composition/schema/page-manifest.schema.json", "$defs", "rowLayout"),
                "page-manifest.schema.json $defs.rowLayout enum");
    }

    private static List<String> schemaEnum(String resource, String... path) throws IOException {
        try (InputStream in = RowLayoutCatalogTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            JsonNode node = new ObjectMapper().readTree(in);
            for (String segment : path) {
                node = node.path(segment);
            }
            JsonNode enumNode = node.path("enum");
            assertTrue(enumNode.isArray(), resource + " enum not found");
            List<String> keys = new ArrayList<>(enumNode.size());
            enumNode.forEach(item -> keys.add(item.asText()));
            return keys;
        }
    }
}
