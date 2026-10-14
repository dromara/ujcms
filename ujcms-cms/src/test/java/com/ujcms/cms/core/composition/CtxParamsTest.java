package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * $ctx 白名单过滤与缓存键 ctxKey 测试（§14.2 / §16.1 / §19.1）。
 */
class CtxParamsTest {
    @Test
    void sanitizeKeepsOnlyWhitelistedNonBlankKeys() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("channel", "news");
        raw.put("channelId", 100L);
        raw.put("siteId", 1L);
        raw.put("evil", "DROP TABLE");   // 非白名单 → 丢弃
        raw.put("channelAlias", "x");    // 非白名单键名 → 丢弃
        raw.put("channel2", "  ");       // 非白名单 → 丢弃

        Map<String, Object> clean = CtxParams.sanitize(raw);

        assertEquals(3, clean.size());
        assertEquals("news", clean.get("channel"));
        assertEquals(100L, clean.get("channelId"));
        assertEquals(1L, clean.get("siteId"));
        assertFalse(clean.containsKey("evil"));
    }

    @Test
    void sanitizeDropsBlankStringValues() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("channel", "   ");
        Map<String, Object> clean = CtxParams.sanitize(raw);
        assertTrue(clean.isEmpty());
    }

    @Test
    void allowedKeysAreExactlyChannelChannelIdSiteId() {
        assertTrue(CtxParams.isAllowed("channel"));
        assertTrue(CtxParams.isAllowed("channelId"));
        assertTrue(CtxParams.isAllowed("siteId"));
        assertFalse(CtxParams.isAllowed("article"));
        assertFalse(CtxParams.isAllowed("userId"));
    }

    @Test
    void ctxKeyIsSortedStableSerialization() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("siteId", 1L);
        params.put("channel", "news");
        ResolveContext a = ResolveContext.template(THEME, params);

        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("channel", "news");
        reordered.put("siteId", 1L);
        ResolveContext b = ResolveContext.template(THEME, reordered);

        // 键排序 → 与插入顺序无关，按频道而异的解析不会因键序不同而缓存击穿
        assertEquals(a.ctxKey(), b.ctxKey());
        assertEquals("channel=news&siteId=1", a.ctxKey());
        assertEquals("", ResolveContext.template(THEME).ctxKey());
    }

    private static final String THEME = "1/liquid-default";
}
