package com.ujcms.cms.core.security;

import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.support.SecurityProperties;
import com.ujcms.common.web.exception.Http403Exception;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * 三权分立（等保三级 访问控制）权限校验（G4）。
 *
 * <p>开关为 {@link SecurityProperties#isSeparationOfDuties()}（yaml-only，D15）。关闭时本组件不做任何校验，
 * 开箱行为不变。开启时，按用户类型对“拟持有的全部功能权限”（来自所分配角色的并集）校验：
 *
 * <ul>
 *   <li><b>(b) 审计隔离（双向）</b>：审计权限（{@code operationLog:*} / {@code loginLog:*}）仅可授予
 *       审计管理员（{@link User#TYPE_AUDIT}）；且审计管理员<strong>只能</strong>持有审计权限，
 *       不得持有系统/安全等其他权限。</li>
 *   <li><b>(c) 禁止全能账号</b>：任何单一账号不得同时持有系统/安全/审计三类权限。其直接后果是开启后
 *       {@code *}（“所有权限”）不可授予任何账号——root 被中和后系统不再有超级管理员。</li>
 * </ul>
 *
 * <p>权限按模块前缀归入三种“权能”之一（{@link Power}）；公共/个人类权限（{@code menu.*}、{@code backend}、
 * 口令/主页/机器码等）不计入任何权能。{@code *} 视为同时持有三类权能。
 *
 * @author PONY
 * @see SecurityProperties#isSeparationOfDuties()
 */
@Component
public class SeparationOfDuties {
    /** 三种相互制约的权能。 */
    enum Power {
        /** 审计：操作日志 / 登录日志。 */
        AUDIT,
        /** 安全 / 授权：用户、角色、组织、用户组、安全设置。 */
        SECURITY,
        /** 系统 / 业务：其余全部管理类权限。 */
        SYSTEM
    }

    private final SecurityProperties securityProperties;

    public SeparationOfDuties(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public boolean isEnabled() {
        return securityProperties.isSeparationOfDuties();
    }

    /**
     * 校验某用户类型与其拟持有角色集合是否满足三权分立约束；关闭时直接返回。
     *
     * @param userType 用户类型（{@link User#TYPE_SYSTEM} / {@link User#TYPE_SECURITY} / {@link User#TYPE_AUDIT} 等）
     * @param roles    拟分配（或已持有）的角色集合
     * @throws Http403Exception 违反约束时抛出，消息为对应的 i18n key
     */
    public void check(short userType, Collection<? extends Role> roles) {
        if (!isEnabled()) {
            return;
        }
        checkPermissions(userType, collectPermissions(roles));
    }

    /** 汇总角色集合的功能权限并集；{@code allPermission} 角色折算为 {@code *}。 */
    static Set<String> collectPermissions(Collection<? extends Role> roles) {
        Set<String> perms = new HashSet<>();
        for (Role role : roles) {
            if (Boolean.TRUE.equals(role.getAllPermission())) {
                perms.add("*");
            }
            String permission = role.getPermission();
            if (StringUtils.isNotBlank(permission)) {
                for (String token : StringUtils.split(permission, ",")) {
                    String trimmed = token.trim();
                    if (!trimmed.isEmpty()) {
                        perms.add(trimmed);
                    }
                }
            }
        }
        return perms;
    }

    /** 对“拟持有权限并集”按用户类型施加 (b)/(c) 约束。包级可见以便单元测试。 */
    static void checkPermissions(short userType, Set<String> perms) {
        EnumSet<Power> powers = EnumSet.noneOf(Power.class);
        for (String perm : perms) {
            powers.addAll(powersOf(perm));
        }
        boolean hasAudit = powers.contains(Power.AUDIT);
        boolean hasSecurity = powers.contains(Power.SECURITY);
        boolean hasSystem = powers.contains(Power.SYSTEM);

        // (c) 禁止单一账号同时拥有系统/安全/审计三类权限（含 * 全权限）
        if (hasAudit && hasSecurity && hasSystem) {
            throw new Http403Exception("error.separationOfDutiesAllThree");
        }
        // (b) 审计权限仅可授予审计管理员
        if (hasAudit && userType != User.TYPE_AUDIT) {
            throw new Http403Exception("error.separationOfDutiesAuditType");
        }
        // (b) 审计管理员只能持有审计权限，不得持有系统/安全等其他权限
        if (userType == User.TYPE_AUDIT && (hasSecurity || hasSystem)) {
            throw new Http403Exception("error.separationOfDutiesAuditOnly");
        }
    }

    /** 将单条权限归入其所属权能；公共/个人类权限返回空集；{@code *} 返回全部三类。 */
    static Set<Power> powersOf(String perm) {
        if ("*".equals(perm)) {
            return EnumSet.allOf(Power.class);
        }
        if (isUnrestricted(perm)) {
            return EnumSet.noneOf(Power.class);
        }
        if (isAudit(perm)) {
            return EnumSet.of(Power.AUDIT);
        }
        if (isSecurity(perm)) {
            return EnumSet.of(Power.SECURITY);
        }
        return EnumSet.of(Power.SYSTEM);
    }

    /** 公共/个人类权限：菜单可见性、后台通用、口令/主页/机器码等——不代表任何权能。 */
    static boolean isUnrestricted(String perm) {
        if (perm.startsWith("menu.")) {
            return true;
        }
        String module = moduleOf(perm);
        return "backend".equals(module) || "password".equals(module) || "personal".equals(module)
                || "homepage".equals(module) || "machine".equals(module) || "captcha".equals(module);
    }

    /** 审计权能：操作日志 / 登录日志。 */
    static boolean isAudit(String perm) {
        String module = moduleOf(perm);
        return "operationLog".equals(module) || "loginLog".equals(module);
    }

    /** 安全 / 授权权能：用户、角色、组织、用户组管理，以及安全设置（{@code config:security:*}）。 */
    static boolean isSecurity(String perm) {
        if (perm.startsWith("config:security")) {
            return true;
        }
        String module = moduleOf(perm);
        return "user".equals(module) || "role".equals(module) || "org".equals(module) || "group".equals(module);
    }

    /** 取权限的模块名（首个冒号之前）；无冒号则为整串。 */
    private static String moduleOf(String perm) {
        int idx = perm.indexOf(':');
        return idx < 0 ? perm : perm.substring(0, idx);
    }
}
