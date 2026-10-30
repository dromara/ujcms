package com.ujcms.cms.core.component;

import com.ujcms.cms.core.component.UserBatchService.Defaults;
import com.ujcms.cms.core.component.UserBatchService.Issue;
import com.ujcms.cms.core.component.UserBatchService.Plan;
import com.ujcms.cms.core.component.UserBatchService.RoleChange;
import com.ujcms.cms.core.component.UserBatchService.RolesPlan;
import com.ujcms.cms.core.component.UserBatchService.Row;
import com.ujcms.cms.core.component.UserBatchService.UserIssue;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.security.SeparationOfDuties;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.RoleService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.common.web.exception.Http403Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量新建用户的行规则（docs/spec/user-management/plan.md）：用户名、手机号、邮箱不能与已有用户重复、也不能在同一批里重复，
 * 密码按安全设置核对，组织须在操作员的管理范围之内，用户组须是常规组；新用户的等级是操作员的下一级、首次登录须改密。
 * 另有给已有用户批量加减角色的规则（D9）：只加减指定的角色，用户须在操作员的管理范围之内，要加的角色等级不能高于用户
 */
class UserBatchServiceTest {
    private static final long HEAD_OFFICE = 1L;
    private static final long TECH = 2L;
    private static final long ELSEWHERE = 9L;
    private static final long STAFF_GROUP = 10L;
    private static final long IP_GROUP = 11L;
    private static final long EDITOR_ROLE = 20L;
    private static final long CHIEF_ROLE = 21L;

    private UserService userService;
    private OrgService orgService;
    private GroupService groupService;
    private RoleService roleService;
    private SeparationOfDuties separationOfDuties;
    private PasswordEncoder passwordEncoder;
    private UserBatchService service;
    private final Config config = new Config();
    private final User operator = new User();

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        orgService = mock(OrgService.class);
        groupService = mock(GroupService.class);
        roleService = mock(RoleService.class);
        separationOfDuties = mock(SeparationOfDuties.class);
        passwordEncoder = mock(PasswordEncoder.class);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getUnique()).thenReturn(config);
        // Config 每次按 JSON 解析出新的 Security：要改设置须整块写回
        Config.Security security = new Config.Security();
        security.setPasswordMinLength(6);
        security.setPasswordMaxLength(20);
        security.setPasswordStrength(Config.Security.PASSWORD_STRENGTH_1);
        config.setSecurity(security);
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hashed:" + invocation.getArgument(0));
        operator.setId(100L);
        operator.setOrgId(HEAD_OFFICE);
        operator.setRank((short) 3);
        when(orgService.select(HEAD_OFFICE)).thenReturn(org(HEAD_OFFICE, "总部"));
        when(orgService.select(TECH)).thenReturn(org(TECH, "技术部"));
        when(orgService.select(ELSEWHERE)).thenReturn(org(ELSEWHERE, "别处"));
        when(orgService.isDescendant(eq(HEAD_OFFICE), anyLong())).thenAnswer(invocation ->
                (long) invocation.getArgument(1) != ELSEWHERE);
        when(groupService.select(STAFF_GROUP)).thenReturn(group(STAFF_GROUP, Group.TYPE_REGULAR));
        when(groupService.select(IP_GROUP)).thenReturn(group(IP_GROUP, Group.TYPE_IP));
        // 操作员等级 3，新用户等级 4：编辑（等级 5）可以授予，主编（等级 2）高于新用户
        when(roleService.select(EDITOR_ROLE)).thenReturn(role(EDITOR_ROLE, "编辑", (short) 5));
        when(roleService.select(CHIEF_ROLE)).thenReturn(role(CHIEF_ROLE, "主编", (short) 2));
        service = new UserBatchService(userService, orgService, groupService, roleService, configService,
                separationOfDuties, passwordEncoder, new StaticMessageSource());
    }

    private static Role role(long id, String name, short rank) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setRank(rank);
        return role;
    }

    private static Org org(long id, String name) {
        Org org = new Org();
        org.setId(id);
        org.setName(name);
        return org;
    }

    private static Group group(long id, short type) {
        Group group = new Group();
        group.setId(id);
        group.setName("g" + id);
        group.setType(type);
        return group;
    }

    private static User user(long id, String username, long orgId, short rank, Role... roles) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setOrgId(orgId);
        user.setRank(rank);
        user.setRoleList(new ArrayList<>(List.of(roles)));
        return user;
    }

    private RolesPlan planRoles(List<Long> userIds, List<Role> roles, boolean remove) {
        return service.planRoles(userIds, roles, remove, operator, Locale.CHINESE);
    }

    private static Row row(int number, String username, String mobile, String email, String password) {
        return new Row(number, username, "姓名", mobile, email, password, null, null, null, null);
    }

    private static Row rowWithRoles(int number, String username, List<Long> roleIds) {
        return new Row(number, username, null, null, null, null, null, null, null, roleIds);
    }

    private Plan plan(List<Row> rows, Defaults defaults) {
        return service.plan(rows, defaults, operator, Locale.CHINESE);
    }

    private static Defaults defaults(String password, boolean skipExisting) {
        return new Defaults(HEAD_OFFICE, STAFF_GROUP, password, List.of(), skipExisting);
    }

    private static Defaults defaultsWithRoles(List<Long> roleIds) {
        return new Defaults(HEAD_OFFICE, STAFF_GROUP, null, roleIds, false);
    }

    @Test
    void aValidRowBecomesAUserOneRankBelowTheOperatorWithTheSharedPassword() {
        Plan plan = plan(List.of(row(1, " alice ", "13800138000", "alice@example.com", null)), defaults("Secret1", false));

        assertEquals(List.of(), plan.issues());
        assertEquals(1, plan.prepared().size());
        User user = plan.prepared().get(0).user();
        assertEquals("alice", user.getUsername());
        assertEquals("姓名", user.getRealName());
        assertEquals("13800138000", user.getMobile());
        assertEquals("alice@example.com", user.getEmail());
        assertEquals(HEAD_OFFICE, user.getOrgId());
        assertEquals(STAFF_GROUP, user.getGroupId());
        assertEquals((short) 4, user.getRank());
        assertEquals(User.GENDER_SECRET, user.getGender());
        assertTrue(user.getPwdMustChange());
        assertEquals("hashed:Secret1", user.getPassword());
    }

    @Test
    void aRowPasswordWinsOverTheSharedOneAndNoPasswordLeavesTheUserWithoutOne() {
        Plan plan = plan(List.of(row(1, "alice", null, null, "Own12345"), row(2, "bob", null, null, null)),
                defaults(null, false));

        assertEquals(List.of(), plan.issues());
        assertEquals("hashed:Own12345", plan.prepared().get(0).user().getPassword());
        assertNull(plan.prepared().get(1).user().getPassword());
    }

    @Test
    void thePasswordFollowsTheSecuritySettings() {
        Plan plan = plan(List.of(row(1, "alice", null, null, "short"), row(2, "bob", null, null, "lettersonly"),
                row(3, "carol", null, null, "Good1234")), defaults(null, false));

        assertEquals(List.of("user.batch.row.passwordLength", "user.batch.row.passwordStrength.1"),
                plan.issues().stream().map(Issue::reason).toList());
        assertEquals(List.of(3), plan.prepared().stream().map(UserBatchService.Prepared::row).toList());
    }

    @Test
    void usernamesMustBePresentWellFormedAndUniqueInTheBatchAndInTheDatabase() {
        when(userService.selectByUsername("taken")).thenReturn(new User());
        Plan plan = plan(List.of(row(1, "", null, null, null), row(2, "a b", null, null, null),
                row(3, "x".repeat(31), null, null, null), row(4, "Alice", null, null, null),
                row(5, "alice", null, null, null), row(6, "taken", null, null, null)), defaults(null, false));

        assertEquals(List.of("user.batch.row.usernameRequired", "user.batch.row.usernameInvalid",
                        "user.batch.row.usernameTooLong", "user.batch.row.usernameRepeated", "user.batch.row.usernameExists"),
                plan.issues().stream().map(Issue::reason).toList());
        assertEquals(List.of(1, 2, 3, 5, 6), plan.issues().stream().map(Issue::row).toList());
        assertEquals(5, plan.invalid());
        assertEquals(0, plan.skipped());
        assertEquals(1, plan.prepared().size());
    }

    @Test
    void anExistingUsernameIsSkippedWhenTheCallerAsksForIt() {
        when(userService.selectByUsername("taken")).thenReturn(new User());
        Plan plan = plan(List.of(row(1, "taken", null, null, null), row(2, "fresh", null, null, null)),
                defaults(null, true));

        assertEquals(1, plan.skipped());
        assertEquals(0, plan.invalid());
        assertEquals("user.batch.row.usernameSkipped", plan.issues().get(0).reason());
        assertEquals(List.of(2), plan.prepared().stream().map(UserBatchService.Prepared::row).toList());
    }

    @Test
    void mobilesAndEmailsAreCheckedForFormatAndUniqueness() {
        when(userService.selectByMobile("13900000000")).thenReturn(new User());
        when(userService.selectByEmail("used@example.com")).thenReturn(new User());
        Plan plan = plan(List.of(
                row(1, "u1", "abc", null, null),
                row(2, "u2", "13800138000", null, null),
                row(3, "u3", "138 0013 8000", null, null),
                row(4, "u4", "13900000000", null, null),
                row(5, "u5", null, "not-an-email", null),
                row(6, "u6", null, "a@example.com", null),
                row(7, "u7", null, "A@Example.com", null),
                row(8, "u8", null, "used@example.com", null)), defaults(null, false));

        assertEquals(List.of("user.batch.row.mobileInvalid", "user.batch.row.mobileRepeated",
                        "user.batch.row.mobileExists", "user.batch.row.emailInvalid", "user.batch.row.emailRepeated",
                        "user.batch.row.emailExists"),
                plan.issues().stream().map(Issue::reason).toList());
        assertEquals(List.of(1, 3, 4, 5, 7, 8), plan.issues().stream().map(Issue::row).toList());
    }

    @Test
    void theOrganizationMustExistAndBeManageableAndTheGroupMustBeARegularGroup() {
        Plan plan = plan(List.of(
                new Row(1, "u1", null, null, null, null, 77L, null, null, null),
                new Row(2, "u2", null, null, null, null, ELSEWHERE, null, null, null),
                new Row(3, "u3", null, null, null, null, TECH, null, null, null),
                new Row(4, "u4", null, null, null, null, null, 78L, null, null),
                new Row(5, "u5", null, null, null, null, null, IP_GROUP, null, null),
                new Row(6, "u6", null, null, null, null, null, null, (short) 2, null),
                new Row(7, "u7", null, null, null, null, null, null, (short) 5, null)), defaults(null, false));

        assertEquals(List.of("user.batch.row.orgNotFound", "user.batch.row.orgForbidden", "user.batch.row.groupNotFound",
                        "user.batch.row.groupNotRegular", "user.batch.row.genderInvalid"),
                plan.issues().stream().map(Issue::reason).toList());
        assertEquals(List.of(3, 6), plan.prepared().stream().map(UserBatchService.Prepared::row).toList());
        assertEquals(TECH, plan.prepared().get(0).user().getOrgId());
        assertEquals((short) 2, plan.prepared().get(1).user().getGender());
    }

    @Test
    void withoutDefaultsARowMustNameItsOrganizationAndGroup() {
        Plan plan = plan(List.of(row(1, "u1", null, null, null),
                new Row(2, "u2", null, null, null, null, HEAD_OFFICE, null, null, null)),
                new Defaults(null, null, null, List.of(), false));

        assertEquals(List.of("user.batch.row.orgRequired", "user.batch.row.groupRequired"),
                plan.issues().stream().map(Issue::reason).toList());
        assertTrue(plan.prepared().isEmpty());
    }

    @Test
    void aGlobalOperatorMayUseAnyOrganization() {
        User root = mock(User.class);
        when(root.hasGlobalPermission()).thenReturn(true);
        when(root.getRank()).thenReturn((short) 0);
        Plan plan = service.plan(List.of(new Row(1, "u1", null, null, null, null, ELSEWHERE, null, null, null)),
                defaults(null, false), root, Locale.CHINESE);

        assertEquals(List.of(), plan.issues());
        verify(orgService, never()).isDescendant(anyLong(), anyLong());
    }

    @Test
    void creatingWritesThroughTheUserServiceWithoutExtraOrganizations() {
        Plan plan = plan(List.of(row(1, "alice", null, null, null)), defaults(null, false));

        assertEquals(1, service.createAll(plan.prepared()));

        User user = plan.prepared().get(0).user();
        verify(userService).insert(eq(user), eq(user.getExt()), eq(List.of()), eq(List.of()));
        assertFalse(plan.prepared().isEmpty());
    }

    @Test
    void rolesComeFromTheRowOrTheDefaultsAndMustNotOutrankTheNewUser() {
        Plan plan = plan(List.of(
                rowWithRoles(1, "u1", null),
                rowWithRoles(2, "u2", List.of(EDITOR_ROLE, EDITOR_ROLE)),
                rowWithRoles(3, "u3", List.of(CHIEF_ROLE)),
                rowWithRoles(4, "u4", List.of(99L))), defaultsWithRoles(List.of(EDITOR_ROLE)));

        assertEquals(List.of("user.batch.row.roleRankTooHigh", "user.batch.row.roleNotFound"),
                plan.issues().stream().map(Issue::reason).toList());
        assertEquals(List.of(3, 4), plan.issues().stream().map(Issue::row).toList());
        // 行里没有角色：用默认角色；行里有：用行里的（去重）
        assertEquals(List.of(EDITOR_ROLE), plan.prepared().get(0).roleIds());
        assertEquals(List.of(EDITOR_ROLE), plan.prepared().get(1).roleIds());
        // 有角色的两行各校验一次；没有角色、或者角色先被拒绝的行不校验
        verify(separationOfDuties, times(2)).check(eq(User.TYPE_NORMAL), any());

        service.createAll(plan.prepared());

        User first = plan.prepared().get(0).user();
        verify(userService).insert(eq(first), eq(first.getExt()), eq(List.of()), eq(List.of(EDITOR_ROLE)));
    }

    @Test
    void aSeparationOfDutiesRefusalRejectsTheRowWithItsOwnMessage() {
        doThrow(new Http403Exception("error.separationOfDutiesAuditType")).when(separationOfDuties).check(anyShort(), any());
        Plan plan = plan(List.of(rowWithRoles(1, "u1", List.of(EDITOR_ROLE)), rowWithRoles(2, "u2", null)),
                defaultsWithRoles(List.of()));

        assertEquals(List.of("error.separationOfDutiesAuditType"), plan.issues().stream().map(Issue::reason).toList());
        // 没有角色的行不经过三权分立校验
        assertEquals(List.of(2), plan.prepared().stream().map(UserBatchService.Prepared::row).toList());
    }

    @Test
    void noMoreThanTheIssueCapIsListedButEveryRowIsCounted() {
        Row[] rows = new Row[UserBatchService.MAX_ISSUES + 5];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = row(i + 1, "", null, null, null);
        }
        Plan plan = plan(List.of(rows), defaults(null, false));

        assertEquals(UserBatchService.MAX_ISSUES, plan.issues().size());
        assertEquals(rows.length, plan.invalid());
        assertEquals(rows.length, plan.total());
        verify(userService, never()).insert(any(), any(), any(), any());
    }

    @Test
    void addingRolesKeepsTheRolesAUserAlreadyHoldsAndSkipsThoseWhoHaveThemAll() {
        Role editor = role(EDITOR_ROLE, "编辑", (short) 5);
        Role other = role(30L, "作者", (short) 6);
        User alice = user(1L, "alice", TECH, (short) 4, other);
        User bob = user(2L, "bob", TECH, (short) 4, editor);
        when(userService.select(1L)).thenReturn(alice);
        when(userService.select(2L)).thenReturn(bob);

        RolesPlan plan = planRoles(List.of(1L, 2L, 2L), List.of(editor), false);

        assertEquals(List.of(), plan.issues());
        assertEquals(1, plan.unchanged());
        assertEquals(List.of(alice), plan.changes().stream().map(RoleChange::user).toList());
        // 原有的角色在前，新加的在后
        assertEquals(List.of(30L, EDITOR_ROLE), plan.changes().get(0).roleIds());
        // 只对角色会变的用户、按改写后的全部角色校验三权分立
        verify(separationOfDuties).check(eq(User.TYPE_NORMAL), eq(List.of(other, editor)));
        verify(separationOfDuties, times(1)).check(anyShort(), any());

        assertEquals(1, service.applyRoles(plan.changes()));
        verify(userService).update(eq(alice), eq(List.of(30L, EDITOR_ROLE)));
        verify(userService, never()).update(eq(bob), anyList());
    }

    @Test
    void removingRolesLeavesTheOthersAndNeverConsultsSeparationOfDuties() {
        Role editor = role(EDITOR_ROLE, "编辑", (short) 5);
        Role chief = role(CHIEF_ROLE, "主编", (short) 2);
        User alice = user(1L, "alice", TECH, (short) 4, editor, chief);
        User bob = user(2L, "bob", TECH, (short) 4, editor);
        when(userService.select(1L)).thenReturn(alice);
        when(userService.select(2L)).thenReturn(bob);

        RolesPlan plan = planRoles(List.of(1L, 2L), List.of(chief), true);

        assertEquals(List.of(), plan.issues());
        assertEquals(1, plan.unchanged());
        assertEquals(List.of(alice), plan.changes().stream().map(RoleChange::user).toList());
        assertEquals(List.of(EDITOR_ROLE), plan.changes().get(0).roleIds());
        verify(separationOfDuties, never()).check(anyShort(), any());
    }

    @Test
    void usersOutsideTheOperatorsReachAndMissingUsersAreRefusedOneByOne() {
        Role editor = role(EDITOR_ROLE, "编辑", (short) 5);
        when(userService.select(1L)).thenReturn(user(1L, "elsewhere", ELSEWHERE, (short) 4));
        when(userService.select(2L)).thenReturn(user(2L, "boss", TECH, (short) 2));
        when(userService.select(3L)).thenReturn(user(3L, "peer", TECH, (short) 3));

        RolesPlan plan = planRoles(List.of(1L, 2L, 99L, 3L), List.of(editor), false);

        assertEquals(List.of("user.batchRoles.orgForbidden", "user.batchRoles.rankForbidden", "user.batchRoles.notFound"),
                plan.issues().stream().map(UserIssue::reason).toList());
        assertEquals(List.of("elsewhere", "boss", ""), plan.issues().stream().map(UserIssue::username).toList());
        assertEquals(List.of(1L, 2L, 99L), plan.issues().stream().map(UserIssue::userId).toList());
        // 与操作员同级的用户可以管理
        assertEquals(List.of(3L), plan.changes().stream().map(change -> change.user().getId()).toList());
    }

    @Test
    void aRoleAboveTheUsersRankCannotBeAddedUnlessTheUserAlreadyHoldsIt() {
        Role chief = role(CHIEF_ROLE, "主编", (short) 2);
        when(userService.select(1L)).thenReturn(user(1L, "alice", TECH, (short) 4));
        when(userService.select(2L)).thenReturn(user(2L, "bob", TECH, (short) 4, chief));

        RolesPlan plan = planRoles(List.of(1L, 2L), List.of(chief), false);

        assertEquals(List.of("user.batchRoles.roleRankTooHigh"), plan.issues().stream().map(UserIssue::reason).toList());
        assertEquals(1, plan.unchanged());
        assertTrue(plan.changes().isEmpty());
    }

    @Test
    void aSeparationOfDutiesRefusalReportsTheUserWithItsOwnMessage() {
        Role editor = role(EDITOR_ROLE, "编辑", (short) 5);
        when(userService.select(1L)).thenReturn(user(1L, "alice", TECH, (short) 4));
        doThrow(new Http403Exception("error.separationOfDutiesAuditType")).when(separationOfDuties).check(anyShort(), any());

        RolesPlan plan = planRoles(List.of(1L), List.of(editor), false);

        assertEquals(List.of("error.separationOfDutiesAuditType"), plan.issues().stream().map(UserIssue::reason).toList());
        assertTrue(plan.changes().isEmpty());
    }
}
