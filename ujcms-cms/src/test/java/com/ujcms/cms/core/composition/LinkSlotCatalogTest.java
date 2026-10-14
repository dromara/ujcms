package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 链接组展示位词汇表解析（link-groups D7）
 *
 * @author PONY
 */
class LinkSlotCatalogTest {

    @Test
    void parseKeepsDeclarationOrderAndLabels() {
        LinkSlotCatalog catalog = LinkSlotCatalog.parse(List.of(
                Map.of("name", "footer", "label", Map.of("zh-CN", "页脚链接栏", "en", "Footer Links")),
                Map.of("name", "friend-band")));
        assertEquals(2, catalog.getSlots().size());
        assertEquals("footer", catalog.getSlots().get(0).name());
        assertEquals("页脚链接栏", catalog.getSlots().get(0).label().get("zh-CN"));
        assertEquals("friend-band", catalog.getSlots().get(1).name());
        assertTrue(catalog.getSlots().get(1).label().isEmpty());
    }

    /** 非数组 / 空数组 / 无名项 → 空词汇表（清单未声明与声明为空同形） */
    @Test
    void parseToleratesMissingOrMalformedNode() {
        assertTrue(LinkSlotCatalog.parse(null).isEmpty());
        assertTrue(LinkSlotCatalog.parse(Map.of("name", "x")).isEmpty());
        assertTrue(LinkSlotCatalog.parse(List.of()).isEmpty());
        assertTrue(LinkSlotCatalog.parse(List.of(Map.of("label", Map.of("zh-CN", "无名")))).isEmpty());
    }
}
