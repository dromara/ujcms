package com.ujcms.cms.core.security;

import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.SecurityProperties;
import com.ujcms.cms.core.support.StaticProps;
import com.ujcms.common.web.exception.Http403Exception;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.ujcms.cms.core.security.SeparationOfDuties.Power;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三权分立（等保三级 访问控制）：权限归类、(b) 审计双向隔离、(c) 禁止全能账号；root 中和；关闭时不校验。
 */
class SeparationOfDutiesTest {

    @AfterEach
    void resetFlag() {
        StaticProps.setSeparationOfDuties(false);
    }

    // region 权限归类
    @Test
    void powersOf_classifiesByModule() {
        assertEquals(EnumSetOf(Power.AUDIT), SeparationOfDuties.powersOf("operationLog:list"));
        assertEquals(EnumSetOf(Power.AUDIT), SeparationOfDuties.powersOf("loginLog:delete"));
        assertEquals(EnumSetOf(Power.SECURITY), SeparationOfDuties.powersOf("user:create"));
        assertEquals(EnumSetOf(Power.SECURITY), SeparationOfDuties.powersOf("role:updatePermission"));
        assertEquals(EnumSetOf(Power.SECURITY), SeparationOfDuties.powersOf("org:update"));
        assertEquals(EnumSetOf(Power.SECURITY), SeparationOfDuties.powersOf("group:create"));
        assertEquals(EnumSetOf(Power.SECURITY), SeparationOfDuties.powersOf("config:security:update"));
        assertEquals(EnumSetOf(Power.SYSTEM), SeparationOfDuties.powersOf("config:base:update"));
        assertEquals(EnumSetOf(Power.SYSTEM), SeparationOfDuties.powersOf("article:create"));
        assertEquals(EnumSetOf(Power.SYSTEM), SeparationOfDuties.powersOf("site:create"));
    }

    @Test
    void powersOf_unrestrictedReturnsEmpty() {
        assertTrue(SeparationOfDuties.powersOf("menu.user").isEmpty());
        assertTrue(SeparationOfDuties.powersOf("backend").isEmpty());
        assertTrue(SeparationOfDuties.powersOf("password:update").isEmpty());
        assertTrue(SeparationOfDuties.powersOf("homepage:systemInfo").isEmpty());
        assertTrue(SeparationOfDuties.powersOf("machine:license").isEmpty());
    }

    @Test
    void powersOf_wildcardSpansAllThree() {
        Set<Power> powers = SeparationOfDuties.powersOf("*");
        assertTrue(powers.contains(Power.AUDIT));
        assertTrue(powers.contains(Power.SECURITY));
        assertTrue(powers.contains(Power.SYSTEM));
    }
    // endregion

    // region (b) 审计双向隔离
    @Test
    void auditPerm_allowedOnlyForAuditAdmin() {
        assertDoesNotThrow(() -> SeparationOfDuties.checkPermissions(User.TYPE_AUDIT,
                Set.of("operationLog:list", "loginLog:list", "backend")));
        assertKey("error.separationOfDutiesAuditType", User.TYPE_SYSTEM, Set.of("operationLog:list"));
        assertKey("error.separationOfDutiesAuditType", User.TYPE_SECURITY, Set.of("loginLog:list"));
        assertKey("error.separationOfDutiesAuditType", User.TYPE_NORMAL, Set.of("operationLog:delete"));
    }

    @Test
    void auditAdmin_mayNotHoldNonAuditPerm() {
        assertKey("error.separationOfDutiesAuditOnly", User.TYPE_AUDIT,
                Set.of("operationLog:list", "article:create"));
        assertKey("error.separationOfDutiesAuditOnly", User.TYPE_AUDIT, Set.of("user:create"));
    }
    // endregion

    // region (c) 禁止全能账号 + 允许的组合
    @Test
    void wildcardRejectedForEveryType() {
        assertKey("error.separationOfDutiesAllThree", User.TYPE_SYSTEM, Set.of("*"));
        assertKey("error.separationOfDutiesAllThree", User.TYPE_SECURITY, Set.of("*"));
        assertKey("error.separationOfDutiesAllThree", User.TYPE_AUDIT, Set.of("*"));
        assertKey("error.separationOfDutiesAllThree", User.TYPE_NORMAL, Set.of("*"));
    }

    @Test
    void systemAndSecurityMayCoexist() {
        // 计划仅强制：审计隔离 + 不可三类全占；系统与安全可同时持有（“系统配置类”）
        assertDoesNotThrow(() -> SeparationOfDuties.checkPermissions(User.TYPE_SYSTEM,
                Set.of("article:create", "user:create", "config:base:update")));
        assertDoesNotThrow(() -> SeparationOfDuties.checkPermissions(User.TYPE_SECURITY,
                Set.of("user:create", "role:create", "site:create")));
    }

    @Test
    void unrestrictedOnlyAlwaysAllowed() {
        assertDoesNotThrow(() -> SeparationOfDuties.checkPermissions(User.TYPE_AUDIT,
                Set.of("backend", "menu.personal", "password:update")));
        assertDoesNotThrow(() -> SeparationOfDuties.checkPermissions(User.TYPE_SYSTEM, Set.of()));
    }
    // endregion

    // region collectPermissions
    @Test
    void collectPermissions_splitsCsvAndFoldsAllPermission() {
        Role csv = new Role();
        csv.setPermission("article:create, channel:list ,user:create");
        Role all = new Role();
        all.setAllPermission(true);
        Set<String> perms = SeparationOfDuties.collectPermissions(List.of(csv, all));
        assertTrue(perms.contains("article:create"));
        assertTrue(perms.contains("channel:list"));
        assertTrue(perms.contains("user:create"));
        assertTrue(perms.contains("*"));
    }
    // endregion

    // region check() 开关
    @Test
    void check_noOpWhenDisabled() {
        SecurityProperties props = new SecurityProperties();
        props.setSeparationOfDuties(false);
        SeparationOfDuties sod = new SeparationOfDuties(props);
        Role audit = new Role();
        audit.setPermission("operationLog:list");
        // 关闭时即便违规也不抛出
        assertDoesNotThrow(() -> sod.check(User.TYPE_SYSTEM, List.of(audit)));
    }

    @Test
    void check_enforcesWhenEnabled() {
        SecurityProperties props = new SecurityProperties();
        props.setSeparationOfDuties(true);
        SeparationOfDuties sod = new SeparationOfDuties(props);
        assertTrue(sod.isEnabled());
        Role audit = new Role();
        audit.setPermission("operationLog:list");
        assertThrows(Http403Exception.class, () -> sod.check(User.TYPE_SYSTEM, List.of(audit)));
        assertDoesNotThrow(() -> sod.check(User.TYPE_AUDIT, List.of(audit)));
    }
    // endregion

    // region root 中和
    @Test
    void root_neutralizedWhenEnabled() {
        User root = new User();
        root.setId(1L);
        root.setStatus(User.STATUS_ENABLED);
        User other = new User();
        other.setId(2L);
        other.setStatus(User.STATUS_ENABLED);

        StaticProps.setSeparationOfDuties(false);
        assertTrue(root.isRoot());
        assertTrue(root.isEnabled());

        StaticProps.setSeparationOfDuties(true);
        assertFalse(root.isRoot(), "三权分立开启后 root 不再是超级管理员");
        assertFalse(root.isEnabled(), "三权分立开启后 root 被强制停用");
        // 即便库中状态被改回正常，仍判为停用（不可重新启用）
        root.setStatus(User.STATUS_ENABLED);
        assertFalse(root.isEnabled());
        // 普通用户不受影响
        assertFalse(other.isRoot());
        assertTrue(other.isEnabled());
    }
    // endregion

    private static java.util.EnumSet<Power> EnumSetOf(Power power) {
        return java.util.EnumSet.of(power);
    }

    private static void assertKey(String key, short userType, Set<String> perms) {
        Http403Exception ex = assertThrows(Http403Exception.class,
                () -> SeparationOfDuties.checkPermissions(userType, perms));
        assertEquals(key, ex.getMessage());
    }
}
