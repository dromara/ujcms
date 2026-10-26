package com.ujcms.common.captcha;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.security.SecureRandom;

/**
 * 滑块拼图渲染器。纯几何绘制：背景是程序生成的渐变色块（无需图库、无版权、无字体），从中挖出一块拼图形状的缺口，
 * 并把这块拼图作为带透明通道的小图单独输出；浏览器把拼图从最左侧拖到缺口处即为答案（缺口外接框的左边距，逻辑像素）。
 * <p>
 * 背景：两种随机柔和色沿随机方向线性渐变，再叠加若干随机位置、随机颜色的径向色斑（每次都不同）。
 * 缺口：正方形主体 + 一侧外凸的圆形榫头 + 一侧内凹的圆形榫眼，榫头在上或右、榫眼在左或下随机；缺口压暗并描一圈浅色边。
 * 另在别处画一个只压暗、不描边的“假缺口”，让“找最暗区域”的朴素检测失效。
 * 拼图：从干净背景（挖缺口前）按形状裁出，描一圈浅色边。
 * <p>
 * 所有尺寸参数为逻辑像素，实际图片为逻辑尺寸乘以像素密度倍数（高分屏清晰），页面以 CSS 固定显示尺寸。
 *
 * @author PONY
 */
public final class SliderRenderer {
    /**
     * 一道滑块题：缺口位置、拼图尺寸（逻辑像素）与两张图。
     *
     * @param x           缺口外接框左边距（答案）
     * @param y           缺口外接框上边距
     * @param pieceWidth  拼图外接框宽
     * @param pieceHeight 拼图外接框高
     * @param background  带缺口的背景图（不透明）
     * @param piece       拼图（透明通道）
     */
    public record SliderImage(int x, int y, int pieceWidth, int pieceHeight, BufferedImage background,
                              BufferedImage piece) {
    }

    /**
     * 榫头/榫眼半径（相对主体边长）
     */
    private static final double TAB_RATIO = 0.2;
    /**
     * 缺口与画布边缘、与假缺口之间的最小间距（逻辑像素）
     */
    private static final int MARGIN = 8;
    /**
     * 缺口左边距的下限：至少比拼图外接框宽出这么多，拖动距离才不至于太短
     */
    private static final int MIN_DRAG = 20;
    /**
     * 径向色斑数量
     */
    private static final int BLOBS = 4;

    private final int width;
    private final int height;
    private final int pieceSize;
    private final int scale;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param width     背景逻辑宽
     * @param height    背景逻辑高
     * @param pieceSize 拼图主体边长（逻辑像素）
     * @param scale     像素密度倍数
     */
    public SliderRenderer(int width, int height, int pieceSize, int scale) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("slider width and height must be positive");
        }
        if (pieceSize <= 0) {
            throw new IllegalArgumentException("slider piece size must be positive");
        }
        if (scale <= 0) {
            throw new IllegalArgumentException("slider scale must be positive");
        }
        int tab = (int) Math.round(pieceSize * TAB_RATIO);
        int bounds = pieceSize + tab;
        if (bounds * 2 + MIN_DRAG + MARGIN > width || bounds + 2 * MARGIN > height) {
            throw new IllegalArgumentException("slider piece does not fit the background: "
                    + width + "x" + height + " / piece " + pieceSize);
        }
        this.width = width;
        this.height = height;
        this.pieceSize = pieceSize;
        this.scale = scale;
    }

    /**
     * 生成下一道题
     */
    public SliderImage next() {
        int r = (int) Math.round(pieceSize * TAB_RATIO);
        Piece shape = new Piece(pieceSize, r, random.nextBoolean(), random.nextBoolean());
        int x = between(shape.width + MIN_DRAG, width - shape.width - MARGIN);
        int y = between(MARGIN, height - shape.height - MARGIN);

        BufferedImage clean = paintBackground();
        BufferedImage piece = cutPiece(clean, shape, x, y);
        BufferedImage background = carve(clean, shape, x, y);
        return new SliderImage(x, y, shape.width, shape.height, background, piece);
    }

    /**
     * 拼图形状：主体正方形、外凸榫头、内凹榫眼。坐标相对外接框左上角，逻辑像素。
     */
    private static final class Piece {
        final int width;
        final int height;
        final Area area;

        Piece(int size, int r, boolean tabTop, boolean notchLeft) {
            // 榫头在上时外接框高出 r，在右时宽出 r
            int bodyX = 0;
            int bodyY = tabTop ? r : 0;
            this.width = size + (tabTop ? 0 : r);
            this.height = size + (tabTop ? r : 0);
            Area a = new Area(new Rectangle2D.Double(bodyX, bodyY, size, size));
            double tabCx = tabTop ? bodyX + size / 2.0 : bodyX + size;
            double tabCy = tabTop ? bodyY : bodyY + size / 2.0;
            a.add(new Area(new Ellipse2D.Double(tabCx - r, tabCy - r, 2.0 * r, 2.0 * r)));
            double notchCx = notchLeft ? bodyX : bodyX + size / 2.0;
            double notchCy = notchLeft ? bodyY + size / 2.0 : bodyY + size;
            a.subtract(new Area(new Ellipse2D.Double(notchCx - r, notchCy - r, 2.0 * r, 2.0 * r)));
            this.area = a;
        }

        Shape at(int x, int y, int scale) {
            AffineTransform t = AffineTransform.getScaleInstance(scale, scale);
            t.translate(x, y);
            return t.createTransformedShape(area);
        }
    }

    /**
     * 程序生成的背景：线性渐变打底，再叠加径向色斑。逐像素计算，直接写像素数组。
     */
    private BufferedImage paintBackground() {
        int w = width * scale;
        int h = height * scale;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        float hueA = random.nextFloat();
        float hueB = hueA + (float) between(0.25, 0.55);
        float[] colorA = pastel(hueA);
        float[] colorB = pastel(hueB);
        double angle = between(0, Math.PI * 2);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        // 渐变轴上的投影范围，用于把投影归一到 [0,1]
        double[] corners = {0, w * cos, h * sin, w * cos + h * sin};
        double min = Math.min(Math.min(corners[0], corners[1]), Math.min(corners[2], corners[3]));
        double max = Math.max(Math.max(corners[0], corners[1]), Math.max(corners[2], corners[3]));
        double span = Math.max(1e-6, max - min);

        double[] blobX = new double[BLOBS];
        double[] blobY = new double[BLOBS];
        double[] blobR = new double[BLOBS];
        double[] blobS = new double[BLOBS];
        float[][] blobColor = new float[BLOBS][];
        double big = Math.max(w, h);
        for (int i = 0; i < BLOBS; i++) {
            blobX[i] = between(0, w);
            blobY[i] = between(0, h);
            blobR[i] = between(0.35, 0.75) * big;
            blobS[i] = between(0.45, 0.85);
            blobColor[i] = pastel(random.nextFloat());
        }

        float[] rgb = new float[3];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                double t = ((x * cos + y * sin) - min) / span;
                for (int c = 0; c < 3; c++) {
                    rgb[c] = (float) (colorA[c] + (colorB[c] - colorA[c]) * t);
                }
                for (int i = 0; i < BLOBS; i++) {
                    double dx = x - blobX[i];
                    double dy = y - blobY[i];
                    double d = Math.sqrt(dx * dx + dy * dy) / blobR[i];
                    if (d >= 1) {
                        continue;
                    }
                    double wgt = blobS[i] * (1 - d) * (1 - d);
                    for (int c = 0; c < 3; c++) {
                        rgb[c] = (float) (rgb[c] + (blobColor[i][c] - rgb[c]) * wgt);
                    }
                }
                pixels[row + x] = (clamp(rgb[0]) << 16) | (clamp(rgb[1]) << 8) | clamp(rgb[2]);
            }
        }
        return image;
    }

    /**
     * 从干净背景按形状裁出拼图，描一圈浅色边
     */
    private BufferedImage cutPiece(BufferedImage clean, Piece shape, int x, int y) {
        int w = shape.width * scale;
        int h = shape.height * scale;
        BufferedImage piece = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = piece.createGraphics();
        try {
            hints(g);
            Shape local = shape.at(0, 0, scale);
            g.setClip(local);
            g.drawImage(clean, -x * scale, -y * scale, null);
            g.setClip(null);
            g.setStroke(new BasicStroke(1.5f * scale));
            g.setColor(new Color(255, 255, 255, 170));
            g.draw(local);
        } finally {
            g.dispose();
        }
        return piece;
    }

    /**
     * 在背景上挖缺口（压暗 + 浅色描边）并画假缺口（只压暗）
     */
    private BufferedImage carve(BufferedImage clean, Piece shape, int x, int y) {
        Graphics2D g = clean.createGraphics();
        try {
            hints(g);
            // 假缺口：与真缺口水平错开至少一个拼图宽，纵向随机
            int decoyX = decoyX(shape, x);
            int decoyY = between(MARGIN, height - shape.height - MARGIN);
            Piece decoy = new Piece(pieceSize, (int) Math.round(pieceSize * TAB_RATIO),
                    random.nextBoolean(), random.nextBoolean());
            g.setColor(new Color(0, 0, 0, 70));
            g.fill(decoy.at(decoyX, decoyY, scale));

            Shape gap = shape.at(x, y, scale);
            g.setColor(new Color(0, 0, 0, 120));
            g.fill(gap);
            g.setStroke(new BasicStroke(1.5f * scale));
            g.setColor(new Color(255, 255, 255, 110));
            g.draw(gap);
        } finally {
            g.dispose();
        }
        return clean;
    }

    private int decoyX(Piece shape, int x) {
        int gapWidth = shape.width + MARGIN;
        int leftMax = x - gapWidth - shape.width;
        int rightMin = x + gapWidth;
        int rightMax = width - shape.width - MARGIN;
        boolean leftRoom = leftMax >= MARGIN;
        boolean rightRoom = rightMin <= rightMax;
        if (leftRoom && (!rightRoom || random.nextBoolean())) {
            return between(MARGIN, leftMax);
        }
        if (rightRoom) {
            return between(rightMin, rightMax);
        }
        // 画布过窄放不下假缺口：与真缺口重叠也无妨，仅是装饰
        return MARGIN;
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /**
     * 柔和色：中等饱和度、高亮度
     */
    private float[] pastel(float hue) {
        Color color = Color.getHSBColor(hue - (float) Math.floor(hue), (float) between(0.40, 0.70),
                (float) between(0.85, 1.0));
        return new float[]{color.getRed(), color.getGreen(), color.getBlue()};
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    private int between(int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) {
            return minInclusive;
        }
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    private double between(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }
}
