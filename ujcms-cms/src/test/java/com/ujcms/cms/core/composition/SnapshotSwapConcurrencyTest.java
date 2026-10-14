package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.mapper.RegistryReloadMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 并发读下的快照原子替换测试：读取方永远看到完整一致的快照；
 * 校验失败的候选快照绝不可见（validate-before-swap）。
 */
class SnapshotSwapConcurrencyTest {
    private static final String THEME = "1/test";
    private final ShapeLoader loader = new ShapeLoader(new CompositionSchemas());

    private static Map<String, Shape> shapes(RegistrySnapshot snapshot) {
        return snapshot.getThemeShapes().getOrDefault(THEME, Map.of());
    }

    private Shape shape(String name, String fieldType) {
        return loader.load("""
                { "name": "%s", "version": "1.0.0", "item": { "a": { "type": "%s" } } }
                """.formatted(name, fieldType), "theme:1/test", name + ".json");
    }

    /**
     * 每个合法快照包含 generation 个 Shape（Gen 一致性不变量）：
     * 读取方任何时刻看到的 Shape 数必须等于其所有成员名中编码的代数。
     */
    private RegistrySnapshot validSnapshot(int generation) {
        RegistrySnapshot.Builder builder = RegistrySnapshot.builder();
        for (int i = 0; i < generation; i++) {
            builder.addThemeShape(THEME, shape("Gen" + generation + "Item" + i, "text"), "{}", "g.json");
        }
        return builder.build();
    }

    private RegistrySnapshot invalidSnapshot() {
        return RegistrySnapshot.builder()
                .addThemeShape(THEME, shape("Broken", "hologram"), "{}", "broken.json")
                .build();
    }

    @Test
    void readersAlwaysSeeConsistentSnapshotsAndInvalidOnesNeverBecomeVisible() throws Exception {
        ManifestService service = new ManifestService(
                new SnapshotValidator(FieldTypeMatrixTest.registry()), new ShapeCompatibilityClassifier(),
                mock(RegistryReloadMapper.class), new SnowflakeSequence(0, 0), mock(ThemeDirScanner.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        int generations = 50;
        int readers = 4;
        AtomicBoolean running = new AtomicBoolean(true);
        ConcurrentLinkedQueue<String> violations = new ConcurrentLinkedQueue<>();
        CountDownLatch started = new CountDownLatch(readers);

        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int r = 0; r < readers; r++) {
            futures.add(pool.submit(() -> {
                started.countDown();
                while (running.get()) {
                    RegistrySnapshot snapshot = service.getSnapshot();
                    int size = shapes(snapshot).size();
                    if (size == 0) {
                        continue;
                    }
                    // 一致性不变量：所有 Shape 名编码同一代数，且数量与代数一致
                    String first = shapes(snapshot).keySet().iterator().next();
                    String generation = first.substring(3, first.indexOf("Item"));
                    if (size != Integer.parseInt(generation)) {
                        violations.add("size=" + size + " generation=" + generation);
                    }
                    if (shapes(snapshot).containsKey("Broken")) {
                        violations.add("invalid snapshot became visible");
                    }
                }
            }));
        }

        started.await(5, TimeUnit.SECONDS);
        for (int generation = 1; generation <= generations; generation++) {
            service.applySnapshot(validSnapshot(generation), "test", null);
            if (generation % 10 == 0) {
                RegistrySnapshot broken = invalidSnapshot();
                assertThrows(CompositionLoadException.class,
                        () -> service.applySnapshot(broken, "test", null));
            }
        }
        running.set(false);
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        for (var future : futures) {
            future.get();
        }

        assertEquals(List.of(), List.copyOf(violations));
        // 最终生效的是最后一个合法快照
        assertEquals(generations, shapes(service.getSnapshot()).size());
    }
}
