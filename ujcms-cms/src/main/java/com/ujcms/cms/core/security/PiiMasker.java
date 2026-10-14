package com.ujcms.cms.core.security;

import org.jspecify.annotations.Nullable;

/**
 * 用户 PII（手机号）的脱敏工具。
 * <p>
 * 用于向用户提示“验证码已发送至哪个号码”：只露出足以让本人辨认的首尾几位，既能让用户发现登记的号码有误，
 * 又不向他人泄露完整号码。脱敏前先经 {@link PiiNormalizer} 规范化，避免分隔符位置影响保留的位数。
 *
 * @author PONY
 */
public final class PiiMasker {
    /** 保留首 3 位、尾 4 位所需的最小长度。11 位大陆手机号脱敏为 {@code 138****1234}。 */
    private static final int FULL_MASK_MIN_LENGTH = 8;
    private static final int PREFIX_LENGTH = 3;
    private static final int SUFFIX_LENGTH = 4;
    /** 号码较短时只保留尾 2 位所需的最小长度。更短的号码全部遮盖。 */
    private static final int SHORT_MASK_MIN_LENGTH = 5;
    private static final int SHORT_SUFFIX_LENGTH = 2;
    private static final char MASK = '*';

    /**
     * 手机号脱敏。
     * <ul>
     *   <li>8 位及以上：保留首 3 位、尾 4 位，如 {@code 138****1234}、{@code +86*******8000}。</li>
     *   <li>5 ~ 7 位：只保留尾 2 位。</li>
     *   <li>4 位及以下：全部遮盖。</li>
     * </ul>
     *
     * @return 脱敏后的号码。空白 / {@code null} 返回空串
     */
    public static String maskMobile(@Nullable String mobile) {
        String normalized = PiiNormalizer.normalizeMobile(mobile);
        if (normalized == null) {
            return "";
        }
        int length = normalized.length();
        if (length >= FULL_MASK_MIN_LENGTH) {
            return normalized.substring(0, PREFIX_LENGTH)
                    + String.valueOf(MASK).repeat(length - PREFIX_LENGTH - SUFFIX_LENGTH)
                    + normalized.substring(length - SUFFIX_LENGTH);
        }
        if (length >= SHORT_MASK_MIN_LENGTH) {
            return String.valueOf(MASK).repeat(length - SHORT_SUFFIX_LENGTH)
                    + normalized.substring(length - SHORT_SUFFIX_LENGTH);
        }
        return String.valueOf(MASK).repeat(length);
    }

    private PiiMasker() {
        throw new IllegalStateException("Utility class");
    }
}
