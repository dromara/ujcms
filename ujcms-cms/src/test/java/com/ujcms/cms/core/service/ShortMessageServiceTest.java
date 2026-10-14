package com.ujcms.cms.core.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.ujcms.cms.core.component.AliyunSmsService;
import com.ujcms.cms.core.component.TencentSmsService;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.mapper.ShortMessageMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;

/**
 * Verifies the verification-code rules that every consumer (register, reset password, change
 * mobile / email, two-factor login) relies on: a code is bound to the usage it was sent for, the
 * receiver is compared after normalization, and the number of checks per code is bounded by the
 * database — an attempt slot is claimed atomically <em>before</em> the code is compared, so parallel
 * guesses cannot exceed {@link ShortMessage#MAX_ATTEMPTS}.
 */
class ShortMessageServiceTest {
    private static final long ID = 1L;
    private static final String MOBILE = "13800138000";
    private static final String CODE = "123456";
    private static final int EXPIRES = 10;
    private static final int CODE_LENGTH = 6;

    private ShortMessageMapper mapper;
    private ShortMessageService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ShortMessageMapper.class);
        service = new ShortMessageService(mapper, mock(SnowflakeSequence.class), mock(TencentSmsService.class),
                mock(AliyunSmsService.class));
    }

    private ShortMessage stored(short usage) {
        ShortMessage bean = new ShortMessage(ShortMessage.TYPE_MOBILE, MOBILE, CODE, "127.0.0.1", usage);
        bean.setId(ID);
        when(mapper.select(ID)).thenReturn(bean);
        return bean;
    }

    private void attemptSlotAvailable() {
        when(mapper.increaseAttempts(ID, ShortMessage.MAX_ATTEMPTS)).thenReturn(1);
    }

    @Test
    void correctCodeClaimsAnAttemptThenConsumesTheCode() {
        stored(ShortMessage.USAGE_MFA_LOGIN);
        attemptSlotAvailable();
        when(mapper.updateStatusIfUnused(ID, ShortMessage.STATUS_CORRECT)).thenReturn(1);

        assertTrue(service.validateCode(ID, MOBILE, CODE, EXPIRES, ShortMessage.USAGE_MFA_LOGIN));

        InOrder order = inOrder(mapper);
        order.verify(mapper).increaseAttempts(ID, ShortMessage.MAX_ATTEMPTS);
        order.verify(mapper).updateStatusIfUnused(ID, ShortMessage.STATUS_CORRECT);
    }

    @Test
    void codeSentForAnotherUsageIsRejectedWithoutTouchingTheRow() {
        stored(ShortMessage.USAGE_RESET_PASSWORD);

        assertFalse(service.validateCode(ID, MOBILE, CODE, EXPIRES, ShortMessage.USAGE_MFA_LOGIN));

        verify(mapper).select(ID);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void wrongCodeKeepsTheRowUsableUntilAttemptsRunOut() {
        stored(ShortMessage.USAGE_MFA_LOGIN);
        attemptSlotAvailable();

        assertFalse(service.validateCode(ID, MOBILE, "000000", EXPIRES, ShortMessage.USAGE_MFA_LOGIN));

        // 输错不直接作废：是否作废交给数据库按当前尝试次数判断
        verify(mapper).exceedIfAttemptsReached(ID, ShortMessage.MAX_ATTEMPTS, ShortMessage.STATUS_EXCEEDED);
        verify(mapper, never()).updateStatusIfUnused(anyLong(), anyShort());
    }

    @Test
    void correctCodeIsRejectedOnceAttemptsAreExhausted() {
        stored(ShortMessage.USAGE_MFA_LOGIN);
        when(mapper.increaseAttempts(ID, ShortMessage.MAX_ATTEMPTS)).thenReturn(0);

        assertFalse(service.validateCode(ID, MOBILE, CODE, EXPIRES, ShortMessage.USAGE_MFA_LOGIN));

        verify(mapper).exceedIfAttemptsReached(ID, ShortMessage.MAX_ATTEMPTS, ShortMessage.STATUS_EXCEEDED);
        verify(mapper, never()).updateStatusIfUnused(ID, ShortMessage.STATUS_CORRECT);
    }

    @Test
    void concurrentSubmitOfTheSameCorrectCodeSucceedsOnlyOnce() {
        stored(ShortMessage.USAGE_MFA_LOGIN);
        attemptSlotAvailable();
        // 另一个请求已抢先消费
        when(mapper.updateStatusIfUnused(ID, ShortMessage.STATUS_CORRECT)).thenReturn(0);

        assertFalse(service.validateCode(ID, MOBILE, CODE, EXPIRES, ShortMessage.USAGE_MFA_LOGIN));
    }

    @Test
    void expiredCodeIsBurnedWithoutClaimingAnAttempt() {
        ShortMessage bean = stored(ShortMessage.USAGE_MFA_LOGIN);
        bean.setSendDate(OffsetDateTime.now().minusMinutes(EXPIRES + 1L));

        assertFalse(service.validateCode(ID, MOBILE, CODE, EXPIRES, ShortMessage.USAGE_MFA_LOGIN));

        verify(mapper).updateStatusIfUnused(ID, ShortMessage.STATUS_EXPIRED);
        verify(mapper, never()).increaseAttempts(anyLong(), anyInt());
    }

    @Test
    void usedCodeIsRejected() {
        ShortMessage bean = stored(ShortMessage.USAGE_MFA_LOGIN);
        bean.setStatus(ShortMessage.STATUS_CORRECT);

        assertFalse(service.validateCode(ID, MOBILE, CODE, EXPIRES, ShortMessage.USAGE_MFA_LOGIN));

        verify(mapper, never()).increaseAttempts(anyLong(), anyInt());
    }

    @Test
    void receiverIsComparedAfterNormalization() {
        stored(ShortMessage.USAGE_MODIFY_MOBILE);
        attemptSlotAvailable();
        when(mapper.updateStatusIfUnused(ID, ShortMessage.STATUS_CORRECT)).thenReturn(1);

        // 用户库中的号码可能带分隔符
        assertTrue(service.validateCode(ID, "138-0013 8000", CODE, EXPIRES, ShortMessage.USAGE_MODIFY_MOBILE));
    }

    @Test
    void anotherReceiverIsRejectedWithoutConsumingAnAttempt() {
        stored(ShortMessage.USAGE_MFA_LOGIN);

        assertFalse(service.validateCode(ID, "13900139000", CODE, EXPIRES, ShortMessage.USAGE_MFA_LOGIN));
        // 账号未登记手机号码
        assertFalse(service.validateCode(ID, null, CODE, EXPIRES, ShortMessage.USAGE_MFA_LOGIN));
        assertFalse(service.tryCode(ID, "13900139000", CODE, EXPIRES, CODE_LENGTH));

        // 短消息 ID 可被猜测：号码对不上就不能消耗别人验证码的尝试次数
        verify(mapper, never()).increaseAttempts(anyLong(), anyInt());
        verify(mapper, never()).updateStatusIfUnused(anyLong(), anyShort());
    }

    @Test
    void missingIdIsRejected() {
        assertFalse(service.validateCode(null, MOBILE, CODE, EXPIRES, ShortMessage.USAGE_MFA_LOGIN));
    }

    @Test
    void tryCodeRefusesTwoFactorCodesWithoutTouchingTheRow() {
        stored(ShortMessage.USAGE_MFA_LOGIN);

        // 双因子登录验证码只能在登录流程中校验（计入登录失败次数），预校验接口即使验证码正确也返回 false
        assertFalse(service.tryCode(ID, MOBILE, CODE, EXPIRES, CODE_LENGTH));

        verify(mapper).select(ID);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void tryCodeCountsOnlyWrongGuessesAndNeverConsumes() {
        stored(ShortMessage.USAGE_REGISTER);

        assertTrue(service.tryCode(ID, MOBILE, CODE, EXPIRES, CODE_LENGTH));
        verify(mapper, never()).increaseAttempts(anyLong(), anyInt());

        assertFalse(service.tryCode(ID, MOBILE, "000000", EXPIRES, CODE_LENGTH));
        verify(mapper).increaseAttempts(ID, ShortMessage.MAX_ATTEMPTS);
        verify(mapper).exceedIfAttemptsReached(ID, ShortMessage.MAX_ATTEMPTS, ShortMessage.STATUS_EXCEEDED);
        verify(mapper, never()).updateStatusIfUnused(anyLong(), anyShort());
    }
}
