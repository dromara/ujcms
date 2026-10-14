package com.ujcms.common.captcha;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CaptchaRenderer} 的单元测试：尺寸、答案、字母表、不出界、每次形状不同、图片内容。
 *
 * @author PONY
 */
class CaptchaRendererTest {
    private static final int WIDTH = 120;
    private static final int HEIGHT = 36;
    private static final int FONT_SIZE = 30;
    private static final int WORD_LENGTH = 5;
    private static final int SCALE = 2;
    private static final Color TEXT = new Color(23, 67, 172);
    private static final String ALL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private static CaptchaRenderer renderer(float distortion) {
        return renderer(distortion, WORD_LENGTH, CaptchaRenderer.DEFAULT_ALPHABET);
    }

    private static CaptchaRenderer renderer(float distortion, int wordLength, String alphabet) {
        return new CaptchaRenderer(WIDTH, HEIGHT, FONT_SIZE, wordLength, distortion, SCALE, alphabet, TEXT, Color.WHITE);
    }

    @Test
    void rendersWordsOfTheConfiguredShape() {
        CaptchaRenderer renderer = renderer(20);
        String previous = null;
        for (int i = 0; i < 200; i++) {
            CaptchaRenderer.CaptchaImage captcha = renderer.next();
            assertEquals(WORD_LENGTH, captcha.word().length());
            for (char c : captcha.word().toCharArray()) {
                assertTrue(CaptchaRenderer.DEFAULT_ALPHABET.indexOf(c) >= 0, "character outside the alphabet: " + c);
            }
            assertEquals(WIDTH * SCALE, captcha.image().getWidth());
            assertEquals(HEIGHT * SCALE, captcha.image().getHeight());
            assertNotEquals(previous, captcha.word());
            previous = captcha.word();
            assertTrue(inkPixels(captcha.image()) > 200, "the image should contain the rendered word");
        }
    }

    @Test
    void worksWithoutDistortionAndAtScaleOne() {
        CaptchaRenderer.CaptchaImage captcha = renderer(0).next();
        assertEquals(WIDTH * SCALE, captcha.image().getWidth());
        assertTrue(inkPixels(captcha.image()) > 200);

        CaptchaRenderer legacy = new CaptchaRenderer(WIDTH, HEIGHT, FONT_SIZE, WORD_LENGTH, 20, 1,
                CaptchaRenderer.DEFAULT_ALPHABET, TEXT, Color.WHITE);
        BufferedImage image = legacy.next().image();
        assertEquals(WIDTH, image.getWidth());
        assertEquals(HEIGHT, image.getHeight());
        assertTrue(inkPixels(image) > 50);
    }

    @Test
    void everyGlyphRendersInk() {
        for (char c : ALL.toCharArray()) {
            CaptchaRenderer renderer = renderer(20, 1, String.valueOf(c));
            CaptchaRenderer.CaptchaImage captcha = renderer.next();
            assertEquals(String.valueOf(c), captcha.word());
            assertTrue(inkPixels(captcha.image()) > 150, "glyph '" + c + "' should render ink");
        }
    }

    @Test
    void inkNeverTouchesTheEdge() {
        // 长词、满字高：整词必须被等比缩小后仍留在画布内，扭曲之后也不出界
        CaptchaRenderer[] renderers = {renderer(20), renderer(20, 7, ALL), renderer(40, 6, "WMW")};
        for (CaptchaRenderer renderer : renderers) {
            for (int i = 0; i < 100; i++) {
                BufferedImage image = renderer.next().image();
                Rectangle ink = inkBounds(image);
                assertNotNull(ink, "image should contain ink");
                assertTrue(ink.x >= 1 && ink.y >= 1, "ink touches the top/left edge: " + ink);
                assertTrue(ink.x + ink.width <= image.getWidth() - 1
                        && ink.y + ink.height <= image.getHeight() - 1, "ink touches the bottom/right edge: " + ink);
            }
        }
    }

    @Test
    void everyGlyphIsConnectedToItsNeighbour() {
        // 整词必须是一块连通的墨迹：没有孤立字符可供机器切分
        CaptchaRenderer[] renderers = {renderer(20), renderer(0), renderer(20, 7, ALL), renderer(40, 4, "ILJT17")};
        for (CaptchaRenderer renderer : renderers) {
            for (int i = 0; i < 150; i++) {
                CaptchaRenderer.CaptchaImage captcha = renderer.next();
                int components = inkComponents(captcha.image());
                assertEquals(1, components, "'" + captcha.word() + "' renders " + components + " separate ink blobs");
            }
        }
    }

    @Test
    void sameWordNeverRendersTheSameShape() {
        CaptchaRenderer renderer = renderer(20);
        for (int i = 0; i < 20; i++) {
            BufferedImage a = renderer.render("ABC23").image();
            BufferedImage b = renderer.render("ABC23").image();
            int differing = 0;
            int ink = 0;
            for (int y = 0; y < a.getHeight(); y++) {
                for (int x = 0; x < a.getWidth(); x++) {
                    boolean inkA = isInk(a.getRGB(x, y));
                    boolean inkB = isInk(b.getRGB(x, y));
                    if (inkA || inkB) {
                        ink++;
                    }
                    if (inkA != inkB) {
                        differing++;
                    }
                }
            }
            assertTrue(differing > ink * 0.05, "two renders of the same word should differ: " + differing + "/" + ink);
        }
    }

    @Test
    void encodesAsPng() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(renderer(20).next().image(), "png", out));
        assertTrue(out.size() > 100);
    }

    @Test
    void rejectsInvalidArguments() {
        String alphabet = "AB";
        assertThrows(IllegalArgumentException.class,
                () -> new CaptchaRenderer(0, HEIGHT, FONT_SIZE, 5, 20, SCALE, alphabet, TEXT, Color.WHITE));
        assertThrows(IllegalArgumentException.class,
                () -> new CaptchaRenderer(WIDTH, HEIGHT, 0, 5, 20, SCALE, alphabet, TEXT, Color.WHITE));
        assertThrows(IllegalArgumentException.class,
                () -> new CaptchaRenderer(WIDTH, HEIGHT, FONT_SIZE, 0, 20, SCALE, alphabet, TEXT, Color.WHITE));
        assertThrows(IllegalArgumentException.class,
                () -> new CaptchaRenderer(WIDTH, HEIGHT, FONT_SIZE, 5, 20, 0, alphabet, TEXT, Color.WHITE));
        assertThrows(IllegalArgumentException.class,
                () -> new CaptchaRenderer(WIDTH, HEIGHT, FONT_SIZE, 5, 20, SCALE, "", TEXT, Color.WHITE));
        assertThrows(IllegalArgumentException.class,
                () -> new CaptchaRenderer(WIDTH, HEIGHT, FONT_SIZE, 5, 20, SCALE, "A#", TEXT, Color.WHITE));
    }

    /**
     * 与背景色明显不同的像素数
     */
    static int inkPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isInk(image.getRGB(x, y))) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 墨迹像素的包围盒；没有墨迹时为 {@code null}
     */
    static Rectangle inkBounds(BufferedImage image) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isInk(image.getRGB(x, y))) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? null : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /**
     * 墨迹的 8 连通分量个数
     */
    static int inkComponents(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[] ink = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ink[y * w + x] = isFaintInk(image.getRGB(x, y));
            }
        }
        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        int components = 0;
        for (int start = 0; start < ink.length; start++) {
            if (!ink[start] || seen[start]) {
                continue;
            }
            components++;
            int top = 0;
            stack[top++] = start;
            seen[start] = true;
            while (top > 0) {
                int p = stack[--top];
                int px = p % w;
                int py = p / w;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = px + dx;
                        int ny = py + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        int n = ny * w + nx;
                        if (ink[n] && !seen[n]) {
                            seen[n] = true;
                            stack[top++] = n;
                        }
                    }
                }
            }
        }
        return components;
    }

    private static boolean isFaintInk(int rgb) {
        int distance = Math.abs(((rgb >> 16) & 0xFF) - 255) + Math.abs(((rgb >> 8) & 0xFF) - 255)
                + Math.abs((rgb & 0xFF) - 255);
        return distance > 60;
    }

    private static boolean isInk(int rgb) {
        int distance = Math.abs(((rgb >> 16) & 0xFF) - 255) + Math.abs(((rgb >> 8) & 0xFF) - 255)
                + Math.abs((rgb & 0xFF) - 255);
        return distance > 120;
    }
}
