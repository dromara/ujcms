package com.ujcms.cms.core.security;

import com.ujcms.cms.core.support.StaticProps;
import com.ujcms.common.security.Secures;
import com.ujcms.common.web.Strings;
import org.jspecify.annotations.Nullable;

/**
 * 用户 PII（手机号 / 邮箱）盲索引（blind index）计算。
 * <p>
 * 手机号 / 邮箱在库中以随机 IV 的 SM4-GCM 整列加密（{@link Sm4StringTypeHandler}），密文非确定性，
 * 无法用于等值查询。为同时保留<strong>强加密</strong>与<strong>按值精确查询 / 唯一性</strong>，
 * 额外维护一列确定性盲索引：{@code index = HMAC-SM3(indexKey, normalize(value))}（64 个十六进制字符）。
 * 它只泄露“相等性”（对近乎唯一的手机号 / 邮箱可忽略），不可逆推原值。
 * <p>
 * 索引密钥派生自 {@code ujcms.data-secret-key}（{@link StaticProps#getDataSecretKey()}）：
 * {@code indexKey = SM3(dataSecretKey + ":idx")}。不引入单独配置项；与加密值同源同生命周期——
 * <strong>轮换 {@code data-secret-key} 会同时使历史密文与索引失效</strong>，须重新回填（见维护端点）。
 * <p>
 * 规范化由 {@link PiiNormalizer} 负责，写入与查询必须一致。空白值返回 {@code null}
 * （索引列存 {@code NULL}，从而在 UNIQUE 约束下允许多行无值并存）。
 *
 * @author PONY
 * @see PiiNormalizer
 * @see Sm4StringTypeHandler
 */
public final class PiiBlindIndex {
    /** 索引密钥派生时附加的领域分隔串，避免与其它用途（如直接 SM3）的派生值重叠。 */
    private static final String KEY_DOMAIN_SUFFIX = ":idx";

    /**
     * 由 {@code data-secret-key} 派生的索引密钥（每次重算，不缓存——以便密钥在测试 / 运行期变更后即时生效）。
     */
    private static byte[] indexKey() {
        return Strings.toBytes(Secures.sm3Hex(StaticProps.getDataSecretKey() + KEY_DOMAIN_SUFFIX));
    }

    /**
     * 计算手机号盲索引。{@code null} / 空白返回 {@code null}。
     */
    @Nullable
    public static String mobileIndex(@Nullable String mobile) {
        return computeIndex(PiiNormalizer.normalizeMobile(mobile));
    }

    /**
     * 计算邮箱盲索引。{@code null} / 空白返回 {@code null}。
     */
    @Nullable
    public static String emailIndex(@Nullable String email) {
        return computeIndex(PiiNormalizer.normalizeEmail(email));
    }

    @Nullable
    private static String computeIndex(@Nullable String normalized) {
        if (normalized == null) {
            return null;
        }
        return Secures.hmacSm3Hex(indexKey(), Strings.toBytes(normalized));
    }

    private PiiBlindIndex() {
        throw new IllegalStateException("Utility class");
    }
}
