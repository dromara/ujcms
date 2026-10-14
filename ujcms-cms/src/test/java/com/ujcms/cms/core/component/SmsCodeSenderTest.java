package com.ujcms.cms.core.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.common.sms.IpSmsCounterService;
import com.ujcms.common.sms.SmsCooldownCache;

/**
 * Verifies the two-factor send path. Unlike the public endpoint (covered by {@code SmsControllerTest})
 * it is exempt from the per-IP daily quota — a whole office often shares one egress IP — and is
 * capped per receiver instead. It is also idempotent inside the cooldown window: reopening the
 * login dialog must hand back the code that was just sent rather than fail with "cooling down"
 * and strand the user without the id of a code they already received.
 *
 * <p>Without a web application context {@code Servlets.getMessage} falls back to the message key.
 */
class SmsCodeSenderTest {
    private static final String MOBILE = "13800138000";
    private static final String IP = "127.0.0.1";

    private Config.Sms sms;
    private ShortMessageService shortMessageService;
    private IpSmsCounterService ipSmsCounterService;
    private SmsCooldownCache smsCooldownCache;
    private SmsCodeSender sender;

    @BeforeEach
    void setUp() {
        sms = new Config.Sms();
        sms.setProvider(Config.Sms.PROVIDER_ALIYUN);
        shortMessageService = mock(ShortMessageService.class);
        ipSmsCounterService = mock(IpSmsCounterService.class);
        smsCooldownCache = mock(SmsCooldownCache.class);
        sender = new SmsCodeSender(shortMessageService, ipSmsCounterService, smsCooldownCache);
    }

    private static ShortMessage message(long id, OffsetDateTime sendDate) {
        ShortMessage bean = new ShortMessage(ShortMessage.TYPE_MOBILE, MOBILE, "123456", IP,
                ShortMessage.USAGE_MFA_LOGIN);
        bean.setId(id);
        bean.setSendDate(sendDate);
        return bean;
    }

    private static String failureKey(SmsCodeSender.SendResult result) {
        assertFalse(result.isSuccess());
        return result.toFailure(new MockHttpServletRequest()).getBody().getMessage();
    }

    @Test
    void sendsToTheNormalizedMobileAndIgnoresTheIpQuota() {
        ShortMessage inserted = message(1L, OffsetDateTime.now());
        when(shortMessageService.insertMobileMessage(eq(MOBILE), anyString(), eq(IP),
                eq(ShortMessage.USAGE_MFA_LOGIN))).thenReturn(inserted);

        SmsCodeSender.SendResult result = sender.sendMfaCode("138-0013 8000", IP, sms);

        assertTrue(result.isSuccess());
        assertSame(inserted, result.getMessage());
        assertEquals(SmsCooldownCache.EXPIRES_SECONDS, result.getCooldownSeconds());
        verify(shortMessageService).sendMobileMessage(eq(MOBILE), anyString(), eq(sms));
        verify(smsCooldownCache).start(MOBILE);
        // 整个单位常共用一个出口 IP：双因子验证码不查、不计 IP 每日限额
        verifyNoInteractions(ipSmsCounterService);
    }

    @Test
    void codeSentWithinTheCooldownIsReusedInsteadOfResent() {
        ShortMessage recent = message(7L, OffsetDateTime.now().minusSeconds(20));
        when(shortMessageService.findLatestUnused(MOBILE, ShortMessage.USAGE_MFA_LOGIN)).thenReturn(recent);

        SmsCodeSender.SendResult result = sender.sendMfaCode(MOBILE, IP, sms);

        assertTrue(result.isSuccess());
        assertSame(recent, result.getMessage());
        // 返回该验证码剩余的冷却时间，前台据此显示倒计时
        assertTrue(result.getCooldownSeconds() > 30 && result.getCooldownSeconds() <= 40);
        verify(shortMessageService, never()).sendMobileMessage(anyString(), anyString(), any());
        verify(shortMessageService, never()).insertMobileMessage(anyString(), anyString(), anyString(), anyShort());
    }

    @Test
    void unusedCodeOlderThanTheCooldownDoesNotBlockANewOne() {
        when(shortMessageService.findLatestUnused(MOBILE, ShortMessage.USAGE_MFA_LOGIN))
                .thenReturn(message(7L, OffsetDateTime.now().minusSeconds(90)));
        when(shortMessageService.insertMobileMessage(anyString(), anyString(), anyString(), anyShort()))
                .thenReturn(message(8L, OffsetDateTime.now()));

        SmsCodeSender.SendResult result = sender.sendMfaCode(MOBILE, IP, sms);

        assertTrue(result.isSuccess());
        assertEquals(8L, result.getMessage().getId());
        verify(shortMessageService).sendMobileMessage(eq(MOBILE), anyString(), eq(sms));
    }

    @Test
    void cooldownStillAppliesWhenTheRecentCodeWasAlreadyUsed() {
        // 刚登录成功又重新登录：冷却期内的验证码已被消费，没有可复用的，仍需等冷却结束
        when(smsCooldownCache.isCoolingDown(MOBILE)).thenReturn(true);

        assertEquals("error.codeSendCooldown", failureKey(sender.sendMfaCode(MOBILE, IP, sms)));
        verify(shortMessageService, never()).sendMobileMessage(anyString(), anyString(), any());
    }

    @Test
    void dailyCapPerReceiverStopsSmsBombingByAPasswordHolder() {
        when(shortMessageService.countSince(eq(MOBILE), eq(ShortMessage.USAGE_MFA_LOGIN), any()))
                .thenReturn((long) SmsCodeSender.MFA_DAILY_LIMIT);

        assertEquals("error.mfaSendExcessive", failureKey(sender.sendMfaCode(MOBILE, IP, sms)));
        verify(shortMessageService, never()).sendMobileMessage(anyString(), anyString(), any());
    }

    @Test
    void smsProviderNotConfiguredIsReported() {
        sms.setProvider(Config.Sms.PROVIDER_NONE);

        assertEquals("error.smsNotEnabled", failureKey(sender.sendMfaCode(MOBILE, IP, sms)));
        verify(ipSmsCounterService, never()).isExcessive(anyString(), anyInt());
    }
}
