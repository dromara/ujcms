package com.ujcms.common.captcha;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SliderRenderer} 与 {@link SliderCaptchaProvider} 的单元测试：尺寸、范围、拼图透明通道、缺口压暗、每次不同、
 * 容差校验（docs/spec/captcha/plan.md Policy 3）。另把样张写到 target/captcha-samples/ 供肉眼检查。
 */
class SliderRendererTest {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 150;
    private static final int PIECE = 50;
    private static final int SCALE = 2;

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void rendersImagesOfTheConfiguredShape() {
        SliderRenderer renderer = new SliderRenderer(WIDTH, HEIGHT, PIECE, SCALE);
        int previousX = -1;
        boolean moved = false;
        for (int i = 0; i < 30; i++) {
            SliderRenderer.SliderImage image = renderer.next();
            assertEquals(WIDTH * SCALE, image.background().getWidth());
            assertEquals(HEIGHT * SCALE, image.background().getHeight());
            assertEquals(image.pieceWidth() * SCALE, image.piece().getWidth());
            assertEquals(image.pieceHeight() * SCALE, image.piece().getHeight());
            assertTrue(image.pieceWidth() >= PIECE && image.pieceWidth() <= PIECE + PIECE / 4);
            assertTrue(image.pieceHeight() >= PIECE && image.pieceHeight() <= PIECE + PIECE / 4);
            assertTrue(image.x() >= image.pieceWidth(), "the gap must sit right of the piece's start position");
            assertTrue(image.x() + image.pieceWidth() <= WIDTH);
            assertTrue(image.y() >= 0 && image.y() + image.pieceHeight() <= HEIGHT);
            if (previousX >= 0 && previousX != image.x()) {
                moved = true;
            }
            previousX = image.x();
        }
        assertTrue(moved, "the gap position must vary");
    }

    @Test
    void pieceHasAlphaAndGapIsDarkened() {
        SliderRenderer renderer = new SliderRenderer(WIDTH, HEIGHT, PIECE, SCALE);
        SliderRenderer.SliderImage image = renderer.next();
        BufferedImage piece = image.piece();
        int opaque = 0;
        int transparent = 0;
        long pieceLuma = 0;
        long gapLuma = 0;
        for (int y = 0; y < piece.getHeight(); y++) {
            for (int x = 0; x < piece.getWidth(); x++) {
                int argb = piece.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    transparent++;
                } else if (alpha == 255) {
                    opaque++;
                    pieceLuma += luma(argb);
                    gapLuma += luma(image.background().getRGB(image.x() * SCALE + x, image.y() * SCALE + y));
                }
            }
        }
        assertTrue(opaque > piece.getWidth() * piece.getHeight() / 3, "the jigsaw body is opaque");
        assertTrue(transparent > piece.getWidth() * piece.getHeight() / 10, "the area outside the jigsaw is transparent");
        assertTrue(gapLuma < pieceLuma * 0.8, "the gap on the background is darker than the piece cut from it");
    }

    @Test
    void refusesPiecesThatDoNotFit() {
        assertThrows(IllegalArgumentException.class, () -> new SliderRenderer(100, 60, 50, 1));
        assertThrows(IllegalArgumentException.class, () -> new SliderRenderer(300, 150, 0, 1));
    }

    @Test
    void providerVerifiesWithinTolerance() {
        CaptchaCrypto crypto = new CaptchaCrypto("0123456789abcdef0123456789abcdef");
        SliderCaptchaProvider provider = new SliderCaptchaProvider(
                new SliderRenderer(WIDTH, HEIGHT, PIECE, 1), crypto, WIDTH, HEIGHT, 5);
        CaptchaChallenge challenge = provider.challenge();
        assertEquals(WIDTH, challenge.payload().get("width"));
        assertTrue(challenge.payload().containsKey("piece"));
        String cipher = (String) challenge.claims().get(SliderCaptchaProvider.CLAIM_ANSWER);
        int x = Integer.parseInt(crypto.decrypt(cipher));
        Map<String, Object> claims = challenge.claims();
        assertTrue(provider.verify(claims, String.valueOf(x)));
        assertTrue(provider.verify(claims, String.valueOf(x + 5)));
        assertTrue(provider.verify(claims, String.valueOf(x - 5)));
        assertTrue(provider.verify(claims, (x + 4) + ".4"), "fractional answers are rounded");
        assertFalse(provider.verify(claims, String.valueOf(x + 6)));
        assertFalse(provider.verify(claims, String.valueOf(x - 6)));
        assertFalse(provider.verify(claims, "abc"));
        assertFalse(provider.verify(claims, "-1"));
        assertFalse(provider.verify(claims, String.valueOf(WIDTH + 1)));
        assertFalse(provider.verify(Map.of(SliderCaptchaProvider.CLAIM_ANSWER, "garbage"), String.valueOf(x)));
        // 同一期望值每次密文不同（随机 IV）
        assertNotEquals(cipher, crypto.encrypt(String.valueOf(x)));
    }

    /**
     * 样张：背景 + 拼图停在起点、拼图停在缺口处，写到 target/captcha-samples/
     */
    @Test
    void writesSampleSheet() throws IOException {
        SliderRenderer renderer = new SliderRenderer(WIDTH, HEIGHT, PIECE, SCALE);
        Path dir = Path.of("target", "captcha-samples");
        Files.createDirectories(dir);
        for (int i = 0; i < 3; i++) {
            SliderRenderer.SliderImage image = renderer.next();
            BufferedImage sheet = new BufferedImage(WIDTH * SCALE, HEIGHT * SCALE * 2 + 8, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sheet.createGraphics();
            try {
                g.drawImage(image.background(), 0, 0, null);
                g.drawImage(image.piece(), 0, image.y() * SCALE, null);
                g.drawImage(image.background(), 0, HEIGHT * SCALE + 8, null);
                g.drawImage(image.piece(), image.x() * SCALE, HEIGHT * SCALE + 8 + image.y() * SCALE, null);
            } finally {
                g.dispose();
            }
            ImageIO.write(sheet, "png", dir.resolve("slider-" + i + ".png").toFile());
        }
        assertTrue(Files.exists(dir.resolve("slider-0.png")));
    }

    private static int luma(int rgb) {
        return ((rgb >> 16) & 0xFF) * 3 + ((rgb >> 8) & 0xFF) * 6 + (rgb & 0xFF);
    }
}
