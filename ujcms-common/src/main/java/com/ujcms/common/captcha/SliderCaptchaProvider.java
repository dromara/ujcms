package com.ujcms.common.captcha;

import com.ujcms.common.web.Strings;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 滑块拼图验证码：{@link SliderRenderer} 出图，答案为拼图外接框的左边距（逻辑像素），允许 ±tolerance 的误差。
 * <p>
 * 期望值需要做数值比较，故以 SM4-GCM 密文写入声明 {@code ans}（哈希无法做容差比较）。
 * 载荷：{@code image}（带缺口的背景）、{@code piece}（拼图）、{@code width}、{@code height}、
 * {@code pieceWidth}、{@code pieceHeight}、{@code y}。
 *
 * @author PONY
 */
public final class SliderCaptchaProvider implements CaptchaProvider {
    public static final String TYPE = "slider";
    static final String CLAIM_ANSWER = "ans";

    private final SliderRenderer renderer;
    private final CaptchaCrypto crypto;
    private final int width;
    private final int height;
    private final int tolerance;

    /**
     * @param renderer  渲染器
     * @param crypto    答案加密
     * @param width     背景逻辑宽（与渲染器一致，用于载荷与答案范围）
     * @param height    背景逻辑高
     * @param tolerance 允许误差（逻辑像素）
     */
    public SliderCaptchaProvider(SliderRenderer renderer, CaptchaCrypto crypto, int width, int height, int tolerance) {
        this.renderer = renderer;
        this.crypto = crypto;
        this.width = width;
        this.height = height;
        this.tolerance = Math.max(0, tolerance);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public CaptchaChallenge challenge() {
        SliderRenderer.SliderImage image = renderer.next();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("image", encodePng(image.background()));
        payload.put("piece", encodePng(image.piece()));
        payload.put("width", width);
        payload.put("height", height);
        payload.put("pieceWidth", image.pieceWidth());
        payload.put("pieceHeight", image.pieceHeight());
        payload.put("y", image.y());
        return new CaptchaChallenge(Map.of(CLAIM_ANSWER, crypto.encrypt(String.valueOf(image.x()))), payload);
    }

    @Override
    public boolean verify(Map<String, Object> claims, String answer) {
        int given = parse(answer);
        if (given < 0 || given > width) {
            return false;
        }
        String expected = crypto.decrypt(claims.get(CLAIM_ANSWER));
        if (expected == null) {
            return false;
        }
        int x = parse(expected);
        return x >= 0 && Math.abs(given - x) <= tolerance;
    }

    /**
     * 整数或小数（浏览器按渲染尺寸折算后可能带小数），四舍五入；非法返回 -1
     */
    private static int parse(String value) {
        if (value.length() > 12) {
            return -1;
        }
        try {
            double d = Double.parseDouble(value);
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return -1;
            }
            return (int) Math.round(d);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String encodePng(BufferedImage image) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", outputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot encode slider image", e);
        }
        return Strings.encodeBase64(outputStream.toByteArray());
    }
}
