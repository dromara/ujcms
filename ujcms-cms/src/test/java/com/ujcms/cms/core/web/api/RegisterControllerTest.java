package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.security.Secures;
import com.ujcms.common.web.exception.Http400Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static com.ujcms.cms.core.domain.Config.Register.VERIFY_MODE_EMAIL;
import static com.ujcms.cms.core.domain.Config.Register.VERIFY_MODE_MANUAL;
import static com.ujcms.cms.core.domain.Config.Register.VERIFY_MODE_MOBILE;
import static com.ujcms.cms.core.domain.Config.Register.VERIFY_MODE_NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 注册接口的查重顺序（backend-authorization §3）：手机号 / 邮箱是否已注册只在验证码通过之后才查、才报，
 * 不验证 / 人工验证模式既不查也不存这两个字段；用户名重复先于人机验证报出。顺序一乱，{@code POST /api/register}
 * 就是一个匿名的账号枚举口子，删掉公开的 {@code *-exist} 接口便毫无意义。
 */
class RegisterControllerTest {
    private static final String MOBILE = "13800000000";
    private static final String EMAIL = "alice@example.com";

    private final CaptchaTokenService captchaTokenService = mock(CaptchaTokenService.class);
    private final ShortMessageService shortMessageService = mock(ShortMessageService.class);
    private final UserService userService = mock(UserService.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final Props props = mock(Props.class);
    private final Config config = new Config();
    /** {@code Config#getRegister()} 没有存储值时每次都新建，设置要经 {@code setRegister} 落到 JSON 列才留得住 */
    private final Config.Register register = new Config.Register();
    private final Secures.Pair keyPair = Secures.generateSm2QdKeyPair();
    private final RegisterController controller = new RegisterController(captchaTokenService, shortMessageService,
            userService, configService, props);

    @BeforeEach
    void setUp() {
        register.setEnabled(true);
        config.setRegister(register);
        when(configService.getUnique()).thenReturn(config);
        when(props.getClientSm2PrivateKey()).thenReturn(keyPair.getPrivateKey());
    }

    /** 四种模式都带齐全部字段：顺序测试要证明的正是"多给的字段不会被查" */
    private RegisterController.RegisterParams params(int verifyMode) {
        register.setVerifyMode(verifyMode);
        config.setRegister(register);
        RegisterController.RegisterParams params = new RegisterController.RegisterParams();
        params.setUsername("alice");
        params.setPassword(Secures.sm2Encrypt("secret", keyPair.getPublicKey()));
        params.setMobile(MOBILE);
        params.setEmail(EMAIL);
        params.captchaToken = "token";
        params.captcha = "answer";
        params.mobileMessageId = 1L;
        params.mobileMessageValue = "123456";
        params.emailMessageId = 2L;
        params.emailMessageValue = "654321";
        return params;
    }

    private Http400Exception rejected(RegisterController.RegisterParams params) {
        return assertThrows(Http400Exception.class, () -> controller.register(params, new MockHttpServletRequest()));
    }

    private User registered(RegisterController.RegisterParams params) {
        controller.register(params, new MockHttpServletRequest());
        ArgumentCaptor<User> inserted = ArgumentCaptor.forClass(User.class);
        verify(userService).insert(inserted.capture(), any(), any());
        return inserted.getValue();
    }

    private void codeValid(String receiver, boolean valid) {
        when(shortMessageService.validateCode(any(), eq(receiver), any(), anyInt(), eq(ShortMessage.USAGE_REGISTER)))
                .thenReturn(valid);
    }

    @Test
    void duplicateUsernameIsReportedBeforeAnyCaptchaOrCodeIsSpent() {
        when(userService.selectByUsername("alice")).thenReturn(new User());
        assertEquals("error.usernameExist", rejected(params(VERIFY_MODE_MOBILE)).getMessage());
        verifyNoInteractions(captchaTokenService, shortMessageService);
        verify(userService, never()).selectByMobile(any());
        verify(userService, never()).selectByEmail(any());
    }

    @Test
    void captchaModesNeverLookUpOrStoreMobileAndEmail() {
        when(userService.selectByMobile(MOBILE)).thenReturn(new User());
        when(userService.selectByEmail(EMAIL)).thenReturn(new User());
        when(captchaTokenService.validateCaptcha(any(), any())).thenReturn(false);
        assertEquals("error.captchaIncorrect", rejected(params(VERIFY_MODE_MANUAL)).getMessage());

        when(captchaTokenService.validateCaptcha(any(), any())).thenReturn(true);
        User user = registered(params(VERIFY_MODE_NONE));
        assertNull(user.getMobile());
        assertNull(user.getEmail());
        verify(userService, never()).selectByMobile(any());
        verify(userService, never()).selectByEmail(any());
        verifyNoInteractions(shortMessageService);
    }

    @Test
    void mobileIsLookedUpOnlyAfterItsCodeIsVerified() {
        when(userService.selectByMobile(MOBILE)).thenReturn(new User());
        codeValid(MOBILE, false);
        assertEquals("error.mobileMessageIncorrect", rejected(params(VERIFY_MODE_MOBILE)).getMessage());
        verify(userService, never()).selectByMobile(any());

        codeValid(MOBILE, true);
        assertEquals("error.mobileExist", rejected(params(VERIFY_MODE_MOBILE)).getMessage());
        verifyNoInteractions(captchaTokenService);
    }

    @Test
    void emailIsLookedUpOnlyAfterItsCodeIsVerified() {
        when(userService.selectByEmail(EMAIL)).thenReturn(new User());
        codeValid(EMAIL, false);
        assertEquals("error.emailMessageIncorrect", rejected(params(VERIFY_MODE_EMAIL)).getMessage());
        verify(userService, never()).selectByEmail(any());

        codeValid(EMAIL, true);
        assertEquals("error.emailExist", rejected(params(VERIFY_MODE_EMAIL)).getMessage());
        verifyNoInteractions(captchaTokenService);
    }

    @Test
    void onlyTheVerifiedFieldIsStored() {
        codeValid(MOBILE, true);
        User byMobile = registered(params(VERIFY_MODE_MOBILE));
        assertEquals(MOBILE, byMobile.getMobile());
        assertNull(byMobile.getEmail());
        verify(userService, never()).selectByEmail(any());
    }
}
