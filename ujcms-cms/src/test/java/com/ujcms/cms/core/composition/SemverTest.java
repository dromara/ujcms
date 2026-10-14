package com.ujcms.cms.core.composition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语义化版本与范围测试（§13.2）：解析、比较、范围包含与重叠。
 */
class SemverTest {
    @Test
    void parsesAndIgnoresQualifier() {
        assertEquals(new Semver(2, 1, 3), Semver.parse("2.1.3"));
        assertEquals(new Semver(2, 0, 0), Semver.parse("2.0.0-beta.1"));
        assertEquals(new Semver(1, 2, 0), Semver.parse("1.2.0+build.7"));
        assertEquals(new Semver(0, 0, 0), Semver.parse(""));
        assertEquals(new Semver(1, 0, 0), Semver.parse("1"));
    }

    @Test
    void comparesByPrecedence() {
        assertTrue(Semver.parse("1.0.0").compareTo(Semver.parse("2.0.0")) < 0);
        assertTrue(Semver.parse("1.2.0").compareTo(Semver.parse("1.1.9")) > 0);
        assertEquals(0, Semver.parse("1.2.3").compareTo(Semver.parse("1.2.3")));
    }

    @Test
    void rangeContains() {
        assertTrue(Semver.Range.parse("1.x").contains(Semver.parse("1.9.4")));
        assertFalse(Semver.Range.parse("1.x").contains(Semver.parse("2.0.0")));
        assertTrue(Semver.Range.parse("1.2.x").contains(Semver.parse("1.2.7")));
        assertFalse(Semver.Range.parse("1.2.x").contains(Semver.parse("1.3.0")));
        assertTrue(Semver.Range.parse("1.2.3").contains(Semver.parse("1.2.3")));
        assertFalse(Semver.Range.parse("1.2.3").contains(Semver.parse("1.2.4")));
        assertTrue(Semver.Range.parse("*").contains(Semver.parse("9.9.9")));
    }

    @Test
    void rangeOverlap() {
        assertTrue(Semver.Range.parse("1.x").overlaps(Semver.Range.parse("1.2.x")));
        assertTrue(Semver.Range.parse("1.2.x").overlaps(Semver.Range.parse("1.x")));
        assertFalse(Semver.Range.parse("1.x").overlaps(Semver.Range.parse("2.x")));
        assertTrue(Semver.Range.parse("1.2.3").overlaps(Semver.Range.parse("1.x")));
        assertFalse(Semver.Range.parse("1.2.3").overlaps(Semver.Range.parse("1.3.x")));
        assertTrue(Semver.Range.parse("*").overlaps(Semver.Range.parse("3.1.4")));
    }

    @Test
    void lowerBoundForOrdering() {
        assertEquals(new Semver(1, 0, 0), Semver.Range.parse("1.x").lowerBound());
        assertEquals(new Semver(1, 2, 0), Semver.Range.parse("1.2.x").lowerBound());
        assertEquals(new Semver(0, 0, 0), Semver.Range.parse("*").lowerBound());
    }
}
