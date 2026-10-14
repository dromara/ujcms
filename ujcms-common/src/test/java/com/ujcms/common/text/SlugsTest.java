package com.ujcms.common.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Slugs} 单元测试：确定性、离线、永不抛异常的 slug 化规则。
 *
 * @author PONY
 */
class SlugsTest {

    @Test
    void asciiWithPunctuationCollapsesToDashes() {
        assertEquals("hello-world-2026", Slugs.slugify("Hello, World! 2026"));
    }

    @Test
    void accentsAreStrippedViaNfkd() {
        assertEquals("creme-brulee", Slugs.slugify("Crème Brûlée"));
    }

    @Test
    void fullwidthLettersDigitsAndIdeographicSpaceFoldToAscii() {
        assertEquals("ujcms-13", Slugs.slugify("ＵＪＣＭＳ　１３"));
    }

    @Test
    void pureCjkYieldsEmpty() {
        assertEquals("", Slugs.slugify("全新发布"));
    }

    @Test
    void mixedCjkKeepsOnlyAsciiPart() {
        assertEquals("ujcms-13", Slugs.slugify("UJCMS 13 全新发布"));
    }

    @Test
    void underscoresAndUppercaseAreNormalised() {
        assertEquals("hello-world", Slugs.slugify("Hello_World"));
        assertEquals("a-b-c", Slugs.slugify("A__B--C"));
    }

    @Test
    void leadingAndTrailingDashesAreStripped() {
        assertEquals("hello", Slugs.slugify("--hello--"));
        assertEquals("hello", Slugs.slugify("  !hello!  "));
    }

    @Test
    void truncationPrefersDashBoundaryAndNeverEndsWithDash() {
        // 超长时优先在后半段最近的连字符处截断
        assertEquals("alpha-beta", Slugs.slugify("alpha-beta-gamma", 12));
        // 截断点恰好落在连字符上：不得以 '-' 结尾
        assertEquals("alpha", Slugs.slugify("alpha-beta", 6));
        // 连字符在前半段：硬截断（不丢过多内容）
        assertEquals("ab-cdefg", Slugs.slugify("ab-cdefghijkl", 8));
        // 未超长：原样
        assertEquals("alpha-beta", Slugs.slugify("alpha-beta", 10));
        assertFalse(Slugs.slugify("alpha-beta-gamma-delta", 17).endsWith("-"));
    }

    @Test
    void truncateHelperHonoursLimit() {
        assertEquals("hello", Slugs.truncate("hello", 10));
        assertEquals("hello-world", Slugs.truncate("hello-world-again", 13));
        assertEquals("", Slugs.truncate("hello", 0));
    }

    @Test
    void nullAndBlankYieldEmpty() {
        assertEquals("", Slugs.slugify(null));
        assertEquals("", Slugs.slugify(""));
        assertEquals("", Slugs.slugify("   "));
        assertEquals("", Slugs.slugify("hello", 0));
    }

    @Test
    void isValidMatchesEntityPattern() {
        assertTrue(Slugs.isValid(""));
        assertTrue(Slugs.isValid("abc-1_2"));
        assertTrue(Slugs.isValid("2024"));
        assertFalse(Slugs.isValid("a b"));
        assertFalse(Slugs.isValid("中"));
        assertFalse(Slugs.isValid("a/b"));
        assertFalse(Slugs.isValid(null));
    }
}
