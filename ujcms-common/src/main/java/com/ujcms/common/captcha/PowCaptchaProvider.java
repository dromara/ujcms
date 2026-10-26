package com.ujcms.common.captcha;

import com.ujcms.common.security.Secures;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 工作量证明（静默验证）：浏览器找一个 nonce，使 {@code SHA-256(salt + ":" + nonce)} 的前 {@code difficulty} 位为零。
 * 用户无感，对批量脚本是按次计费的 CPU 成本；不是“证明是人”，而是“证明花了钱”。
 * <p>
 * 难度为前导零比特数：16 位约 6.5 万次哈希，18 位约 26 万次（浏览器内纯 JS 实现约零点几秒），上限 24 位。
 * 难度来自 {@link IntSupplier}，由全局设置决定，修改即生效。
 * <p>
 * 声明：{@code salt}、{@code d}。载荷：{@code salt}、{@code difficulty}。答案：nonce（字母数字，至多 {@value #MAX_NONCE_LENGTH} 位）。
 *
 * @author PONY
 */
public final class PowCaptchaProvider implements CaptchaProvider {
    public static final String TYPE = "pow";
    public static final int MIN_DIFFICULTY = 10;
    public static final int MAX_DIFFICULTY = 24;
    public static final int MAX_NONCE_LENGTH = 32;
    static final String CLAIM_SALT = "salt";
    static final String CLAIM_DIFFICULTY = "d";
    private static final Pattern NONCE = Pattern.compile("[0-9A-Za-z]{1," + MAX_NONCE_LENGTH + "}");

    private volatile IntSupplier difficulty;

    public PowCaptchaProvider(IntSupplier difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * 替换难度来源（接入全局设置时由 cms 层调用）
     */
    public void setDifficulty(IntSupplier difficulty) {
        this.difficulty = difficulty;
    }

    public int difficulty() {
        return clamp(difficulty.getAsInt());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean visible() {
        return false;
    }

    @Override
    public CaptchaChallenge challenge() {
        String salt = Secures.randomLowerCaseAlphanumeric(16);
        int d = difficulty();
        return new CaptchaChallenge(Map.of(CLAIM_SALT, salt, CLAIM_DIFFICULTY, d),
                Map.of("salt", salt, "difficulty", d));
    }

    @Override
    public boolean verify(Map<String, Object> claims, String answer) {
        if (!(claims.get(CLAIM_SALT) instanceof String salt) || !(claims.get(CLAIM_DIFFICULTY) instanceof Number d)) {
            return false;
        }
        if (!NONCE.matcher(answer).matches()) {
            return false;
        }
        return leadingZeroBits(sha256(salt + ':' + answer)) >= clamp(d.intValue());
    }

    static int clamp(int difficulty) {
        return Math.max(MIN_DIFFICULTY, Math.min(MAX_DIFFICULTY, difficulty));
    }

    static byte[] sha256(String data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data.getBytes(UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    static int leadingZeroBits(byte[] digest) {
        int zeros = 0;
        for (byte b : digest) {
            int value = b & 0xFF;
            if (value == 0) {
                zeros += 8;
                continue;
            }
            zeros += Integer.numberOfLeadingZeros(value) - 24;
            break;
        }
        return zeros;
    }
}
