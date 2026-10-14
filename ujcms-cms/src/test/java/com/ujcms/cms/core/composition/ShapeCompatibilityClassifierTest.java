package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shape 兼容性分级测试（§13.1）：additive / rename / breaking-required / breaking-removal，
 * 以及迁移块对移除的覆盖判定。
 */
class ShapeCompatibilityClassifierTest {
    private final ShapeLoader loader = new ShapeLoader(new CompositionSchemas());
    private final ShapeCompatibilityClassifier classifier = new ShapeCompatibilityClassifier();

    private Shape shape(String json) {
        return loader.load(json, "theme:1/test", "x");
    }

    @Test
    void additiveWhenNewOptionalField() {
        Shape old = shape("""
                { "name": "Demo", "version": "1.0.0", "item": { "a": { "type": "text" } } }""");
        Shape next = shape("""
                { "name": "Demo", "version": "1.1.0",
                  "item": { "a": { "type": "text" }, "b": { "type": "text" } } }""");
        ShapeCompatibilityClassifier.Result result = classifier.classify(old, next);
        assertEquals(ShapeCompatibility.COMPATIBLE_ADDITIVE, result.level());
        assertTrue(result.uncoveredRemovals().isEmpty());
    }

    @Test
    void renameWhenAliasDeclared() {
        Shape old = shape("""
                { "name": "Demo", "version": "1.0.0", "item": { "subtitle": { "type": "text" } } }""");
        Shape next = shape("""
                { "name": "Demo", "version": "1.1.0", "item": { "subhead": { "type": "text" } },
                  "migrations": [ { "from": "1.x", "rename": { "subtitle": "subhead" } } ] }""");
        ShapeCompatibilityClassifier.Result result = classifier.classify(old, next);
        assertEquals(ShapeCompatibility.COMPATIBLE_RENAME, result.level());
        assertTrue(result.renamedFields().contains("subtitle"));
        assertTrue(result.uncoveredRemovals().isEmpty());
    }

    @Test
    void breakingRequiredWhenNewRequiredField() {
        Shape old = shape("""
                { "name": "Demo", "version": "1.0.0", "item": { "a": { "type": "text" } } }""");
        Shape next = shape("""
                { "name": "Demo", "version": "2.0.0",
                  "item": { "a": { "type": "text" }, "b": { "type": "text", "required": true } } }""");
        ShapeCompatibilityClassifier.Result result = classifier.classify(old, next);
        assertEquals(ShapeCompatibility.BREAKING_REQUIRED, result.level());
        assertTrue(result.newRequiredFields().contains("b"));
        assertTrue(result.uncoveredRemovals().isEmpty());
    }

    @Test
    void breakingRemovalWhenFieldDeletedWithoutMigration() {
        Shape old = shape("""
                { "name": "Demo", "version": "1.0.0",
                  "item": { "a": { "type": "text" }, "b": { "type": "text" } } }""");
        Shape next = shape("""
                { "name": "Demo", "version": "2.0.0", "item": { "a": { "type": "text" } } }""");
        ShapeCompatibilityClassifier.Result result = classifier.classify(old, next);
        assertEquals(ShapeCompatibility.BREAKING_REMOVAL, result.level());
        assertTrue(result.uncoveredRemovals().contains("b"));
    }

    @Test
    void removalCoveredByRemoveVerbIsNotBreaking() {
        Shape old = shape("""
                { "name": "Demo", "version": "1.0.0",
                  "item": { "a": { "type": "text" }, "legacy": { "type": "text" } } }""");
        Shape next = shape("""
                { "name": "Demo", "version": "2.0.0", "item": { "a": { "type": "text" } },
                  "migrations": [ { "from": "1.x", "remove": ["legacy"] } ] }""");
        ShapeCompatibilityClassifier.Result result = classifier.classify(old, next);
        assertEquals(ShapeCompatibility.COMPATIBLE_RENAME, result.level());
        assertTrue(result.uncoveredRemovals().isEmpty());
    }

    @Test
    void retypeWithoutTransformIsBreakingRemoval() {
        Shape old = shape("""
                { "name": "Demo", "version": "1.0.0", "item": { "a": { "type": "text" } } }""");
        Shape next = shape("""
                { "name": "Demo", "version": "2.0.0", "item": { "a": { "type": "number" } } }""");
        ShapeCompatibilityClassifier.Result result = classifier.classify(old, next);
        assertEquals(ShapeCompatibility.BREAKING_REMOVAL, result.level());
        assertTrue(result.uncoveredRemovals().contains("a"));
    }

    @Test
    void coverageIgnoresBlocksWhoseFromRangeExcludesOldVersion() {
        // §21 Q12(k)：from:"1.x" 的旧迁移块对 2.0.0 的旧卡片不会套用（project 按 from.contains 匹配），
        // 不能作为 2.x→3.0 移除的覆盖证据——此前全量并集会把该移除误判为已覆盖
        Shape old = shape("""
                { "name": "Demo", "version": "2.0.0",
                  "item": { "a": { "type": "text" }, "legacy": { "type": "text" } } }""");
        Shape next = shape("""
                { "name": "Demo", "version": "3.0.0", "item": { "a": { "type": "text" } },
                  "migrations": [ { "from": "1.x", "remove": ["legacy"] } ] }""");
        ShapeCompatibilityClassifier.Result result = classifier.classify(old, next);
        assertEquals(ShapeCompatibility.BREAKING_REMOVAL, result.level());
        assertTrue(result.uncoveredRemovals().contains("legacy"));
    }

    @Test
    void coverageCountsBlockWhoseFromRangeContainsOldVersion() {
        // 对照：from:"2.x" 含旧版本 2.0.0 → 移除被覆盖，改名/移除级兼容
        Shape old = shape("""
                { "name": "Demo", "version": "2.0.0",
                  "item": { "a": { "type": "text" }, "legacy": { "type": "text" } } }""");
        Shape next = shape("""
                { "name": "Demo", "version": "3.0.0", "item": { "a": { "type": "text" } },
                  "migrations": [ { "from": "2.x", "remove": ["legacy"] } ] }""");
        ShapeCompatibilityClassifier.Result result = classifier.classify(old, next);
        assertEquals(ShapeCompatibility.COMPATIBLE_RENAME, result.level());
        assertTrue(result.uncoveredRemovals().isEmpty());
    }
}
