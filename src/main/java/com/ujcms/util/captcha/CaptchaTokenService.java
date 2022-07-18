package com.ujcms.util.captcha;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.primitives.Longs;
import com.octo.captcha.image.ImageCaptcha;
import com.ujcms.util.security.jwt.HmacSm3Algorithm;
import com.ujcms.util.web.Strings;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ENGLISH;

/**
 * 使用 Token 方式实现验证码。由于 RESTful 是无状态的，无法使用 Session 记住验证码。
 * <p>
 * 可以将验证码写入数据库，这种方式显得太重了。这里使用自验证的方式，不依赖数据库，甚至在分布式系统也适用。
 * <p>
 * 使用缓存记住已经使用的验证码，防止重复使用。
 *
 * @author liufang
 */
public class CaptchaTokenService {
    public CaptchaTokenService(GmailCaptchaEngine gmailCaptchaEngine, CaptchaCache cache, CaptchaProperties properties) {
        this.gmailCaptchaEngine = gmailCaptchaEngine;
        this.cache = cache;
        this.properties = properties;

        this.algorithm = new HmacSm3Algorithm(properties.getSecret());
        this.verifier = JWT.require(algorithm).withIssuer(properties.getIssuer())
                .acceptLeeway(properties.getLeeway()).build();
    }

    /**
     * base64 格式的图片使用方式：{@code <img src="data:image/png;base64,<base64 code>">}
     *
     * @return CaptchaToken
     */
    public CaptchaToken getCaptchaToken() {
        ImageCaptcha captcha = gmailCaptchaEngine.getNextImageCaptcha();
        BufferedImage bufferedImage = captcha.getImageChallenge();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(bufferedImage, "png", outputStream);
            String image = Strings.encodeBase64(outputStream.toByteArray());
            // 区分大小写
            String word = captcha.getResponse();
            Date now = new Date();
            // 只取秒级，不要毫秒。JWT 的 expiresAt 只能记录到秒
            Date expiresAt = new Date(now.getTime() / 1000 * 1000 + properties.getExpires() * 60 * 1000);
            String subject = sign(word, expiresAt);
            String token = JWT.create().withIssuer(properties.getIssuer())
                    .withIssuedAt(now).withExpiresAt(expiresAt).withSubject(subject).sign(algorithm);
            return new CaptchaToken(token, properties.getExpires() * 60, image);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 用于前台检查验证码是否正确，提高用户体验，方便。尝试次数限制一般为 5 次。
     *
     * @param token   captcha token
     * @param captcha 验证码
     * @return 是否验证成功
     */
    public boolean tryCaptcha(String token, String captcha) {
        if (StringUtils.isBlank(token) || StringUtils.isBlank(captcha)) {
            return false;
        }
        try {
            DecodedJWT jwt = verifier.verify(token);
            int wordLength = StringUtils.length(captcha);
            if (wordLength < properties.getWordLength() || wordLength > properties.getWordLength()) {
                return false;
            }
            int count = cache.getAttempts(jwt.getSignature());
            // count == -1 代表已经被使用过
            if (count < 0 || count >= properties.getMaxAttempts()) {
                return false;
            }
            // 区分大小写
            if (verifySubject(jwt.getSubject(), captcha, jwt.getExpiresAt())) {
                return true;
            }
            cache.updateAttempts(jwt.getSignature(), count + 1);
            return false;
        } catch (JWTVerificationException e) {
            // 验证失败
            return false;
        }
    }

    /**
     * 检查验证码是否正确，无论正确与否，该验证码立即失效，不可以再次使用。
     *
     * @param token   captcha token
     * @param captcha 验证码
     * @return 是否验证成功
     */
    public boolean validateCaptcha(@Nullable String token, @Nullable String captcha) {
        if (StringUtils.isBlank(token) || StringUtils.isBlank(captcha)) {
            return false;
        }
        try {
            DecodedJWT jwt = verifier.verify(token);
            int count = cache.getAttempts(jwt.getSignature());
            // 已使用或者尝试过多，直接返回false
            boolean isExcessiveAttempts = count < 0 || count >= properties.getMaxAttempts();
            if (isExcessiveAttempts) {
                return false;
            }
            // count == -1 代表已经被使用过
            cache.updateAttempts(jwt.getSignature(), -1);
            return verifySubject(jwt.getSubject(), captcha, jwt.getExpiresAt());
        } catch (JWTVerificationException e) {
            // 验证失败
            return false;
        }
    }

    private boolean verifySubject(String subject, String word, Date expiresAt) {
        return sign(word, expiresAt).equals(subject);
    }

    private String sign(String word, Date expiresAt) {
        // 不区分大小写
        byte[] subjectBytes = algorithm.sign(word.toUpperCase(ENGLISH).getBytes(UTF_8), Longs.toByteArray(expiresAt.getTime()));
        return Strings.encodeUrlBase64(subjectBytes);
    }

    private GmailCaptchaEngine gmailCaptchaEngine;
    private CaptchaCache cache;
    private CaptchaProperties properties;

    private Algorithm algorithm;
    private JWTVerifier verifier;
}
