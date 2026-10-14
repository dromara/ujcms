package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.RegistryReload;
import com.ujcms.cms.core.mapper.RegistryReloadMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 兼容性分级在快照替换路径上的接线测试（§13.1）：无迁移块覆盖的破坏性移除使重载被拒、旧快照继续服务；
 * 带迁移块描述则放行。
 */
class ManifestServiceCompatTest {
    private final ShapeLoader shapeLoader = new ShapeLoader(new CompositionSchemas());

    private ManifestService service() {
        return new ManifestService(new SnapshotValidator(FieldTypeMatrixTest.registry()),
                new ShapeCompatibilityClassifier(), mock(RegistryReloadMapper.class),
                new SnowflakeSequence(0, 0), mock(ThemeDirScanner.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    private Shape shape(String json) {
        return shapeLoader.load(json, "theme:1/test", "demo.json");
    }

    private RegistrySnapshot snapshotOf(Shape shape) {
        return RegistrySnapshot.builder().addThemeShape("1/test", shape, "{}", "demo.json").build();
    }

    @Test
    void rejectsBreakingRemovalReloadAndKeepsOldSnapshot() {
        ManifestService service = service();
        RegistrySnapshot v1 = snapshotOf(shape("""
                { "name": "Demo", "version": "1.0.0",
                  "item": { "a": { "type": "text" }, "b": { "type": "text" } } }"""));
        service.applySnapshot(v1, RegistryReload.SOURCE_BOOT, null);
        assertEquals(v1.getContentHash(), service.getSnapshot().getContentHash());

        RegistrySnapshot v2 = snapshotOf(shape("""
                { "name": "Demo", "version": "2.0.0", "item": { "a": { "type": "text" } } }"""));
        assertThrows(CompositionLoadException.class,
                () -> service.applySnapshot(v2, RegistryReload.SOURCE_WATCHER, null));
        // 旧快照继续服务
        assertEquals(v1.getContentHash(), service.getSnapshot().getContentHash());
    }

    @Test
    void allowsBreakingRemovalWhenDescribedByMigration() {
        ManifestService service = service();
        service.applySnapshot(snapshotOf(shape("""
                { "name": "Demo", "version": "1.0.0",
                  "item": { "a": { "type": "text" }, "b": { "type": "text" } } }""")),
                RegistryReload.SOURCE_BOOT, null);
        RegistrySnapshot v2 = snapshotOf(shape("""
                { "name": "Demo", "version": "2.0.0", "item": { "a": { "type": "text" } },
                  "migrations": [ { "from": "1.x", "remove": ["b"] } ] }"""));
        service.applySnapshot(v2, RegistryReload.SOURCE_WATCHER, null);
        assertEquals(v2.getContentHash(), service.getSnapshot().getContentHash());
    }
}
