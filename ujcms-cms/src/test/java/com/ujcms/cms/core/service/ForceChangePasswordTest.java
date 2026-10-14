package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.UserExt;
import com.ujcms.cms.core.mapper.UserExtMapper;
import com.ujcms.cms.core.mapper.UserMapper;
import org.apache.ibatis.reflection.Reflector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证“首次登录强制改密”标记的置位 / 清除：
 * 管理员重置口令（4 参 mustChangeNext=true）置位；用户自助改密 / 验证码自助重置（3 参）清除。
 */
class ForceChangePasswordTest {
    private UserMapper mapper;
    private UserExtMapper extMapper;
    private UserService service;

    @BeforeEach
    void setUp() {
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn("{noop}encoded");
        mapper = mock(UserMapper.class);
        extMapper = mock(UserExtMapper.class);
        // 仅 passwordEncoder / mapper / extMapper 参与本测试，其余依赖不被触达，传 null 即可
        service = new UserService(passwordEncoder, null, null, null, mapper, extMapper,
                null, null, null, null, null, null);
    }

    @Test
    void selfServiceChangeClearsFlag() {
        User user = new User();
        user.setPwdMustChange(true);
        service.updatePassword(user, new UserExt(), "newPassword");
        assertFalse(user.getPwdMustChange(), "自助改密后应清除强制改密标记");
        verify(mapper).update(user);
    }

    @Test
    void adminResetSetsFlag() {
        User user = new User();
        user.setPwdMustChange(false);
        service.updatePassword(user, new UserExt(), "tempPassword", true);
        assertTrue(user.getPwdMustChange(), "管理员重置口令后应置位强制改密标记");
        verify(mapper).update(user);
    }

    @Test
    void defaultOverloadClearsFlag() {
        User user = new User();
        user.setPwdMustChange(true);
        // 3 参等价于 mustChangeNext=false
        service.updatePassword(user, new UserExt(), "newPassword", false);
        assertFalse(user.getPwdMustChange());
    }

    @Test
    void pwdMustChangeGetterIsUnambiguousForMyBatis() throws Exception {
        // 回归：曾因 getPwdMustChange()/isPwdMustChange() 双 getter 触发 MyBatis
        // “Illegal overloaded getter method with ambiguous type”，导致更新用户时报错。
        User user = new User();
        user.setPwdMustChange(true);
        Object value = new Reflector(User.class).getGetInvoker("pwdMustChange").invoke(user, null);
        assertEquals(Boolean.TRUE, value);
    }
}
