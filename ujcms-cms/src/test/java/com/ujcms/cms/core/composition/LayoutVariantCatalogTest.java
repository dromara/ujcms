package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 布局变体目录解析测试（§21 Q4）：缺失/畸形 → 空目录；声明顺序即 order；缺 value 跳过；
 * enabled 缺省 true；displayLabel 优先 zh-CN 再回退。
 */
class LayoutVariantCatalogTest {

    @Test
    void nullOrNonMapYieldsEmpty() {
        assertSame(LayoutVariantCatalog.EMPTY, LayoutVariantCatalog.parse(null));
        assertSame(LayoutVariantCatalog.EMPTY, LayoutVariantCatalog.parse("not a map"));
        assertSame(LayoutVariantCatalog.EMPTY, LayoutVariantCatalog.parse(List.of()));
        // 全部条目无 value → 仍为空
        assertTrue(LayoutVariantCatalog.parse(Map.of("channel", List.of(Map.of("label", Map.of())))).isEmpty());
    }

    @Test
    void parsesOrderEnabledAndLabel() {
        LayoutVariantCatalog catalog = LayoutVariantCatalog.parse(Map.of(
                "channel", List.of(
                        Map.of("value", "default", "label", Map.of("zh-CN", "默认栏目页", "en", "Default")),
                        Map.of("value", "channel_about", "label", Map.of("en", "About")),
                        Map.of("value", "channel_off", "enabled", false),
                        Map.of("label", Map.of("zh-CN", "无 value 应跳过")))));
        List<LayoutVariantCatalog.Item> channel = catalog.forSurface("channel");
        assertEquals(3, channel.size(), "缺 value 的条目应被跳过");
        assertEquals(0, channel.get(0).getOrder());
        assertEquals(1, channel.get(1).getOrder());
        assertEquals("默认栏目页", channel.get(0).displayLabel());
        // 无 zh-CN → 回退首个非空 label
        assertEquals("About", channel.get(1).displayLabel());
        // 无 label → 回退 value
        assertEquals("channel_off", channel.get(2).displayLabel());
        assertTrue(channel.get(0).isEnabled());
        assertFalse(channel.get(2).isEnabled());
        // 未知 surface → 空列表
        assertTrue(catalog.forSurface("topic").isEmpty());
    }
}
