package com.ujcms.common.captcha;

import com.ujcms.common.security.Secures;
import com.ujcms.common.web.Strings;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static java.util.Locale.ENGLISH;

/**
 * 字符图片验证码：{@link CaptchaRenderer} 出图，答案不区分大小写。始终可取（{@code ?type=char}），是无障碍回退。
 * <p>
 * 声明：{@code sub} = HMAC(大写答案 | 随机数)，{@code n} = 随机数。载荷：{@code image}（PNG Base64）。
 *
 * @author PONY
 */
public final class CharCaptchaProvider implements CaptchaProvider {
    public static final String TYPE = "char";
    static final String CLAIM_SUBJECT = "sub";
    static final String CLAIM_NONCE = "n";

    private final CaptchaRenderer renderer;
    private final CaptchaCrypto crypto;
    private final int wordLength;

    public CharCaptchaProvider(CaptchaRenderer renderer, CaptchaCrypto crypto, int wordLength) {
        this.renderer = renderer;
        this.crypto = crypto;
        this.wordLength = wordLength;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public CaptchaChallenge challenge() {
        CaptchaRenderer.CaptchaImage captcha = renderer.next();
        String nonce = Secures.randomAlphanumeric(16);
        return new CaptchaChallenge(
                Map.of(CLAIM_SUBJECT, crypto.hmac(subject(captcha.word(), nonce)), CLAIM_NONCE, nonce),
                Map.of("image", encodePng(captcha)));
    }

    @Override
    public boolean verify(Map<String, Object> claims, String answer) {
        if (answer.length() != wordLength || !(claims.get(CLAIM_NONCE) instanceof String nonce)) {
            return false;
        }
        return crypto.hmacEquals(subject(answer, nonce), claims.get(CLAIM_SUBJECT));
    }

    private static String subject(String word, String nonce) {
        // 不区分大小写
        return word.toUpperCase(ENGLISH) + '|' + nonce;
    }

    private static String encodePng(CaptchaRenderer.CaptchaImage captcha) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(captcha.image(), "png", outputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot encode captcha image", e);
        }
        return Strings.encodeBase64(outputStream.toByteArray());
    }
}
