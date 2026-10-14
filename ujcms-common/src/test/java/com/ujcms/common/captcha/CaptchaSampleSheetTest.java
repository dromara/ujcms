package com.ujcms.common.captcha;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证码样张：把随机验证码与完整字形表拼成一张图写到 {@code target/captcha-samples/sheet.png}，供人工检查可读性。
 * <p>
 * 也可作为独立程序运行（{@code main}，参数为输出路径），用于在没有 fontconfig 的精简 JRE 容器中验证渲染不依赖字体子系统。
 *
 * @author PONY
 */
class CaptchaSampleSheetTest {
    private static final Color TEXT = new Color(23, 67, 172);
    private static final String ALL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int GAP = 8;

    @Test
    void writesTheSampleSheet() throws IOException {
        File file = new File("target/captcha-samples/sheet.png");
        write(file);
        assertTrue(file.length() > 1000);
    }

    public static void main(String[] args) throws IOException {
        File file = new File(args.length > 0 ? args[0] : "target/captcha-samples/sheet.png");
        write(file);
        System.out.println("captcha sample sheet written to " + file.getAbsolutePath());
    }

    static void write(File file) throws IOException {
        System.setProperty("java.awt.headless", "true");
        CaptchaRenderer random = new CaptchaRenderer(120, 36, 30, 5, 20, 2, CaptchaRenderer.DEFAULT_ALPHABET,
                TEXT, Color.WHITE);
        CaptchaRenderer alphabet = new CaptchaRenderer(240, 36, 30, 9, 20, 2, ALL, TEXT, Color.WHITE);

        int columns = 4;
        int rows = 6;
        int cellW = 240;
        int cellH = 72;
        int alphabetRows = 4;
        int alphabetW = 480;
        int sheetW = GAP + columns * (cellW + GAP);
        int sheetH = GAP + rows * (cellH + GAP) + GAP + alphabetRows * (cellH + GAP);
        BufferedImage sheet = new BufferedImage(sheetW, sheetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        try {
            g.setColor(new Color(226, 232, 240));
            g.fillRect(0, 0, sheetW, sheetH);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    BufferedImage image = random.next().image();
                    g.drawImage(image, GAP + c * (cellW + GAP), GAP + r * (cellH + GAP), null);
                }
            }
            int top = GAP + rows * (cellH + GAP) + GAP;
            for (int r = 0; r < alphabetRows; r++) {
                String word = ALL.substring(r * 9, r * 9 + 9);
                BufferedImage image = alphabet.render(word).image();
                g.drawImage(image, GAP, top + r * (cellH + GAP), null);
                BufferedImage again = alphabet.render(word).image();
                g.drawImage(again, GAP + alphabetW + GAP, top + r * (cellH + GAP), null);
            }
        } finally {
            g.dispose();
        }
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        ImageIO.write(sheet, "png", file);
    }
}
