package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 快照差异摘要测试：基线缺失、无差异、增删改归类（组件/页面/主题级声明）、列表截断。
 */
class SnapshotDiffTest {
    private final ShapeLoader shapeLoader = new ShapeLoader(new CompositionSchemas());
    private final ManifestLoader manifestLoader = new ManifestLoader(new CompositionSchemas());
    private final ObjectMapper mapper = new ObjectMapper();

    private Shape shape(String name) {
        return shapeLoader.load("""
                { "name": "%s", "version": "1.0.0",
                  "item": { "title": { "type": "text" } } }
                """.formatted(name), "theme:1/test", name + ".json");
    }

    private PageManifest homeManifest() {
        return manifestLoader.load("""
                { "name": "home", "template": "index",
                  "slots": { "promo": { "kind": "region", "allowedShapes": ["Banner"] } } }
                """, "home.manifest.json");
    }

    private Map<String, Object> parse(String json) throws Exception {
        return mapper.readValue(json, new TypeReference<>() {
        });
    }

    @Test
    void emptyBaselineYieldsNull() {
        RegistrySnapshot next = RegistrySnapshot.builder()
                .addThemeShape("1/default", shape("Banner"), "v1", "banner.json").build();
        assertNull(SnapshotDiff.summarize(RegistrySnapshot.empty(), next));
    }

    @Test
    void identicalContentYieldsEmptyDiff() {
        RegistrySnapshot old = RegistrySnapshot.builder()
                .addThemeShape("1/default", shape("Banner"), "v1", "banner.json").build();
        RegistrySnapshot next = RegistrySnapshot.builder()
                .addThemeShape("1/default", shape("Banner"), "v1", "banner.json").build();
        assertEquals("{}", SnapshotDiff.summarize(old, next));
    }

    @Test
    void categorizesAddedRemovedChanged() throws Exception {
        RegistrySnapshot old = RegistrySnapshot.builder()
                .addThemeShape("1/default", shape("Banner"), "v1", "banner.json")
                .addThemeShape("1/default", shape("Hero"), "v1", "hero.json")
                .build();
        RegistrySnapshot next = RegistrySnapshot.builder()
                // Banner 内容变更、Hero 移除、Nav 新增；页面与主题设置新增
                .addThemeShape("1/default", shape("Banner"), "v2", "banner.json")
                .addThemeShape("1/default", shape("Nav"), "v1", "nav.json")
                .addThemeManifest("1/default", homeManifest(), "v1", "home.manifest.json")
                .addThemeSettings("1/default",
                        ThemeSettingsSchema.parse(Map.of("groups", List.of(Map.of("name", "palette")))), "v1")
                .build();
        Map<String, Object> diff = parse(SnapshotDiff.summarize(old, next));
        assertEquals(Map.of("added", List.of("Nav"), "removed", List.of("Hero"), "changed", List.of("Banner")),
                diff.get("shapes"));
        assertEquals(Map.of("added", List.of("home")), diff.get("pages"));
        assertEquals(Map.of("added", List.of("1/default")), diff.get("themes"));
        assertNull(diff.get("truncated"));
    }

    @Test
    void truncatesOversizedLists() throws Exception {
        RegistrySnapshot.Builder oldBuilder = RegistrySnapshot.builder();
        RegistrySnapshot.Builder nextBuilder = RegistrySnapshot.builder();
        for (int i = 1; i <= 25; i++) {
            String name = "Shape" + i;
            oldBuilder.addThemeShape("1/default", shape(name), "v1", name + ".json");
            nextBuilder.addThemeShape("1/default", shape(name), "v2", name + ".json");
        }
        Map<String, Object> diff = parse(SnapshotDiff.summarize(oldBuilder.build(), nextBuilder.build()));
        Map<?, ?> shapes = (Map<?, ?>) diff.get("shapes");
        assertEquals(20, ((List<?>) shapes.get("changed")).size());
        assertTrue((Boolean) diff.get("truncated"));
    }
}
