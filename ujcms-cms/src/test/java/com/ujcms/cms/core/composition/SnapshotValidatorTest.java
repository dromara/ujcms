package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 快照校验测试：模板重复绑定、Shape 引用不可解析、未知字段类型等拒绝场景。
 */
class SnapshotValidatorTest {
    private final ShapeLoader shapeLoader = new ShapeLoader(new CompositionSchemas());
    private final ManifestLoader manifestLoader = new ManifestLoader(new CompositionSchemas());
    private final SnapshotValidator validator = new SnapshotValidator(FieldTypeMatrixTest.registry());

    private Shape bannerShape() {
        return shapeLoader.load("""
                { "name": "Banner", "version": "1.0.0",
                  "item": { "title": { "type": "text", "required": true } } }
                """, "theme:1/test", "banner.json");
    }

    private PageManifest manifest(String json, String desc) {
        return manifestLoader.load(json, desc);
    }

    @Test
    void validSnapshotPasses() {
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", bannerShape(), "{}", "banner.json")
                .addThemeManifest("default", manifest("""
                        { "name": "home", "template": "index",
                          "slots": { "promo": { "kind": "region", "allowedShapes": ["Banner"] } } }
                        """, "home.manifest.json"), "{}", "home.manifest.json")
                .build();
        assertEquals(List.of(), validator.validate(snapshot));
    }

    @Test
    void rejectsDuplicateTemplateBindingWithinTheme() {
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", bannerShape(), "{}", "banner.json")
                .addThemeManifest("default", manifest("""
                        { "name": "home", "template": ["index", "index_special"],
                          "slots": { "promo": { "kind": "region", "allowedShapes": ["Banner"] } } }
                        """, "home.manifest.json"), "{}", "home.manifest.json")
                .addThemeManifest("default", manifest("""
                        { "name": "landing", "template": "index",
                          "slots": { "promo": { "kind": "region", "allowedShapes": ["Banner"] } } }
                        """, "landing.manifest.json"), "{}", "landing.manifest.json")
                .build();
        List<String> errors = validator.validate(snapshot);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("index"), errors.toString());
        assertTrue(errors.get(0).contains("被多个页面清单绑定"), errors.toString());
    }

    @Test
    void allowsSameTemplateBindingAcrossDifferentThemes() {
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("theme-a", bannerShape(), "{}", "banner.json")
                .addThemeShape("theme-b", bannerShape(), "{}", "banner.json")
                .addThemeManifest("theme-a", manifest("""
                        { "name": "home", "template": "index",
                          "slots": { "promo": { "kind": "region", "allowedShapes": ["Banner"] } } }
                        """, "a/home.manifest.json"), "{}", "a/home.manifest.json")
                .addThemeManifest("theme-b", manifest("""
                        { "name": "home", "template": "index",
                          "slots": { "promo": { "kind": "region", "allowedShapes": ["Banner"] } } }
                        """, "b/home.manifest.json"), "{}", "b/home.manifest.json")
                .build();
        assertEquals(List.of(), validator.validate(snapshot));
    }

    @Test
    void themeShapeResolvesSlot() {
        Shape themeBanner = shapeLoader.load("""
                { "name": "ThemeBanner", "version": "1.0.0",
                  "item": { "title": { "type": "text" } } }
                """, Shape.SOURCE_THEME_PREFIX + "default", "theme-banner.json");
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", themeBanner, "{}", "theme-banner.json")
                .addThemeManifest("default", manifest("""
                        { "name": "home", "slots": { "promo": { "kind": "region", "allowedShapes": ["ThemeBanner"] } } }
                        """, "home.manifest.json"), "{}", "home.manifest.json")
                .build();
        assertEquals(List.of(), validator.validate(snapshot));
    }

    @Test
    void rejectsUnknownFieldTypeInShape() {
        // Schema 不限定 type 取值（扩展类型合法），未注册类型由快照校验兜底
        Shape shape = shapeLoader.load("""
                { "name": "Demo", "version": "1.0.0",
                  "item": { "weird": { "type": "hologram" } } }
                """, "theme:1/test", "demo.json");
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", shape, "{}", "demo.json").build();
        List<String> errors = validator.validate(snapshot);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("hologram"), errors.toString());
    }

    @Test
    void manifestSchemaRejectsRetiredSectionFields() {
        // sectionFields 已随经典插槽于 Phase D 退役：Schema additionalProperties=false 直接拒绝
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "home", "slots": { "promo": { "kind": "region", "allowedShapes": ["Banner"],
                  "sectionFields": { "title": { "type": "text" } } } } }
                """, "home.manifest.json"));
    }

    @Test
    void manifestSchemaRejectsRetiredMinMaxConstraints() {
        // min/max 属经典插槽基数约束（Phase D 退役）：constraints 仅允许 maxRows/maxPanelsPerColumn
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "home", "slots": { "promo": { "kind": "region", "allowedShapes": ["Banner"],
                  "constraints": { "min": 5, "max": 2 } } } }
                """, "home.manifest.json"));
    }

    @Test
    void manifestSchemaRejectsRetiredSourceKeys() {
        // allowedSources/defaultSource 已随来源机制于 Phase D 退役
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "home", "slots": { "promo": { "kind": "region", "allowedShapes": ["Banner"],
                  "allowedSources": ["curated"], "defaultSource": { "kind": "query" } } } }
                """, "home.manifest.json"));
    }

    @Test
    void manifestLoaderRejectsInvalidPageName() {
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "Home Page", "slots": { "promo": { "kind": "region", "allowedShapes": ["Banner"] } } }
                """, "home.manifest.json"));
    }

    @Test
    void builderRejectsDuplicatePageNameWithinTheme() {
        PageManifest home = manifest("""
                { "name": "home", "slots": { "promo": { "kind": "region", "allowedShapes": ["Banner"] } } }
                """, "home.manifest.json");
        RegistrySnapshot.Builder builder = RegistrySnapshot.builder()
                .addThemeManifest("default", home, "{}", "home.manifest.json");
        assertThrows(CompositionLoadException.class,
                () -> builder.addThemeManifest("default", home, "{}", "home2.manifest.json"));
    }

    @Test
    void builderRejectsDuplicateThemeShapeName() {
        RegistrySnapshot.Builder builder = RegistrySnapshot.builder()
                .addThemeShape("default", bannerShape(), "{}", "banner.json");
        assertThrows(CompositionLoadException.class,
                () -> builder.addThemeShape("default", bannerShape(), "{}", "banner-copy.json"));
    }

    @Test
    void rejectsOverlappingMigrationRangesWithConflictingVerbs() {
        // 两个 from 范围（1.x 与 1.2.x）重叠且都触及字段 subtitle：加载期校验错误（§13.2）
        Shape shape = shapeLoader.load("""
                { "name": "Demo", "version": "2.0.0", "item": { "subhead": { "type": "text" } },
                  "migrations": [
                    { "from": "1.x", "rename": { "subtitle": "subhead" } },
                    { "from": "1.2.x", "remove": ["subtitle"] }
                  ] }
                """, "theme:1/test", "demo.json");
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", shape, "{}", "demo.json").build();
        List<String> errors = validator.validate(snapshot);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("subtitle"), errors.toString());
        assertTrue(errors.get(0).contains("范围重叠"), errors.toString());
    }

    // ---- 区域插槽（page-regions §4）----

    @Test
    void regionSlotPassesWhenAllowedShapesResolveAndLayoutsKnown() {
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", bannerShape(), "{}", "banner.json")
                .addThemeManifest("default", manifest("""
                        { "name": "home", "slots": { "body": { "kind": "region",
                          "allowedShapes": ["Banner"], "allowedRowLayouts": ["full", "halves"] } } }
                        """, "home.manifest.json"), "{}", "home.manifest.json")
                .build();
        assertEquals(List.of(), validator.validate(snapshot));
    }

    @Test
    void rejectsRegionWithUnresolvableAllowedShape() {
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", bannerShape(), "{}", "banner.json")
                .addThemeManifest("default", manifest("""
                        { "name": "home", "slots": { "body": { "kind": "region",
                          "allowedShapes": ["Banner", "Missing"] } } }
                        """, "home.manifest.json"), "{}", "home.manifest.json")
                .build();
        List<String> errors = validator.validate(snapshot);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Missing"), errors.toString());
    }

    @Test
    void manifestSchemaRejectsRegionWithUnknownRowLayout() {
        // allowedRowLayouts 由 Schema 枚举约束，未知行布局在装载期即被拒（早于快照校验的兜底检查）
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "home", "slots": { "body": { "kind": "region",
                  "allowedShapes": ["Banner"], "allowedRowLayouts": ["full", "quintuple"] } } }
                """, "home.manifest.json"));
    }

    @Test
    void manifestLoaderParsesRegionAndDefaultsRowLayoutsToFullCatalog() {
        PageManifest m = manifest("""
                { "name": "home", "slots": { "body": { "kind": "region",
                  "allowedShapes": ["Banner"], "constraints": { "maxRows": 8, "maxPanelsPerColumn": 4 } } } }
                """, "home.manifest.json");
        PageManifest.SlotDef body = m.getSlots().get("body");
        assertEquals(List.of("Banner"), body.getAllowedShapes());
        assertEquals(RowLayoutCatalog.keys(), body.getAllowedRowLayouts());
        assertEquals(8, body.getMaxRows());
        assertEquals(4, body.getMaxPanelsPerColumn());
    }

    @Test
    void manifestSchemaRejectsRegionWithoutAllowedShapes() {
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "home", "slots": { "body": { "kind": "region" } } }
                """, "home.manifest.json"));
    }

    @Test
    void manifestSchemaRejectsRegionCarryingShapeOrCardinality() {
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "home", "slots": { "body": { "kind": "region",
                  "allowedShapes": ["Banner"], "shape": "Banner", "cardinality": "list" } } }
                """, "home.manifest.json"));
    }

    @Test
    void manifestSchemaRejectsClassicSlotForm() {
        // 经典插槽形态（无 kind、携带 shape/cardinality）已于 Phase D 退役：装载期一律拒绝
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "home", "slots": { "body": { "shape": "Banner", "cardinality": "list",
                  "allowedShapes": ["Banner"] } } }
                """, "home.manifest.json"));
    }

    @Test
    void allowsNonOverlappingMigrationRanges() {
        Shape shape = shapeLoader.load("""
                { "name": "Demo", "version": "3.0.0", "item": { "subhead": { "type": "text" } },
                  "migrations": [
                    { "from": "1.x", "rename": { "subtitle": "subhead" } },
                    { "from": "2.x", "remove": ["subtitle"] }
                  ] }
                """, "theme:1/test", "demo.json");
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", shape, "{}", "demo.json").build();
        assertEquals(List.of(), validator.validate(snapshot));
    }

    // ---- 共享区域引用（page-regions §15）----

    private static final String SITE_OWNER = """
            { "name": "site", "slots": { "aside": { "kind": "region",
              "allowedShapes": ["Banner"], "allowedRowLayouts": ["full"],
              "constraints": { "maxRows": 12, "maxPanelsPerColumn": 2 } } } }
            """;

    @Test
    void sharedReferenceResolvesToOwnerDefinition() {
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", bannerShape(), "{}", "banner.json")
                .addThemeManifest("default", manifest(SITE_OWNER, "site.manifest.json"), "{}", "site.manifest.json")
                .addThemeManifest("default", manifest("""
                        { "name": "channel", "template": "channel",
                          "slots": { "aside": { "kind": "region", "shared": "site" } } }
                        """, "channel.manifest.json"), "{}", "channel.manifest.json")
                .build();
        assertEquals(List.of(), validator.validate(snapshot));
        PageManifest channel = snapshot.getThemeManifest("default", "channel");
        PageManifest.SlotDef aside = channel.getSlots().get("aside");
        assertTrue(aside.isShared());
        assertEquals("site", aside.getSharedFrom());
        assertEquals(List.of("Banner"), aside.getAllowedShapes());
        assertEquals(List.of("full"), aside.getAllowedRowLayouts());
        assertEquals(12, aside.getMaxRows());
        assertEquals(2, aside.getMaxPanelsPerColumn());
        // 引用形态原文保留在 raw；模板绑定指向解引用后的同一实例
        assertEquals("site", ((java.util.Map<?, ?>) ((java.util.Map<?, ?>) channel.getRaw().get("slots"))
                .get("aside")).get("shared"));
        assertSame(channel, snapshot.resolveThemeManifestByTemplate("default", "channel"));
        // 宿主：无模板的清单可装载、不绑定模板，自身定义不带 sharedFrom 标记
        PageManifest site = snapshot.getThemeManifest("default", "site");
        assertNull(site.getSlots().get("aside").getSharedFrom());
        assertEquals(List.of(), site.getTemplates());
        assertNull(snapshot.resolveThemeManifestByTemplate("default", "site"));
    }

    @Test
    void rejectsSharedReferenceToMissingOwnerPage() {
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", bannerShape(), "{}", "banner.json")
                .addThemeManifest("default", manifest("""
                        { "name": "channel", "slots": { "aside": { "kind": "region", "shared": "site" } } }
                        """, "channel.manifest.json"), "{}", "channel.manifest.json")
                .build();
        List<String> errors = validator.validate(snapshot);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("shared 引用无效: site"), errors.toString());
        assertTrue(snapshot.getThemeManifest("default", "channel").getSlots().get("aside").isUnresolvedReference());
    }

    @Test
    void rejectsSharedReferenceWhenOwnerLacksTheRegion() {
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", bannerShape(), "{}", "banner.json")
                .addThemeManifest("default", manifest("""
                        { "name": "site", "slots": { "main": { "kind": "region", "allowedShapes": ["Banner"] } } }
                        """, "site.manifest.json"), "{}", "site.manifest.json")
                .addThemeManifest("default", manifest("""
                        { "name": "channel", "slots": { "aside": { "kind": "region", "shared": "site" } } }
                        """, "channel.manifest.json"), "{}", "channel.manifest.json")
                .build();
        List<String> errors = validator.validate(snapshot);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("页面 channel 区域 aside"), errors.toString());
    }

    @Test
    void rejectsSharedReferenceChainAndSelfReference() {
        // article → channel → site 是链式引用（只解一层）；home → home 是自引用：各报一错，channel 本身合法
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", bannerShape(), "{}", "banner.json")
                .addThemeManifest("default", manifest(SITE_OWNER, "site.manifest.json"), "{}", "site.manifest.json")
                .addThemeManifest("default", manifest("""
                        { "name": "channel", "slots": { "aside": { "kind": "region", "shared": "site" } } }
                        """, "channel.manifest.json"), "{}", "channel.manifest.json")
                .addThemeManifest("default", manifest("""
                        { "name": "article", "slots": { "aside": { "kind": "region", "shared": "channel" } } }
                        """, "article.manifest.json"), "{}", "article.manifest.json")
                .addThemeManifest("default", manifest("""
                        { "name": "home", "slots": { "aside": { "kind": "region", "shared": "home" } } }
                        """, "home.manifest.json"), "{}", "home.manifest.json")
                .build();
        List<String> errors = validator.validate(snapshot);
        assertEquals(2, errors.size(), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("页面 article 区域 aside")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("页面 home 区域 aside")), errors.toString());
        assertTrue(snapshot.getThemeManifest("default", "channel").getSlots().get("aside").isShared());
    }

    @Test
    void sharedReferenceMustTargetTheSameTheme() {
        // 宿主在另一主题命名空间：不可跨主题引用
        RegistrySnapshot snapshot = RegistrySnapshot.builder()
                .addThemeShape("default", bannerShape(), "{}", "banner.json")
                .addThemeShape("other", bannerShape(), "{}", "banner.json")
                .addThemeManifest("other", manifest(SITE_OWNER, "site.manifest.json"), "{}", "site.manifest.json")
                .addThemeManifest("default", manifest("""
                        { "name": "channel", "slots": { "aside": { "kind": "region", "shared": "site" } } }
                        """, "channel.manifest.json"), "{}", "channel.manifest.json")
                .build();
        List<String> errors = validator.validate(snapshot);
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith("[theme:default]"), errors.toString());
    }

    @Test
    void manifestSchemaRejectsSharedReferenceCarryingOwnConstraints() {
        // 引用形态只含 kind + shared：携带 allowedShapes / constraints / label 均在装载期被拒
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "channel", "slots": { "aside": { "kind": "region", "shared": "site",
                  "allowedShapes": ["Banner"] } } }
                """, "channel.manifest.json"));
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "channel", "slots": { "aside": { "kind": "region", "shared": "site",
                  "label": { "zh-CN": "侧栏" } } } }
                """, "channel.manifest.json"));
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "channel", "slots": { "aside": { "shared": "site" } } }
                """, "channel.manifest.json"));
        assertThrows(CompositionLoadException.class, () -> manifest("""
                { "name": "channel", "slots": { "aside": { "kind": "region", "shared": "Site Page" } } }
                """, "channel.manifest.json"));
    }
}
