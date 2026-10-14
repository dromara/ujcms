package com.ujcms.cms.core.composition;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link PanelResolveCache} 单元测试：TTL 配置生效（composition-invalidation 计划 §3，
 * 默认 60 秒）、面板缓存命中不再加载、作废后重载（component-settings Phase D 起仅面板口径，
 * 经典插槽缓存已随插槽子系统退役）。
 *
 * @author PONY
 */
class PanelResolveCacheTest {

    @Test
    void ttlIsConfigurable() {
        PanelResolveCache cache = new PanelResolveCache(new SimpleMeterRegistry(), 90);
        assertEquals(90, cache.getTtlSeconds());
    }

    @Test
    void panelHitsSkipLoaderAndInvalidateAllReloads() {
        PanelResolveCache cache = new PanelResolveCache(new SimpleMeterRegistry(), 60);
        // 面板键的插槽名分量带 #panel:<id> 后缀（§19.1）
        String key = PanelResolveCache.key(1L, "home", "focus#panel:9001", "1/default", "", "hash");
        AtomicInteger loads = new AtomicInteger();
        ResolvedRegion.Panel panel = new ResolvedRegion.Panel("9001", "RichHtml", "1.0.0",
                Map.of(), List.of(), true, "all");

        assertSame(panel, cache.getPanel(key, () -> {
            loads.incrementAndGet();
            return panel;
        }));
        assertSame(panel, cache.getPanel(key, () -> {
            loads.incrementAndGet();
            return panel;
        }));
        assertEquals(1, loads.get());

        cache.invalidateAll();
        cache.getPanel(key, () -> {
            loads.incrementAndGet();
            return panel;
        });
        assertEquals(2, loads.get());
    }
}
