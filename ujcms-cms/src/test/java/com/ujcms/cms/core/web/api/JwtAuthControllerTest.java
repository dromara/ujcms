package com.ujcms.cms.core.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.ujcms.cms.core.component.JwtAuthService;
import com.ujcms.cms.core.component.SmsCodeSender;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.security.MfaTokenService;
import com.ujcms.cms.core.security.TwoFactorGuard;
import com.ujcms.cms.core.security.spi.SecondFactorProvider;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.SecurityProperties;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.captcha.IpLoginAttemptService;
import com.ujcms.common.security.Secures;
import com.ujcms.common.security.jwt.JwtProperties;
import com.ujcms.common.sms.IpSmsCounterService;
import com.ujcms.common.sms.SmsCooldownCache;
import com.ujcms.common.web.Responses.Body;

/**
 * Verifies the two-step second-factor login: the password is the first factor and is checked
 * first; only then is a challenge issued and a code sent — to the mobile registered on the account,
 * never to a number supplied by the caller. The challenge step must not reset any failure counter,
 * and a wrong code must count toward lockout exactly like a wrong password.
 *
 * <p>Without a web application context {@code Servlets.getMessage} falls back to the message key, so
 * the response message asserted here is the key itself.
 */
class JwtAuthControllerTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String USERNAME = "editor";
    private static final String RAW_PASSWORD = "pw";
    private static final String ENCODED_PASSWORD = "encoded";
    private static final String MOBILE = "13800138000";
    private static final String IP = "127.0.0.1";
    private static final long USER_ID = 2L;
    private static final long MESSAGE_ID = 9L;

    private Config.Security security;
    private User user;
    private Secures.Pair sm2;

    private UserService userService;
    private LoginLogService loginLogService;
    private IpLoginAttemptService ipLoginAttemptService;
    private CaptchaTokenService captchaTokenService;
    private SecondFactorProvider secondFactorProvider;
    private ShortMessageService shortMessageService;
    private MfaTokenService mfaTokenService;
    private SecurityProperties securityProperties;
    private JwtAuthController controller;

    @BeforeEach
    void setUp() {
        security = new Config.Security();
        security.setTwoFactor(true);
        security.setUserMaxAttempts(5);
        Config.Sms sms = new Config.Sms();
        sms.setProvider(Config.Sms.PROVIDER_ALIYUN);
        Config config = mock(Config.class);
        when(config.getSecurity()).thenReturn(security);
        when(config.getSms()).thenReturn(sms);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(config);

        sm2 = Secures.generateSm2QdKeyPair();
        Props props = mock(Props.class);
        when(props.getClientSm2PrivateKey()).thenReturn(sm2.getPrivateKey());

        user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setPassword(ENCODED_PASSWORD);
        user.setMobile(MOBILE);
        userService = mock(UserService.class);
        when(userService.selectByUsername(USERNAME)).thenReturn(user);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        loginLogService = mock(LoginLogService.class);
        ipLoginAttemptService = mock(IpLoginAttemptService.class);
        captchaTokenService = mock(CaptchaTokenService.class);
        secondFactorProvider = mock(SecondFactorProvider.class);

        shortMessageService = mock(ShortMessageService.class);
        ShortMessage sent = new ShortMessage(ShortMessage.TYPE_MOBILE, MOBILE, "123456", IP,
                ShortMessage.USAGE_MFA_LOGIN);
        sent.setId(MESSAGE_ID);
        when(shortMessageService.insertMobileMessage(anyString(), anyString(), anyString(), anyShort()))
                .thenReturn(sent);
        SmsCodeSender smsCodeSender = new SmsCodeSender(shortMessageService, mock(IpSmsCounterService.class),
                mock(SmsCooldownCache.class));

        SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSM3");
        JwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableJWKSet<>(new JWKSet(new OctetSequenceKey.Builder(secretKey).build())));
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(SECRET);
        mfaTokenService = new MfaTokenService(encoder, secretKey, jwtProperties, 600);
        JwtAuthService jwtAuthService = new JwtAuthService(jwtProperties, encoder, loginLogService, userService,
                ipLoginAttemptService, props);

        controller = new JwtAuthController(props, jwtProperties, secretKey, jwtAuthService);
        controller.setConfigService(configService);
        controller.setUserService(userService);
        controller.setLoginLogService(loginLogService);
        controller.setSecondFactorProvider(secondFactorProvider);
        controller.setMfaTokenService(mfaTokenService);
        securityProperties = new SecurityProperties();
        controller.setTwoFactorGuard(new TwoFactorGuard(securityProperties, userService));
        controller.setSmsCodeSender(smsCodeSender);
        controller.setShortMessageService(shortMessageService);
        controller.setPasswordEncoder(passwordEncoder);
        controller.setCaptchaTokenService(captchaTokenService);
        controller.setIpLoginAttemptService(ipLoginAttemptService);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(IP);
        return request;
    }

    private ResponseEntity<Body> login(String rawPassword) {
        JwtAuthController.LoginParams params = new JwtAuthController.LoginParams();
        params.username = USERNAME;
        params.password = Secures.sm2Encrypt(rawPassword, sm2.getPublicKey());
        return controller.login(params, request());
    }

    private ResponseEntity<Body> mfaVerify(String mfaToken, String code) {
        JwtAuthController.MfaVerifyParams params = new JwtAuthController.MfaVerifyParams();
        params.mfaToken = mfaToken;
        params.shortMessageId = MESSAGE_ID;
        params.code = code;
        return controller.mfaVerify(params, request());
    }

    private ResponseEntity<Body> mfaSend(String mfaToken) {
        JwtAuthController.MfaSendParams params = new JwtAuthController.MfaSendParams();
        params.mfaToken = mfaToken;
        return controller.mfaSend(params, request());
    }

    private static Body body(ResponseEntity<Body> response) {
        Body body = response.getBody();
        assertNotNull(body);
        return body;
    }

    private static void assertFailure(String messageKey, ResponseEntity<Body> response) {
        assertEquals(Body.FAILURE, body(response).getStatus());
        assertEquals(messageKey, body(response).getMessage());
    }

    @Test
    void withoutTwoFactorACorrectPasswordCompletesLogin() {
        security.setTwoFactor(false);

        Body body = body(login(RAW_PASSWORD));

        assertEquals(Body.SUCCESS, body.getStatus());
        assertInstanceOf(JwtAuthController.JwtTokenResult.class, body.getResult());
        verify(userService).loginSuccess(user.getExt(), IP);
        verify(loginLogService).loginSuccess(USER_ID, USERNAME, IP);
    }

    @Test
    void correctPasswordIssuesAChallengeWithoutTokensSmsOrCounterReset() {
        Body body = body(login(RAW_PASSWORD));

        assertEquals(JwtAuthController.STATUS_SECOND_FACTOR_REQUIRED, body.getStatus());
        JwtAuthController.SecondFactorChallenge challenge =
                assertInstanceOf(JwtAuthController.SecondFactorChallenge.class, body.getResult());
        // 口令通过后才提示验证码将发往哪个号码，且只给脱敏号码
        assertEquals("138****8000", challenge.maskedMobile());
        assertNotNull(mfaTokenService.verify(challenge.mfaToken()));
        // 第一步不得清零失败计数：否则持有口令者可在两次验证码猜测之间重置锁定计数
        verify(userService, never()).loginSuccess(any(), anyString());
        verify(ipLoginAttemptService, never()).success(anyString());
        verify(loginLogService, never()).loginSuccess(anyLong(), anyString(), anyString());
        // 验证码由客户端凭挑战令牌另行请求发送
        verify(shortMessageService, never()).sendMobileMessage(anyString(), anyString(), any());
    }

    @Test
    void breakGlassPropertySkipsTheSecondFactorButStillRequiresThePassword() {
        // 运维以 --ujcms.security.disable-two-factor=true 重启：后台开关仍为开启，登录只校验口令
        securityProperties.setDisableTwoFactor(true);

        assertEquals(Body.FAILURE, body(login("wrong")).getStatus());

        Body body = body(login(RAW_PASSWORD));
        assertEquals(Body.SUCCESS, body.getStatus());
        assertInstanceOf(JwtAuthController.JwtTokenResult.class, body.getResult());
    }

    @Test
    void breakGlassLetsAnAccountWithoutMobileBackIn() {
        // 应急开关要解决的正是这种情况：管理员没有可用的手机号码，进不了后台去纠正
        securityProperties.setDisableTwoFactor(true);
        user.setMobile(null);

        assertEquals(Body.SUCCESS, body(login(RAW_PASSWORD)).getStatus());
    }

    @Test
    void wrongPasswordNeverReachesTheSecondFactor() {
        ResponseEntity<Body> response = login("wrong");

        assertEquals(Body.FAILURE, body(response).getStatus());
        assertNull(body(response).getResult());
        verify(ipLoginAttemptService).failure(IP);
        verify(userService).loginFailure(eq(user.getExt()), anyInt());
        verifyNoInteractions(secondFactorProvider);
    }

    @Test
    void accountWithoutMobileFailsClosedWithAClearReason() {
        user.setMobile(null);

        assertFailure("error.twoFactorMobileMissing", login(RAW_PASSWORD));

        verify(loginLogService).loginFailure(USER_ID, USERNAME, IP, LoginLog.STATUS_MOBILE_MISSING);
        verify(userService, never()).loginSuccess(any(), anyString());
    }

    @Test
    void captchaStepUpStillAppliesWhenTwoFactorIsOn() {
        when(ipLoginAttemptService.isExcessive(IP, security.getIpCaptchaAttempts())).thenReturn(true);
        when(captchaTokenService.validateCaptcha(any(), any())).thenReturn(false);

        assertFailure("error.captchaIncorrect", login(RAW_PASSWORD));
    }

    @Test
    void forcedPasswordChangeComesBeforeTheSecondFactor() {
        security.setForceChangePasswordOnFirstLogin(true);
        user.setPwdMustChange(true);

        Body body = body(login(RAW_PASSWORD));

        // 改密接口只需旧口令，先处理可避免白白消耗一条验证码
        assertEquals(JwtAuthController.STATUS_PASSWORD_CHANGE_REQUIRED, body.getStatus());
        assertNull(body.getResult());
    }

    @Test
    void codeIsSentToTheMobileRegisteredOnTheAccount() {
        Body body = body(mfaSend(mfaTokenService.mint(USERNAME, true)));

        assertEquals(Body.SUCCESS, body.getStatus());
        JwtAuthController.MfaSendResult result =
                assertInstanceOf(JwtAuthController.MfaSendResult.class, body.getResult());
        assertEquals(MESSAGE_ID, result.shortMessageId());
        assertEquals("138****8000", result.maskedMobile());
        assertEquals(SmsCooldownCache.EXPIRES_SECONDS, result.cooldownSeconds());
        assertEquals(6, result.codeLength());
        verify(shortMessageService).sendMobileMessage(eq(MOBILE), anyString(), any());
        verify(shortMessageService).insertMobileMessage(eq(MOBILE), anyString(), eq(IP),
                eq(ShortMessage.USAGE_MFA_LOGIN));
    }

    @Test
    void correctCodeCompletesLogin() {
        when(secondFactorProvider.validate(eq(MOBILE), eq(MESSAGE_ID), eq("123456"), anyInt())).thenReturn(true);

        Body body = body(mfaVerify(mfaTokenService.mint(USERNAME, true), "123456"));

        assertEquals(Body.SUCCESS, body.getStatus());
        assertInstanceOf(JwtAuthController.JwtTokenResult.class, body.getResult());
        verify(userService).loginSuccess(user.getExt(), IP);
        verify(ipLoginAttemptService).success(IP);
        verify(loginLogService).loginSuccess(USER_ID, USERNAME, IP);
    }

    @Test
    void wrongCodeCountsTowardLockoutLikeAWrongPassword() {
        ShortMessage stillUsable = new ShortMessage();
        when(shortMessageService.select(MESSAGE_ID)).thenReturn(stillUsable);

        assertFailure("error.mobileMessageIncorrect", mfaVerify(mfaTokenService.mint(USERNAME, true), "000000"));

        verify(ipLoginAttemptService).failure(IP);
        verify(userService).loginFailure(eq(user.getExt()), anyInt());
        // 登录日志带用户 ID，便于审计是哪个账号在被猜验证码
        verify(loginLogService).loginFailure(USER_ID, USERNAME, IP, LoginLog.STATUS_SHORT_MESSAGE_WRONG);
        verify(userService, never()).loginSuccess(any(), anyString());
    }

    @Test
    void burnedCodeAsksForANewOneInsteadOfSayingIncorrect() {
        ShortMessage burned = new ShortMessage();
        burned.setStatus(ShortMessage.STATUS_EXCEEDED);
        when(shortMessageService.select(MESSAGE_ID)).thenReturn(burned);

        assertFailure("error.mobileMessageExpired", mfaVerify(mfaTokenService.mint(USERNAME, true), "000000"));
    }

    @Test
    void lockedUserCanNeitherReceiveNorGuessCodes() {
        user.getExt().setErrorCount(security.getUserMaxAttempts());
        String mfaToken = mfaTokenService.mint(USERNAME, true);

        assertFailure("error.userExcessiveAttempts", mfaSend(mfaToken));
        assertFailure("error.userExcessiveAttempts", mfaVerify(mfaToken, "123456"));

        verify(shortMessageService, never()).sendMobileMessage(anyString(), anyString(), any());
        verifyNoInteractions(secondFactorProvider);
    }

    @Test
    void invalidChallengeTokenSendsTheClientBackToThePasswordStep() {
        assertEquals(JwtAuthController.STATUS_SECOND_FACTOR_EXPIRED, body(mfaSend("not.a.jwt")).getStatus());
        assertEquals(JwtAuthController.STATUS_SECOND_FACTOR_EXPIRED,
                body(mfaVerify("not.a.jwt", "123456")).getStatus());
        verifyNoInteractions(secondFactorProvider);
    }

    @Test
    void challengeIsVoidOnceTheUserIsDisabledOrLosesTheMobile() {
        String mfaToken = mfaTokenService.mint(USERNAME, true);

        user.setMobile(" ");
        assertEquals(JwtAuthController.STATUS_SECOND_FACTOR_EXPIRED, body(mfaSend(mfaToken)).getStatus());

        user.setMobile(MOBILE);
        user.setStatus(User.STATUS_LOCKED);
        assertEquals(JwtAuthController.STATUS_SECOND_FACTOR_EXPIRED, body(mfaVerify(mfaToken, "123456")).getStatus());
    }

    @Test
    void accessTokenCannotStandInForAChallengeToken() {
        security.setTwoFactor(false);
        JwtAuthController.JwtTokenResult tokens =
                assertInstanceOf(JwtAuthController.JwtTokenResult.class, body(login(RAW_PASSWORD)).getResult());
        security.setTwoFactor(true);

        assertEquals(JwtAuthController.STATUS_SECOND_FACTOR_EXPIRED,
                body(mfaVerify(tokens.accessToken(), "123456")).getStatus());
        assertEquals(JwtAuthController.STATUS_SECOND_FACTOR_EXPIRED,
                body(mfaVerify(tokens.refreshToken(), "123456")).getStatus());
    }
}
