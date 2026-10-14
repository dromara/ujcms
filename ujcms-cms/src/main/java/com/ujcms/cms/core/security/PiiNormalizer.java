package com.ujcms.cms.core.security;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * 用户 PII（手机号 / 邮箱）的规范化工具。
 * <p>
 * 盲索引（blind index）的正确性依赖<strong>写入与查询时使用完全相同的规范化</strong>：
 * 只有把同一份逻辑值映射为同一份规范字符串，确定性散列才能匹配。因此手机号 / 邮箱
 * 入库（{@code UserService} 计算索引）与按值查询（{@code selectByMobile/selectByEmail}）
 * 都必须经过本类。
 * <p>
 * 规则：
 * <ul>
 *   <li>邮箱：去除首尾空白后转小写（{@link Locale#ROOT}，避免土耳其语 I 等本地化问题）。</li>
 *   <li>手机号：剔除空白与常见分隔符（空格、{@code - ( ) . /}），保留数字与 {@code +}（国家码）。</li>
 *   <li>空白 / {@code null} 一律返回 {@code null}（不对空串散列），以便索引列存 {@code NULL}，
 *       从而在 UNIQUE 约束下允许大量无手机号 / 邮箱的用户并存。</li>
 * </ul>
 *
 * @author PONY
 */
public final class PiiNormalizer {
    /** 手机号需剔除的分隔符：空白、连字符、圆括号、点、斜杠。 */
    private static final String MOBILE_SEPARATORS = "[\\s()\\-./]+";

    /**
     * 规范化邮箱：去首尾空白 + 转小写。空白返回 {@code null}。
     */
    @Nullable
    public static String normalizeEmail(@Nullable String email) {
        if (StringUtils.isBlank(email)) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 规范化手机号：剔除空白与常见分隔符，保留数字与 {@code +}。剔除后为空返回 {@code null}。
     */
    @Nullable
    public static String normalizeMobile(@Nullable String mobile) {
        if (StringUtils.isBlank(mobile)) {
            return null;
        }
        String stripped = mobile.replaceAll(MOBILE_SEPARATORS, "");
        return StringUtils.isBlank(stripped) ? null : stripped;
    }

    private PiiNormalizer() {
        throw new IllegalStateException("Utility class");
    }
}
