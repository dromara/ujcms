package com.ujcms.cms.core.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ujcms.cms.core.component.SmsCodeSender;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.sms.IpSmsCounterService;
import com.ujcms.common.sms.SmsCooldownCache;
import com.ujcms.common.sms.SmsError;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;

/**
 * Verifies the public SMS endpoint's throttling and error shaping: provider errors never reach the
 * anonymous caller verbatim, the per-IP counter is actually incremented, and a receiver cools down
 * after a successful send. Every failure message names its source: the system's own limits
 * ({@code error.ipSendSmsExcessive}, {@code error.codeSendCooldown}) versus the SMS platform
 * ({@code error.smsPlatform*}). The send sequence itself lives in {@link SmsCodeSender}; the real sender is
 * wired here so the endpoint is verified end to end.
 *
 * <p>Without a web application context {@code Servlets.getMessage} falls back to the message key, so
 * the response message asserted here is the key itself.
 */
class SmsControllerTest {
    private static final String MOBILE = "13800138000";
    private static final String IP = "127.0.0.1";

    private ShortMessageService shortMessageService;
    private IpSmsCounterService ipSmsCounterService;
    private SmsCooldownCache smsCooldownCache;
    private SmsController controller;

    @BeforeEach
    void setUp() {
        Config.Sms sms = new Config.Sms();
        sms.setProvider(Config.Sms.PROVIDER_ALIYUN);
        Config config = mock(Config.class);
        when(config.getSms()).thenReturn(sms);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(config);

        CaptchaTokenService captchaTokenService = mock(CaptchaTokenService.class);
        when(captchaTokenService.validateCaptcha(any(), any())).thenReturn(true);

        shortMessageService = mock(ShortMessageService.class);
        ShortMessage shortMessage = new ShortMessage();
        shortMessage.setId(1L);
        when(shortMessageService.insertMobileMessage(anyString(), anyString(), anyString(), anyShort()))
                .thenReturn(shortMessage);
        ipSmsCounterService = mock(IpSmsCounterService.class);
        smsCooldownCache = mock(SmsCooldownCache.class);

        SmsCodeSender smsCodeSender = new SmsCodeSender(shortMessageService, ipSmsCounterService, smsCooldownCache);
        controller = new SmsController(configService, shortMessageService, smsCodeSender, ipSmsCounterService,
                smsCooldownCache, captchaTokenService, mock(Props.class));
    }

    private ResponseEntity<Body> send() {
        return send(MOBILE, ShortMessage.USAGE_REGISTER);
    }

    private ResponseEntity<Body> send(String receiver, short usage) {
        SmsController.ShortMessageParams params = new SmsController.ShortMessageParams();
        params.receiver = receiver;
        params.usage = usage;
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(IP);
        return controller.mobileMessage(params, request);
    }

    private static void assertFailure(String messageKey, ResponseEntity<Body> response) {
        Body body = response.getBody();
        assertNotNull(body);
        assertEquals(Body.FAILURE, body.getStatus());
        assertEquals(messageKey, body.getMessage());
    }

    @Test
    void successStartsCooldownAndCountsIp() {
        when(shortMessageService.sendMobileMessage(eq(MOBILE), anyString(), any())).thenReturn(null);

        Body body = send().getBody();

        assertNotNull(body);
        assertEquals(Body.SUCCESS, body.getStatus());
        verify(smsCooldownCache).start(MOBILE);
        verify(ipSmsCounterService).updateCount(IP);
    }

    @Test
    void receiverIsNormalizedBeforeCooldownSendAndRecord() {
        when(shortMessageService.sendMobileMessage(eq(MOBILE), anyString(), any())).thenReturn(null);

        Body body = send("138-0013 8000", ShortMessage.USAGE_REGISTER).getBody();

        assertNotNull(body);
        assertEquals(Body.SUCCESS, body.getStatus());
        // 带分隔符的写法与规范写法共用同一个冷却键，不能借此绕过同号码冷却
        verify(smsCooldownCache).isCoolingDown(MOBILE);
        verify(smsCooldownCache).start(MOBILE);
        verify(shortMessageService).insertMobileMessage(eq(MOBILE), anyString(), eq(IP),
                eq(ShortMessage.USAGE_REGISTER));
    }

    @Test
    void usagesWithoutAPublicConsumerAreRejected() {
        // “测试”只能由后台发送；“登录”没有任何校验方
        assertThrows(Http400Exception.class, () -> send(MOBILE, ShortMessage.USAGE_TEST));
        assertThrows(Http400Exception.class, () -> send(MOBILE, ShortMessage.USAGE_LOGIN));
        // 修改邮箱地址的验证码不会发到手机
        assertThrows(Http400Exception.class, () -> send(MOBILE, ShortMessage.USAGE_MODIFY_EMAIL));
        verify(shortMessageService, never()).sendMobileMessage(anyString(), anyString(), any());
    }

    @Test
    void coolingDownReceiverIsRejectedBeforeCallingProvider() {
        when(smsCooldownCache.isCoolingDown(MOBILE)).thenReturn(true);

        assertFailure("error.codeSendCooldown", send());
        verify(shortMessageService, never()).sendMobileMessage(anyString(), anyString(), any());
    }

    @Test
    void excessiveIpIsRejectedBeforeCallingProvider() {
        when(ipSmsCounterService.isExcessive(eq(IP), anyInt())).thenReturn(true);

        assertFailure("error.ipSendSmsExcessive", send());
        verify(shortMessageService, never()).sendMobileMessage(anyString(), anyString(), any());
    }

    @Test
    void providerRateLimitIsShapedIntoFriendlyMessage() {
        when(shortMessageService.sendMobileMessage(eq(MOBILE), anyString(), any())).thenReturn(SmsError.of(
                SmsError.Kind.RATE_LIMITED, "aliyun", "isv.BUSINESS_LIMIT_CONTROL", "触发小时级流控Permits:5"));

        assertFailure("error.smsPlatformRateLimited", send());
        // 发送失败不计数、不冷却、不入库
        verify(smsCooldownCache, never()).start(anyString());
        verify(ipSmsCounterService, never()).updateCount(anyString());
        verify(shortMessageService, never()).insertMobileMessage(anyString(), anyString(), anyString(), anyShort());
    }

    @Test
    void invalidMobileIsShapedIntoFriendlyMessage() {
        when(shortMessageService.sendMobileMessage(eq(MOBILE), anyString(), any())).thenReturn(SmsError.of(
                SmsError.Kind.INVALID_MOBILE, "aliyun", "isv.MOBILE_NUMBER_ILLEGAL", "非法手机号"));

        assertFailure("error.smsPlatformMobileIncorrect", send());
    }

    @Test
    void otherProviderErrorsNeverLeakToTheCaller() {
        when(shortMessageService.sendMobileMessage(eq(MOBILE), anyString(), any())).thenReturn(SmsError.of(
                SmsError.Kind.OTHER, "aliyun", "isv.AMOUNT_NOT_ENOUGH", "账户余额不足"));

        assertFailure("error.smsPlatformSendFailed", send());
    }
}
