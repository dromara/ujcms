package com.ujcms.common.captcha;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 图形验证码渲染器。纯几何绘制：字形来自内置的笔画表 {@link CaptchaGlyphs}，经 {@link Graphics2D} 描边填充后编码为图片，
 * 不加载字体文件、不触碰 JDK 字体子系统，因此在没有 fontconfig 的精简容器中同样可用。
 * <p>
 * 生成流程：从字母表随机取字 → 每个字形的控制点逐点随机扰动（同一字符每次形状都不同）→ 随机缩放、旋转、倾斜、
 * 上下抖动并沿一条随机弧线排布 → 以随机粗细的圆头笔画描边 → 每个字形向左滑动直到笔画与已排好的墨迹相交，再咬入一小段，
 * 保证相邻字符笔画相连、整词是一个连通的墨迹（孤立字符最容易被机器切分识别）→ 整词超出画布时等比缩小居中
 * → 整图按正弦波在横、纵两个方向扭曲。不加噪点、噪线：它们对人的干扰远大于对机器的干扰。
 * <p>
 * 答案由 {@link CaptchaTokenService} 签入令牌，校验时不区分大小写。字母表默认排除易混淆的字符
 * （0 / O、1 / I、5 / S、8 / B、Q、V、Z 等）。所有尺寸参数都来自 {@link CaptchaProperties}。
 *
 * @author PONY
 */
public final class CaptchaRenderer {
    /**
     * 一张验证码：答案与图片。
     *
     * @param word  答案
     * @param image 图片
     */
    public record CaptchaImage(String word, BufferedImage image) {
    }

    /**
     * 默认字母表。数字重复一次，使数字出现的概率与字母相当。
     */
    public static final String DEFAULT_ALPHABET = "ABCDEGHJKLMNRSTUWXY23692369";

    /**
     * 字形坐标空间的边长（见 {@link CaptchaGlyphs}）
     */
    private static final double GRID = 100.0;
    /**
     * 控制点扰动幅度（相对字高）
     */
    private static final double POINT_JITTER = 0.035;
    /**
     * 字形单轴缩放范围
     */
    private static final double MIN_GLYPH_SCALE = 0.88;
    private static final double MAX_GLYPH_SCALE = 1.08;
    /**
     * 字形最大旋转角度（弧度）
     */
    private static final double MAX_ROTATION = Math.PI / 20;
    /**
     * 字形最大水平倾斜
     */
    private static final double MAX_SHEAR = 0.15;
    /**
     * 字形上下抖动幅度（相对字高）
     */
    private static final double BASELINE_JITTER = 0.06;
    /**
     * 整词基线弧线的最大幅度（相对字高）
     */
    private static final double ARC_AMPLITUDE = 0.08;
    /**
     * 笔画粗细范围（相对字高）
     */
    private static final double MIN_STROKE = 0.09;
    private static final double MAX_STROKE = 0.13;
    /**
     * 同一字形内各笔画粗细的相对波动
     */
    private static final double STROKE_VARIATION = 0.10;
    /**
     * 相邻字形接触后继续咬入的深度范围（相对字高），使笔画真正合并而不是只擦边
     */
    private static final double MIN_BITE = 0.04;
    private static final double MAX_BITE = 0.08;
    /**
     * 寻找接触点时的滑动步长（相对字高）：先按粗步长找到相交，再退回一步按细步长逼近
     */
    private static final double COARSE_STEP = 0.06;
    private static final double FINE_STEP = 0.015;
    /**
     * 最大滑动距离（相对字高）：超过后放弃寻找，按当前位置排布
     */
    private static final double MAX_SLIDE = 0.7;
    /**
     * 画布四周留白（相对图片高）
     */
    private static final double MARGIN_RATIO = 0.04;

    private final int width;
    private final int height;
    private final int fontSize;
    private final int wordLength;
    private final float distortion;
    private final int scale;
    private final String alphabet;
    private final Color textColor;
    private final Color background;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param width      图片逻辑宽（像素，显示尺寸）
     * @param height     图片逻辑高（像素，显示尺寸）
     * @param fontSize   字高（逻辑像素）。整词放不进画布时按比例缩小
     * @param wordLength 字符数量
     * @param distortion 扭曲程度（0 为不扭曲；20 左右时横向位移约 3 逻辑像素）
     * @param scale      像素密度倍数。实际图片尺寸为逻辑尺寸乘以该倍数，供高分屏显示清晰；1 为不放大
     * @param alphabet   字母表，每个字符都必须在 {@link CaptchaGlyphs} 中有定义
     * @param textColor  文字颜色
     * @param background 背景颜色
     */
    public CaptchaRenderer(int width, int height, int fontSize, int wordLength, float distortion, int scale,
                           String alphabet, Color textColor, Color background) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("captcha width and height must be positive");
        }
        if (fontSize <= 0) {
            throw new IllegalArgumentException("captcha font size must be positive");
        }
        if (wordLength <= 0) {
            throw new IllegalArgumentException("captcha word length must be positive");
        }
        if (scale <= 0) {
            throw new IllegalArgumentException("captcha scale must be positive");
        }
        if (alphabet == null || alphabet.isEmpty()) {
            throw new IllegalArgumentException("captcha alphabet must not be empty");
        }
        for (int i = 0; i < alphabet.length(); i++) {
            if (!CaptchaGlyphs.has(alphabet.charAt(i))) {
                throw new IllegalArgumentException("captcha alphabet contains a character without a glyph: '"
                        + alphabet.charAt(i) + "'");
            }
        }
        this.width = width;
        this.height = height;
        this.fontSize = fontSize;
        this.wordLength = wordLength;
        this.distortion = Math.max(0, distortion);
        this.scale = scale;
        this.alphabet = alphabet;
        this.textColor = textColor;
        this.background = background;
    }

    /**
     * 生成下一张验证码
     *
     * @return 答案与图片
     */
    public CaptchaImage next() {
        return render(randomWord());
    }

    /**
     * 渲染指定答案（供测试与样张生成使用；随机性只在形状上，答案不变）
     */
    CaptchaImage render(String word) {
        return new CaptchaImage(word, distort(paint(word)));
    }

    private String randomWord() {
        StringBuilder sb = new StringBuilder(wordLength);
        for (int i = 0; i < wordLength; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * 逐个字形布局并描边：先生成全部笔画形状，再按整词包围盒决定是否缩小，最后居中（带少量随机偏移）填充。
     */
    private BufferedImage paint(String word) {
        int w = width * scale;
        int h = height * scale;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(background);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            List<Shape> shapes = layout(word);
            Rectangle2D bounds = null;
            for (Shape shape : shapes) {
                Rectangle2D b = shape.getBounds2D();
                if (bounds == null) {
                    bounds = b;
                } else {
                    bounds.add(b);
                }
            }
            if (bounds == null) {
                return image;
            }
            // 留白要把后续扭曲的最大位移算进去，扭曲后仍不出界
            double marginX = Math.max(1, h * MARGIN_RATIO) + amplitudeX();
            double marginY = Math.max(1, h * MARGIN_RATIO) + amplitudeY();
            double availableW = w - 2 * marginX;
            double availableH = h - 2 * marginY;
            double fit = Math.min(1.0, Math.min(availableW / bounds.getWidth(), availableH / bounds.getHeight()));
            double slackX = Math.max(0, availableW - bounds.getWidth() * fit) / 2;
            double slackY = Math.max(0, availableH - bounds.getHeight() * fit) / 2;
            AffineTransform place = new AffineTransform();
            place.translate(w / 2.0 + between(-slackX, slackX) * 0.6, h / 2.0 + between(-slackY, slackY) * 0.6);
            place.scale(fit, fit);
            place.translate(-bounds.getCenterX(), -bounds.getCenterY());

            g.setColor(textColor);
            for (Shape shape : shapes) {
                g.fill(place.createTransformedShape(shape));
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * 以字高 em（实际像素）排布整词：返回每条笔画描边后的形状，坐标为未居中的画布坐标。
     * 第一个字形放在原点；之后的每个字形先在前一字形右侧放好，再向左滑动直到与已排好的墨迹相交，然后再咬入一段。
     */
    private List<Shape> layout(String word) {
        double em = (double) fontSize * scale;
        double unit = em / GRID;
        double jitter = POINT_JITTER * GRID;
        double arcAmplitude = between(0, ARC_AMPLITUDE) * em;
        double arcPhase = between(0, Math.PI * 2);
        double arcPeriod = between(1.5, 3.0) * word.length();

        List<Shape> shapes = new ArrayList<>();
        Area previous = null;
        double rightEdge = 0;
        for (int i = 0; i < word.length(); i++) {
            CaptchaGlyphs.Glyph glyph = CaptchaGlyphs.get(word.charAt(i));
            if (glyph == null) {
                throw new IllegalStateException("no glyph for '" + word.charAt(i) + "'");
            }
            double scaleX = between(MIN_GLYPH_SCALE, MAX_GLYPH_SCALE);
            double scaleY = between(MIN_GLYPH_SCALE, MAX_GLYPH_SCALE);
            double advance = glyph.advance() * unit * scaleX;
            double baseline = em + between(-BASELINE_JITTER, BASELINE_JITTER) * em
                    + arcAmplitude * Math.sin(Math.PI * 2 * i / arcPeriod + arcPhase);

            // 字形坐标 → 画布坐标：围绕字形中心缩放、旋转、倾斜，再放到笔位
            double cx = glyph.advance() / 2;
            double cy = GRID / 2;
            AffineTransform transform = new AffineTransform();
            transform.translate(advance / 2, baseline - (GRID - cy) * unit * scaleY);
            transform.rotate(between(-MAX_ROTATION, MAX_ROTATION));
            transform.shear(between(-MAX_SHEAR, MAX_SHEAR), 0);
            transform.scale(unit * scaleX, unit * scaleY);
            transform.translate(-cx, -cy);

            double strokeWidth = between(MIN_STROKE, MAX_STROKE) * em;
            List<Shape> glyphShapes = new ArrayList<>(glyph.strokes().size());
            Area area = new Area();
            for (CaptchaGlyphs.Stroke stroke : glyph.strokes()) {
                Path2D.Double path = toPath(stroke, jitter);
                float lineWidth = (float) (strokeWidth * (1 + between(-STROKE_VARIATION, STROKE_VARIATION)));
                BasicStroke pen = new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
                Shape shape = pen.createStrokedShape(transform.createTransformedShape(path));
                glyphShapes.add(shape);
                area.add(new Area(shape));
            }

            double offset = previous == null ? 0 : slideIntoContact(previous, rightEdge, area, em);
            AffineTransform shift = AffineTransform.getTranslateInstance(offset, 0);
            for (Shape shape : glyphShapes) {
                shapes.add(shift.createTransformedShape(shape));
            }
            area.transform(shift);
            previous = area;
            rightEdge = Math.max(rightEdge, area.getBounds2D().getMaxX());
        }
        return shapes;
    }

    /**
     * 求字形的水平位移：从刚好不与已排墨迹重叠的位置开始向左滑动，直到与前一字形的笔画相交，再随机咬入一段。
     * 先按粗步长找到相交位置，退回一步后按细步长逼近。滑动超过 {@link #MAX_SLIDE} 仍未相交（极少见）则停在当前位置。
     */
    private double slideIntoContact(Area previous, double rightEdge, Area glyph, double em) {
        double x = rightEdge - glyph.getBounds2D().getMinX() + scale;
        double coarse = Math.max(1, COARSE_STEP * em);
        double fine = Math.max(1, FINE_STEP * em);
        double limit = x - MAX_SLIDE * em;
        while (x > limit && !touches(previous, glyph, x)) {
            x -= coarse;
        }
        double back = x + coarse;
        while (back - fine > x && !touches(previous, glyph, back - fine)) {
            back -= fine;
        }
        return Math.max(x, back - fine) - between(MIN_BITE, MAX_BITE) * em;
    }

    private static boolean touches(Area previous, Area glyph, double offset) {
        Area moved = new Area(glyph);
        moved.transform(AffineTransform.getTranslateInstance(offset, 0));
        moved.intersect(previous);
        return !moved.isEmpty();
    }

    /**
     * 笔画 → 路径，每个控制点（含起点）独立随机扰动。
     */
    private Path2D.Double toPath(CaptchaGlyphs.Stroke stroke, double jitter) {
        float[] src = stroke.points();
        double[] p = new double[src.length];
        for (int k = 0; k < src.length; k++) {
            p[k] = src[k] + between(-jitter, jitter);
        }
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0], p[1]);
        int idx = 2;
        for (byte segment : stroke.segments()) {
            if (segment == 1) {
                path.lineTo(p[idx], p[idx + 1]);
                idx += 2;
            } else {
                path.quadTo(p[idx], p[idx + 1], p[idx + 2], p[idx + 3]);
                idx += 4;
            }
        }
        return path;
    }

    private double amplitudeX() {
        return distortion * 0.15 * scale;
    }

    private double amplitudeY() {
        return distortion * 0.08 * scale;
    }

    /**
     * 正弦波扭曲：目标像素 (x, y) 取自源图 (x + a·sin(2πy/p1 + φ1), y + b·sin(2πx/p2 + φ2))，双线性采样，越界取背景色。
     * 波长与相位每张图随机，使同一答案不会得到相同的图。两个位移只各依赖一个坐标轴，先按行、列制表，再直接操作像素数组。
     */
    private BufferedImage distort(BufferedImage source) {
        if (distortion <= 0) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        double amplitudeX = amplitudeX();
        double amplitudeY = amplitudeY();
        double periodX = h * between(0.8, 1.6);
        double periodY = w * between(0.5, 1.0);
        double phaseX = between(0, Math.PI * 2);
        double phaseY = between(0, Math.PI * 2);
        double[] dx = new double[h];
        for (int y = 0; y < h; y++) {
            dx[y] = amplitudeX * Math.sin(Math.PI * 2 * y / periodX + phaseX);
        }
        double[] dy = new double[w];
        for (int x = 0; x < w; x++) {
            dy[x] = amplitudeY * Math.sin(Math.PI * 2 * x / periodY + phaseY);
        }

        int[] src = ((DataBufferInt) source.getRaster().getDataBuffer()).getData();
        BufferedImage target = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] dst = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
        int outside = background.getRGB() & 0xFFFFFF;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                dst[row + x] = sample(src, w, h, x + dx[y], y + dy[x], outside);
            }
        }
        return target;
    }

    /**
     * 双线性采样
     */
    private static int sample(int[] pixels, int w, int h, double x, double y, int outside) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;
        int c00 = pixel(pixels, w, h, x0, y0, outside);
        int c10 = pixel(pixels, w, h, x0 + 1, y0, outside);
        int c01 = pixel(pixels, w, h, x0, y0 + 1, outside);
        int c11 = pixel(pixels, w, h, x0 + 1, y0 + 1, outside);
        if (c00 == c10 && c00 == c01 && c00 == c11) {
            return c00;
        }
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double top = channel(c00, shift) * (1 - fx) + channel(c10, shift) * fx;
            double bottom = channel(c01, shift) * (1 - fx) + channel(c11, shift) * fx;
            int value = (int) Math.round(top * (1 - fy) + bottom * fy);
            rgb |= Math.min(255, Math.max(0, value)) << shift;
        }
        return rgb;
    }

    private static int pixel(int[] pixels, int w, int h, int x, int y, int outside) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return outside;
        }
        return pixels[y * w + x] & 0xFFFFFF;
    }

    private static int channel(int rgb, int shift) {
        return (rgb >> shift) & 0xFF;
    }

    private double between(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }
}
