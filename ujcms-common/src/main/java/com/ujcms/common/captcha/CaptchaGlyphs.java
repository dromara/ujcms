package com.ujcms.common.captcha;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 验证码内置笔画字形表：A–Z、0–9 共 36 个字形，以中心线笔画描述，不依赖任何字体文件与系统字体组件（fontconfig）。
 * <p>
 * 坐标空间：100 单位 em 网格，y 轴向下，大写字高为 0，基线为 100，x 从 0 到字形的步进宽度（advance）。
 * 路径写法为 SVG {@code d} 的子集：{@code M x y}（起笔）、{@code L x y}（直线）、{@code Q cx cy x y}（二次曲线），
 * {@code |} 分隔同一字形内的多条笔画。笔画在渲染时以圆头粗线描边，并逐点随机扰动，因此同一字符每次绘制的形状都不同。
 *
 * @author PONY
 */
public final class CaptchaGlyphs {
    /**
     * 一条笔画。{@code points} 为扁平坐标数组（x0, y0, x1, y1, …），{@code segments} 记录每一段消耗的点数：
     * 1 为直线（终点），2 为二次曲线（控制点 + 终点）。
     */
    public record Stroke(float[] points, byte[] segments) {
        /**
         * 点的数量
         */
        public int pointCount() {
            return points.length / 2;
        }
    }

    /**
     * 一个字形：步进宽度与笔画列表
     */
    public record Glyph(float advance, List<Stroke> strokes) {
    }

    private static final Map<Character, Glyph> GLYPHS;

    static {
        Map<Character, Glyph> map = new HashMap<>();
        define(map, 'A', 72, "M 4 100 L 36 0 L 68 100 | M 16 64 L 56 64");
        define(map, 'B', 66, "M 8 0 L 8 100 | M 8 0 L 36 0 Q 62 0 62 24 Q 62 48 36 48 L 8 48 "
                + "| M 36 48 Q 66 48 66 74 Q 66 100 36 100 L 8 100");
        define(map, 'C', 66, "M 62 18 Q 50 -4 30 2 Q 2 10 2 50 Q 2 90 30 98 Q 50 104 62 82");
        define(map, 'D', 66, "M 8 0 L 8 100 | M 8 0 L 30 0 Q 64 0 64 50 Q 64 100 30 100 L 8 100");
        define(map, 'E', 58, "M 54 0 L 8 0 L 8 100 L 54 100 | M 8 50 L 44 50");
        define(map, 'F', 56, "M 52 0 L 8 0 L 8 100 | M 8 50 L 42 50");
        define(map, 'G', 70, "M 64 18 Q 52 -4 32 2 Q 2 10 2 50 Q 2 90 32 98 Q 60 102 64 76 L 64 56 L 38 56");
        define(map, 'H', 66, "M 8 0 L 8 100 | M 58 0 L 58 100 | M 8 52 L 58 52");
        define(map, 'I', 40, "M 20 0 L 20 100 | M 6 0 L 34 0 | M 6 100 L 34 100");
        define(map, 'J', 56, "M 48 0 L 48 72 Q 48 100 26 100 Q 6 100 4 76");
        define(map, 'K', 64, "M 8 0 L 8 100 | M 58 0 L 8 56 | M 24 44 L 60 100");
        define(map, 'L', 56, "M 8 0 L 8 100 L 54 100");
        define(map, 'M', 80, "M 6 100 L 6 0 L 40 84 L 74 0 L 74 100");
        define(map, 'N', 68, "M 8 100 L 8 0 L 60 100 L 60 0");
        define(map, 'O', 70, "M 35 0 Q 4 0 4 50 Q 4 100 35 100 Q 66 100 66 50 Q 66 0 35 0");
        define(map, 'P', 62, "M 8 100 L 8 0 L 36 0 Q 60 0 60 26 Q 60 52 36 52 L 8 52");
        define(map, 'Q', 70, "M 35 0 Q 4 0 4 50 Q 4 100 35 100 Q 66 100 66 50 Q 66 0 35 0 | M 44 76 L 68 104");
        define(map, 'R', 64, "M 8 100 L 8 0 L 36 0 Q 60 0 60 26 Q 60 50 36 50 L 8 50 | M 32 50 L 62 100");
        define(map, 'S', 62, "M 56 16 Q 48 -4 26 2 Q 4 10 10 32 Q 16 48 36 54 Q 58 60 58 80 Q 56 104 26 98 Q 10 96 4 82");
        define(map, 'T', 62, "M 4 0 L 58 0 | M 31 0 L 31 100");
        define(map, 'U', 66, "M 8 0 L 8 68 Q 8 100 33 100 Q 58 100 58 68 L 58 0");
        define(map, 'V', 70, "M 4 0 L 35 100 L 66 0");
        define(map, 'W', 90, "M 4 0 L 22 100 L 45 30 L 68 100 L 86 0");
        define(map, 'X', 66, "M 6 0 L 60 100 | M 60 0 L 6 100");
        define(map, 'Y', 66, "M 6 0 L 33 50 L 60 0 | M 33 50 L 33 100");
        define(map, 'Z', 62, "M 6 0 L 56 0 L 6 100 L 56 100");
        define(map, '0', 62, "M 31 0 Q 6 0 6 50 Q 6 100 31 100 Q 56 100 56 50 Q 56 0 31 0");
        define(map, '1', 44, "M 8 20 L 26 0 L 26 100");
        define(map, '2', 62, "M 8 24 Q 10 -2 32 2 Q 58 6 54 32 Q 50 54 8 100 L 58 100");
        define(map, '3', 62, "M 8 12 Q 20 -4 36 2 Q 58 8 54 28 Q 50 48 30 48 Q 58 48 58 74 Q 58 104 26 98 Q 10 96 4 82");
        define(map, '4', 66, "M 46 100 L 46 0 L 4 68 L 62 68");
        define(map, '5', 62, "M 54 0 L 14 0 L 10 46 Q 30 38 44 42 Q 60 50 58 72 Q 56 102 24 98 Q 8 96 4 84");
        define(map, '6', 62, "M 50 8 Q 40 -4 26 2 Q 4 14 4 60 Q 4 100 30 100 Q 58 100 58 72 Q 58 46 32 46 Q 10 46 4 66");
        define(map, '7', 60, "M 4 0 L 56 0 L 22 100");
        define(map, '8', 62, "M 31 48 Q 8 48 8 24 Q 8 0 31 0 Q 54 0 54 24 Q 54 48 31 48 "
                + "Q 4 48 4 74 Q 4 100 31 100 Q 58 100 58 74 Q 58 48 31 48");
        define(map, '9', 62, "M 12 92 Q 22 104 36 98 Q 58 86 58 40 Q 58 0 32 0 Q 4 0 4 28 Q 4 54 30 54 Q 52 54 58 34");
        GLYPHS = Collections.unmodifiableMap(map);
    }

    private CaptchaGlyphs() {
    }

    /**
     * 取字形。小写字母按大写查找。
     *
     * @param c 字符
     * @return 字形；没有定义则为 {@code null}
     */
    @Nullable
    public static Glyph get(char c) {
        return GLYPHS.get(Character.toUpperCase(c));
    }

    /**
     * 是否定义了该字符的字形
     */
    public static boolean has(char c) {
        return GLYPHS.containsKey(Character.toUpperCase(c));
    }

    /**
     * 已定义字形的字符集
     */
    public static String characters() {
        StringBuilder sb = new StringBuilder(GLYPHS.size());
        GLYPHS.keySet().stream().sorted().forEach(sb::append);
        return sb.toString();
    }

    private static void define(Map<Character, Glyph> map, char c, float advance, String path) {
        map.put(c, new Glyph(advance, parse(path)));
    }

    /**
     * 解析路径。只接受 {@code M} / {@code L} / {@code Q} 三种命令，命令与数字以空白分隔；格式错误抛出异常（类初始化时即暴露）。
     */
    static List<Stroke> parse(String path) {
        List<Stroke> strokes = new ArrayList<>();
        for (String part : path.split("\\|")) {
            String[] tokens = part.trim().split("\\s+");
            List<Float> points = new ArrayList<>();
            List<Byte> segments = new ArrayList<>();
            int i = 0;
            while (i < tokens.length) {
                String command = tokens[i++];
                switch (command) {
                    case "M" -> {
                        if (!points.isEmpty()) {
                            throw new IllegalArgumentException("'M' must start a stroke: " + path);
                        }
                        points.add(Float.parseFloat(tokens[i++]));
                        points.add(Float.parseFloat(tokens[i++]));
                    }
                    case "L" -> {
                        requireStarted(points, path);
                        points.add(Float.parseFloat(tokens[i++]));
                        points.add(Float.parseFloat(tokens[i++]));
                        segments.add((byte) 1);
                    }
                    case "Q" -> {
                        requireStarted(points, path);
                        for (int k = 0; k < 4; k++) {
                            points.add(Float.parseFloat(tokens[i++]));
                        }
                        segments.add((byte) 2);
                    }
                    default -> throw new IllegalArgumentException("unknown path command '" + command + "': " + path);
                }
            }
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("a stroke needs at least one segment: " + path);
            }
            float[] pts = new float[points.size()];
            for (int k = 0; k < pts.length; k++) {
                pts[k] = points.get(k);
            }
            byte[] segs = new byte[segments.size()];
            for (int k = 0; k < segs.length; k++) {
                segs[k] = segments.get(k);
            }
            strokes.add(new Stroke(pts, segs));
        }
        return Collections.unmodifiableList(strokes);
    }

    private static void requireStarted(List<Float> points, String path) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("a stroke must start with 'M': " + path);
        }
    }
}
