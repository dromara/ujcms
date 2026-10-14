package com.ujcms.cms.core.support;

import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 未解锁的商业权限码不进入安全上下文（{@link User#getAuthorities()}）。
 * <p>
 * 类路径裁剪只能拦住 t{N} 包里的接口；留在 core 的接口（全站置灰、站点全文重建、组织权限、政务/AI 站点设置块）
 * 仅靠权限码分级，而社区版角色树为推广显示带徽标的商业节点、角色 CSV 也可能自更高版本迁移而来，
 * 因此在权限集合处统一按 {@link Commercial#isUnlocked} 过滤。级别为静态只升不降，测试后经反射还原。
 */
class CommercialLockedAuthoritiesTest {
    private static final String CORE = "article:list";
    private static final String T1 = "importData:show";
    private static final String T2 = "config:grey:update";
    private static int savedTier;

    @BeforeAll
    static void saveTier() throws ReflectiveOperationException {
        savedTier = Commercial.getTier();
        setTier(0);
    }

    @AfterAll
    static void restoreTier() throws ReflectiveOperationException {
        setTier(savedTier);
    }

    private static void setTier(int tier) throws ReflectiveOperationException {
        Field field = Commercial.class.getDeclaredField("tier");
        field.setAccessible(true);
        field.setInt(null, tier);
    }

    private static Set<String> authorities(String permissions) {
        Role role = new Role();
        role.setPermission(permissions);
        User user = new User();
        user.setRoleList(List.of(role));
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    void communityDropsEveryLockedCodeAndKeepsCoreOnes() throws ReflectiveOperationException {
        setTier(0);
        Set<String> authorities = authorities(String.join(",", CORE, T1, T2));
        assertEquals(Set.of(CORE, Role.PERMISSION_BACKEND), authorities,
                "社区版下 T1/T2 权限码须被过滤，核心码与 backend 保留");
    }

    @Test
    void tierUnlocksItsOwnCodesButNotHigherOnes() throws ReflectiveOperationException {
        setTier(1);
        Set<String> authorities = authorities(String.join(",", CORE, T1, T2));
        assertTrue(authorities.contains(T1), "T1 下 T1 权限码已解锁");
        assertTrue(!authorities.contains(T2), "T1 下 T2 权限码仍被过滤");
        setTier(2);
        assertTrue(authorities(T2).contains(T2), "T2 下 T2 权限码已解锁");
    }

    @Test
    void everyTieredCodeIsLockedAtCommunity() throws ReflectiveOperationException {
        setTier(0);
        Set<String> authorities = authorities(String.join(",", Commercial.getPermissionTiers().keySet()));
        assertEquals(Set.of(Role.PERMISSION_BACKEND), authorities, "社区版下所有分级权限码都不进入安全上下文");
    }
}
