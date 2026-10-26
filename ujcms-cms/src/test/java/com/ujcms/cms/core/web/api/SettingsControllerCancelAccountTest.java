package com.ujcms.cms.core.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.UserExt;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.security.Secures;
import com.ujcms.common.web.PathResolver;
import com.ujcms.common.web.Responses.Body;

/**
 * Self-service account deregistration (docs/spec/membership/member-center.md §13): only a
 * member may cancel, the login password is checked with the same failure accounting as the
 * email / mobile changes, and a success ends the session and drops the remember-me tokens.
 *
 * <p>Without a web application context {@code Servlets.getMessage} falls back to the message
 * key, so the refusal message asserted here is the key itself.
 */
class SettingsControllerCancelAccountTest {
    private static final long USER_ID = 7L;
    private static final String USERNAME = "member";
    private static final String RAW_PASSWORD = "pw";
    private static final String ENCODED_PASSWORD = "encoded";
    private static final int LOCK_MINUTES = 30;

    private Secures.Pair sm2;
    private User user;
    private UserService userService;
    private Props props;
    private PersistentTokenRepository tokenRepository;
    private SettingsController controller;
    private MockHttpServletRequest request;
    private MockHttpSession session;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        Config.Security security = new Config.Security();
        security.setUserMaxAttempts(5);
        security.setUserLockMinutes(LOCK_MINUTES);
        Config config = mock(Config.class);
        when(config.getSecurity()).thenReturn(security);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(config);

        sm2 = Secures.generateSm2QdKeyPair();
        props = mock(Props.class);
        when(props.getClientSm2PrivateKey()).thenReturn(sm2.getPrivateKey());

        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        userService = mock(UserService.class);
        tokenRepository = mock(PersistentTokenRepository.class);
        controller = new SettingsController(mock(ShortMessageService.class), configService, userService,
                passwordEncoder, mock(PathResolver.class), props, tokenRepository);

        user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setPassword(ENCODED_PASSWORD);
        user.setType(User.TYPE_MEMBER);
        user.setExt(new UserExt());
        Contexts.setCurrentUser(user);

        session = new MockHttpSession();
        request = new MockHttpServletRequest();
        request.setSession(session);
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        Contexts.clearCurrentUser();
        SecurityContextHolder.clearContext();
    }

    private SettingsController.CancelAccountParams params(@Nullable String rawPassword) {
        SettingsController.CancelAccountParams params = new SettingsController.CancelAccountParams();
        if (rawPassword != null) {
            params.password = Secures.sm2Encrypt(rawPassword, sm2.getPublicKey());
        }
        return params;
    }

    private static Body body(ResponseEntity<Body> result) {
        Body body = result.getBody();
        assertNotNull(body);
        return body;
    }

    @Test
    void correctPasswordCancelsAccountEndsSessionAndDropsRememberMe() {
        ResponseEntity<Body> result = controller.cancelAccount(params(RAW_PASSWORD), request, response);

        assertEquals(Body.SUCCESS, body(result).getStatus());
        verify(userService).cancel(user);
        verify(tokenRepository).removeUserTokens(USERNAME);
        verify(userService, never()).loginFailure(any(), anyInt());
        assertTrue(session.isInvalid());
    }

    @Test
    void wrongPasswordCountsAsLoginFailureAndKeepsAccount() {
        ResponseEntity<Body> result = controller.cancelAccount(params("wrong"), request, response);

        assertEquals(Body.FAILURE, body(result).getStatus());
        verify(userService).loginFailure(user.getExt(), LOCK_MINUTES);
        verify(userService, never()).cancel(any());
        verifyNoInteractions(tokenRepository);
        assertFalse(session.isInvalid());
    }

    @Test
    void missingPasswordIsRefusedWhenAccountHasOne() {
        ResponseEntity<Body> result = controller.cancelAccount(params(null), request, response);

        assertEquals(Body.FAILURE, body(result).getStatus());
        verify(userService, never()).cancel(any());
        assertFalse(session.isInvalid());
    }

    @Test
    void accountWithoutPasswordCancelsWithoutOne() {
        user.setPassword(null);

        ResponseEntity<Body> result = controller.cancelAccount(params(null), request, response);

        assertEquals(Body.SUCCESS, body(result).getStatus());
        verify(userService).cancel(user);
        assertTrue(session.isInvalid());
    }

    @Test
    void roleHolderIsRefusedBeforeAnyPasswordCheck() {
        user.setRoleList(List.of(new Role()));

        ResponseEntity<Body> result = controller.cancelAccount(params("wrong"), request, response);

        assertEquals("error.cancelAccountBackendUser", body(result).getMessage());
        verify(userService, never()).cancel(any());
        verify(userService, never()).loginFailure(any(), anyInt());
        assertFalse(session.isInvalid());
    }

    @Test
    void adminTypeIsRefused() {
        user.setType(User.TYPE_NORMAL);

        ResponseEntity<Body> result = controller.cancelAccount(params(RAW_PASSWORD), request, response);

        assertEquals("error.cancelAccountBackendUser", body(result).getMessage());
        verify(userService, never()).cancel(any());
    }

    @Test
    void demoUserIsRefused() {
        when(props.isDemo()).thenReturn(true);
        user.setId((long) Constants.DEMO_USER_ID);

        ResponseEntity<Body> result = controller.cancelAccount(params(RAW_PASSWORD), request, response);

        assertEquals(Body.FAILURE, body(result).getStatus());
        verify(userService, never()).cancel(any());
    }
}
