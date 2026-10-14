package com.ujcms.cms.core.security;

import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.OperationLog;
import com.ujcms.cms.core.security.spi.LocalCryptoKeyProvider;
import com.ujcms.cms.core.support.StaticProps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证审计签名：确定性、64 位十六进制、verify 通过；翻转任一字段 / 篡改签名 / 未签名 → verify 失败；依赖密钥。
 */
class AuditSignatureTest {
    private final AuditSignature auditSignature = new AuditSignature(new LocalCryptoKeyProvider());

    @BeforeEach
    void setKey() {
        StaticProps.setDataSecretKey("0123456789abcdef");
    }

    @AfterEach
    void restoreKey() {
        StaticProps.setDataSecretKey(StaticProps.DEFAULT_DATA_SECRET_KEY);
    }

    private OperationLog operationLog() {
        OperationLog log = new OperationLog();
        log.setId(1001L);
        log.setSiteId(1L);
        log.setUserId(2L);
        log.setName("user.create");
        log.setModule("user");
        log.setRequestMethod("POST");
        log.setIp("127.0.0.1");
        log.setCreated(OffsetDateTime.parse("2026-06-03T19:00:00+08:00"));
        log.setAudit(false);
        log.setType((short) 1);
        log.setStatus(OperationLog.STATUS_SUCCESS);
        return log;
    }

    private LoginLog loginLog() {
        LoginLog log = new LoginLog();
        log.setId(2001L);
        log.setUserId(2L);
        log.setLoginName("admin");
        log.setIp("127.0.0.1");
        log.setCreated(OffsetDateTime.parse("2026-06-03T19:00:00+08:00"));
        log.setType(LoginLog.TYPE_LOGIN);
        log.setStatus(LoginLog.STATUS_SUCCESS);
        return log;
    }

    @Test
    void signIsDeterministicHex64() {
        OperationLog log = operationLog();
        String sign = auditSignature.sign(log);
        assertEquals(sign, auditSignature.sign(log), "相同内容签名应一致");
        assertEquals(64, sign.length(), "HMAC-SM3 输出 32 字节 = 64 个十六进制字符");
        assertTrue(sign.matches("[0-9a-f]{64}"), "应为小写十六进制");
    }

    @Test
    void verifyPassesForSignedRow() {
        OperationLog log = operationLog();
        log.setSign(auditSignature.sign(log));
        assertTrue(auditSignature.verify(log));

        LoginLog login = loginLog();
        login.setSign(auditSignature.sign(login));
        assertTrue(auditSignature.verify(login));
    }

    @Test
    void verifyFailsWhenFieldTampered() {
        OperationLog log = operationLog();
        log.setSign(auditSignature.sign(log));
        // 翻转状态字段后签名不再匹配
        log.setStatus(OperationLog.STATUS_FAILURE);
        assertFalse(auditSignature.verify(log));

        LoginLog login = loginLog();
        login.setSign(auditSignature.sign(login));
        login.setUserId(999L);
        assertFalse(auditSignature.verify(login));
    }

    @Test
    void verifyFailsWhenCreatedTampered() {
        OperationLog log = operationLog();
        log.setSign(auditSignature.sign(log));
        // 篡改创建时间（回填/前移）→ 签名不再匹配（created_ 已纳入签名）
        log.setCreated(log.getCreated().plusHours(1));
        assertFalse(auditSignature.verify(log));

        LoginLog login = loginLog();
        login.setSign(auditSignature.sign(login));
        login.setCreated(login.getCreated().minusDays(1));
        assertFalse(auditSignature.verify(login));
    }

    @Test
    void verifyFailsWhenSignatureTampered() {
        OperationLog log = operationLog();
        log.setSign("0".repeat(64));
        assertFalse(auditSignature.verify(log));
    }

    @Test
    void verifyFailsWhenUnsigned() {
        OperationLog log = operationLog();
        // sign_ 为 null（历史行 / 未开启签名）→ verify 返回 false，由调用方区分“未签名”与“被篡改”
        assertFalse(auditSignature.verify(log));
    }

    /** 模拟 DATETIME（无小数秒）入库：MySQL 对小数秒四舍五入（半进位）到整秒。 */
    private static OffsetDateTime roundToSecond(OffsetDateTime t) {
        return t.getNano() >= 500_000_000 ? t.withNano(0).plusSeconds(1) : t.withNano(0);
    }

    @Test
    void verifyStableWhenCreatedTruncatedToSecond() {
        // 回归：created_ 为整秒列（DATETIME），带小数秒的值入库会被四舍五入到整秒。
        OffsetDateTime now = OffsetDateTime.parse("2026-06-03T19:00:00.700+08:00");

        // 修复：服务层签名前先将 created 截断到整秒 —— 签名值与入库值一致，整秒入库回读仍校验通过。
        LoginLog fixed = loginLog();
        fixed.setCreated(now.withNano(0));
        fixed.setSign(auditSignature.sign(fixed));
        fixed.setCreated(roundToSecond(fixed.getCreated()));
        assertTrue(auditSignature.verify(fixed), "截断到整秒后，整秒列入库回读应校验通过");

        // 反例（修复前）：对带小数秒的值签名，入库被四舍五入到下一秒 → 误报“被篡改”。
        LoginLog buggy = loginLog();
        buggy.setCreated(now);
        buggy.setSign(auditSignature.sign(buggy));
        buggy.setCreated(roundToSecond(now));
        assertFalse(auditSignature.verify(buggy), "未截断时，整秒列的四舍五入会导致误报被篡改");
    }

    @Test
    void signDependsOnKey() {
        OperationLog log = operationLog();
        String withKeyA = auditSignature.sign(log);
        StaticProps.setDataSecretKey("fedcba9876543210");
        String withKeyB = auditSignature.sign(log);
        assertNotEquals(withKeyA, withKeyB, "更换 data-secret-key 后同一行的签名应不同（密钥派生）");
    }
}
