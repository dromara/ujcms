package com.ujcms.common.captcha;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CaptchaGlyphs} 的单元测试：字形齐全、坐标在网格内、路径语法。
 *
 * @author PONY
 */
class CaptchaGlyphsTest {
    private static final String ALL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Test
    void definesEveryLetterAndDigit() {
        assertEquals("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ", CaptchaGlyphs.characters());
        for (char c : ALL.toCharArray()) {
            assertTrue(CaptchaGlyphs.has(c));
            assertNotNull(CaptchaGlyphs.get(c));
            assertNotNull(CaptchaGlyphs.get(Character.toLowerCase(c)), "lower case maps to the upper-case glyph");
        }
        assertNull(CaptchaGlyphs.get('#'));
        for (char c : CaptchaRenderer.DEFAULT_ALPHABET.toCharArray()) {
            assertTrue(CaptchaGlyphs.has(c), "default alphabet character without glyph: " + c);
        }
    }

    @Test
    void glyphsStayInsideTheGrid() {
        for (char c : ALL.toCharArray()) {
            CaptchaGlyphs.Glyph glyph = CaptchaGlyphs.get(c);
            assertNotNull(glyph);
            assertTrue(glyph.advance() > 0 && glyph.advance() <= 100, "advance of " + c);
            assertTrue(!glyph.strokes().isEmpty(), "strokes of " + c);
            for (CaptchaGlyphs.Stroke stroke : glyph.strokes()) {
                assertTrue(stroke.pointCount() >= 2);
                int expected = 1;
                for (byte segment : stroke.segments()) {
                    expected += segment;
                }
                assertEquals(expected, stroke.pointCount(), "point count of a stroke of " + c);
                for (int k = 0; k < stroke.points().length; k += 2) {
                    float x = stroke.points()[k];
                    float y = stroke.points()[k + 1];
                    assertTrue(x >= -15 && x <= glyph.advance() + 15, "x of " + c + ": " + x);
                    assertTrue(y >= -15 && y <= 115, "y of " + c + ": " + y);
                }
            }
        }
    }

    @Test
    void parsesPathsAndRejectsMalformedOnes() {
        List<CaptchaGlyphs.Stroke> strokes = CaptchaGlyphs.parse("M 0 0 L 10 10 Q 20 20 30 10 | M 5 5 L 6 6");
        assertEquals(2, strokes.size());
        assertEquals(4, strokes.get(0).pointCount());
        assertEquals(2, strokes.get(0).segments().length);
        assertEquals(1, strokes.get(1).segments().length);

        assertThrows(IllegalArgumentException.class, () -> CaptchaGlyphs.parse("L 10 10"));
        assertThrows(IllegalArgumentException.class, () -> CaptchaGlyphs.parse("M 0 0"));
        assertThrows(IllegalArgumentException.class, () -> CaptchaGlyphs.parse("M 0 0 L 1 1 M 2 2 L 3 3"));
        assertThrows(IllegalArgumentException.class, () -> CaptchaGlyphs.parse("M 0 0 C 1 1 2 2 3 3"));
        assertThrows(RuntimeException.class, () -> CaptchaGlyphs.parse("M 0 0 L 1"));
    }
}
