package com.ujcms.cms.core.security;

import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.OperationLog;
import com.ujcms.common.security.spi.CryptoKeyProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 审计日志行级签名（HMAC-SM3，密评 日志完整性）。
 * <p>
 * 开启 {@code ujcms.security.audit-integrity} 后，操作日志 / 登录日志写入时对该行的关键字段
 * 计算 HMAC-SM3 签名存入 {@code sign_}；校验端重算比对，发现被篡改 / 未签名的行。密钥经
 * {@link CryptoKeyProvider} 派生（领域 {@code "audit"}，即 {@code SM3(dataSecretKey + ":audit")}），
 * 不引入新配置；与 {@code data-secret-key} 同生命周期——轮换该密钥会使历史签名失效。
 * <p>
 * 规范串<strong>包含 {@code created_}</strong>（以秒级 UTC 时间戳计入；写入时由服务层在 Java 设值后再签名），
 * 使创建时刻可独立校验，而非仅依赖 Snowflake {@code id_} 推断。采用绝对时刻（epoch 秒）而非墙钟字符串，
 * 使其在连接时区的不同表示下仍稳定——只要部署的时区配置自洽（即日志显示时间正确）即可正确校验。
 *
 * @author PONY
 * @see PiiBlindIndex
 */
@Component
public class AuditSignature {
    /** 密钥派生领域。 */
    private static final String DOMAIN = "audit";
    /** 规范串字段分隔符。 */
    private static final String SEP = "|";

    private final CryptoKeyProvider cryptoKeyProvider;

    public AuditSignature(CryptoKeyProvider cryptoKeyProvider) {
        this.cryptoKeyProvider = cryptoKeyProvider;
    }

    public String sign(OperationLog log) {
        return cryptoKeyProvider.hmacSm3Hex(DOMAIN, canonical(log));
    }

    public String sign(LoginLog log) {
        return cryptoKeyProvider.hmacSm3Hex(DOMAIN, canonical(log));
    }

    /** 校验签名是否匹配。未签名（{@code sign_} 为空）返回 {@code false}（由调用方区分“未签名”与“被篡改”）。 */
    public boolean verify(OperationLog log) {
        return log.getSign() != null && log.getSign().equals(sign(log));
    }

    public boolean verify(LoginLog log) {
        return log.getSign() != null && log.getSign().equals(sign(log));
    }

    private String canonical(OperationLog l) {
        return String.join(SEP, str(l.getId()), str(l.getSiteId()), str(l.getUserId()), str(l.getName()),
                str(l.getModule()), str(l.getRequestMethod()), str(l.getIp()), time(l.getCreated()),
                str(l.getAudit()), str(l.getType()), str(l.getStatus()));
    }

    private String canonical(LoginLog l) {
        return String.join(SEP, str(l.getId()), str(l.getUserId()), str(l.getLoginName()), str(l.getIp()),
                time(l.getCreated()), str(l.getType()), str(l.getStatus()));
    }

    private static String str(@Nullable Object value) {
        return value == null ? "" : value.toString();
    }

    /** 创建时刻以秒级 epoch（绝对时刻）计入，与连接时区表示无关；只要绝对时刻往返一致即稳定。 */
    private static String time(@Nullable OffsetDateTime created) {
        return created == null ? "" : Long.toString(created.toInstant().getEpochSecond());
    }
}
