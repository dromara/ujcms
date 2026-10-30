package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.security.PiiBlindIndex;
import com.ujcms.cms.core.security.SeparationOfDuties;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.RoleService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.common.web.exception.Http403Exception;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 批量新建用户的规则（docs/spec/user-management/plan.md）：把一批待建的用户核对成可以创建的记录，再逐个创建。
 * 控制台的“批量录入”（core）与商业版的“导入用户”（T1，表格）共用——两条路径只负责把各自的输入变成 {@link Row}。
 * <p>
 * 规则与控制台的单个“新增”一致：用户名、手机号、邮箱不能与已有用户重复（手机号、邮箱按盲索引比对，与数据库的唯一规则相同），
 * 也不能在同一批里重复；组织须在操作员的管理范围之内（有全局数据权限，或是操作员组织的下级）；用户组须是常规组；
 * 密码按安全设置的长度与强度核对。新用户的等级是操作员的下一级，首次登录须改密（仅在安全设置开启时于登录处生效）。
 * 角色可选（D8）：给了角色时按“权限设置”的规则核对——角色的等级不能高于新用户、三权分立开启时按用户类型校验；
 * 是否允许给角色（须另有权限设置的权限）由控制器判定。
 * <p>
 * 另有给已有用户批量加减角色的核对（D9，{@link #planRoles}）：只加上或去掉指定的角色，其余角色不动，规则同样是“权限设置”的。
 * <p>
 * 不依赖当前请求：操作员、语言都由调用方传入。
 *
 * @author PONY
 */
@Component
public class UserBatchService {
    /**
     * 报告里最多列出的问题行数。统计数字不受此限
     */
    public static final int MAX_ISSUES = 200;
    static final int USERNAME_MAX = 30;
    static final int REAL_NAME_MAX = 50;
    static final int EMAIL_MAX = 50;
    /**
     * 手机号的写法，与控制台用户表单一致
     */
    private static final Pattern MOBILE = Pattern.compile("^\\+?[\\d\\s()\\-./]{5,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    /**
     * 用户名里不能有的字符：空白与控制字符
     */
    private static final Pattern USERNAME_FORBIDDEN = Pattern.compile("[\\s\\p{Cntrl}]");

    private final UserService userService;
    private final OrgService orgService;
    private final GroupService groupService;
    private final RoleService roleService;
    private final ConfigService configService;
    private final SeparationOfDuties separationOfDuties;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;

    public UserBatchService(UserService userService, OrgService orgService, GroupService groupService,
                            RoleService roleService, ConfigService configService,
                            SeparationOfDuties separationOfDuties, PasswordEncoder passwordEncoder,
                            MessageSource messageSource) {
        this.userService = userService;
        this.orgService = orgService;
        this.groupService = groupService;
        this.roleService = roleService;
        this.configService = configService;
        this.separationOfDuties = separationOfDuties;
        this.passwordEncoder = passwordEncoder;
        this.messageSource = messageSource;
    }

    /**
     * 待建的一个用户。文字都是原样（未去空白），{@code null} 与空白同义
     *
     * @param number   行号，给操作员对照用（表格里的行号、录入表格里的序号）
     * @param password 明文密码；为空时用 {@link Defaults#password()}
     * @param orgId    组织；为空时用 {@link Defaults#orgId()}
     * @param groupId  用户组；为空时用 {@link Defaults#groupId()}
     * @param gender   性别；为空时为保密
     * @param roleIds  角色；为空时用 {@link Defaults#roleIds()}
     */
    public record Row(int number, @Nullable String username, @Nullable String realName, @Nullable String mobile,
                      @Nullable String email, @Nullable String password, @Nullable Long orgId, @Nullable Long groupId,
                      @Nullable Short gender, @Nullable List<Long> roleIds) {
    }

    /**
     * 一批共用的默认值
     *
     * @param password     明文的初始密码；为空且行里也没有密码时，用户没有密码（须由管理员重置后才能登录）
     * @param roleIds      行里没有角色时分配的角色；可为空
     * @param skipExisting 用户名已存在的行是跳过（导入）还是算作问题（录入：全有或全无，须改正）
     */
    public record Defaults(@Nullable Long orgId, @Nullable Long groupId, @Nullable String password,
                           List<Long> roleIds, boolean skipExisting) {
    }

    /**
     * 有问题的一行，或被跳过的一行
     *
     * @param row      行号
     * @param username 用户名，可能为空
     * @param reason   原因，已按操作员的语言写好
     */
    public record Issue(int row, String username, String reason) {
    }

    /**
     * 核对通过、可以创建的一行：{@code user} 已填好（密码已散列），{@code roleIds} 是核对过的角色，直接交给 {@link #create}
     */
    public record Prepared(int row, User user, List<Long> roleIds) {
    }

    /**
     * 核对结果
     *
     * @param total    行数
     * @param prepared 可以创建的行
     * @param skipped  因用户名已存在而跳过的行数
     * @param invalid  有问题、不能创建的行数
     * @param issues   被跳过的、有问题的行，最多 {@link #MAX_ISSUES} 条
     */
    public record Plan(int total, List<Prepared> prepared, int skipped, int invalid, List<Issue> issues) {
    }

    /**
     * 组织是否在操作员的管理范围之内：有全局数据权限，或者是操作员所在组织的下级（含本级）。与单个新增的规则相同
     */
    public boolean permitsOrg(User operator, Long orgId) {
        return operator.hasGlobalPermission() || orgService.isDescendant(operator.getOrgId(), orgId);
    }

    /**
     * 核对一批。不写库
     */
    public Plan plan(List<Row> rows, Defaults defaults, User operator, Locale locale) {
        Run run = new Run(defaults, operator, configService.getUnique().getSecurity());
        List<Prepared> prepared = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        int skipped = 0;
        int invalid = 0;
        for (Row row : rows) {
            Verdict verdict = check(row, run);
            if (verdict.prepared != null) {
                prepared.add(verdict.prepared);
                continue;
            }
            if (verdict.skipped) {
                skipped++;
            } else {
                invalid++;
            }
            if (issues.size() < MAX_ISSUES) {
                issues.add(new Issue(row.number(), StringUtils.abbreviate(StringUtils.trimToEmpty(row.username()), 60),
                        messageSource.getMessage(verdict.code, verdict.args, verdict.code, locale)));
            }
        }
        return new Plan(rows.size(), prepared, skipped, invalid, issues);
    }

    /**
     * 创建一个用户。走与控制台新增相同的保存路径（自定义字段拆存、盲索引、附件引用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(Prepared prepared) {
        User user = prepared.user();
        userService.insert(user, user.getExt(), List.of(), prepared.roleIds());
    }

    /**
     * 创建一批：全有或全无
     *
     * @return 创建的用户数
     */
    @Transactional(rollbackFor = Exception.class)
    public int createAll(List<Prepared> prepared) {
        for (Prepared row : prepared) {
            create(row);
        }
        return prepared.size();
    }

    /**
     * 保存失败时写进报告的原因
     */
    public String failedReason(Exception e, Locale locale) {
        String detail = StringUtils.abbreviate(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()), 200);
        return messageSource.getMessage("user.batch.row.failed", new Object[]{detail}, detail, locale);
    }

    /**
     * 批量加减角色里要改写的一个用户（D9）
     *
     * @param roleIds 核对过的、改写后的完整角色列表
     */
    public record RoleChange(User user, List<Long> roleIds) {
    }

    /**
     * 批量加减角色里有问题的一个用户
     *
     * @param reason 原因，已按操作员的语言写好
     */
    public record UserIssue(Long userId, String username, String reason) {
    }

    /**
     * 批量加减角色的核对结果
     *
     * @param changes   角色会变的用户
     * @param unchanged 角色不变的用户数：要添加的都已持有，或要移除的本来就没有
     * @param issues    有问题的用户，最多 {@link #MAX_ISSUES} 条
     */
    public record RolesPlan(List<RoleChange> changes, int unchanged, List<UserIssue> issues) {
    }

    /**
     * 有问题时的原因：消息键与参数
     */
    private record Refusal(String code, Object... args) {
    }

    /**
     * 核对给一批已有用户加上或去掉几个角色（D9）。不写库。只加减指定的角色，各人其余的角色不动——不是整体替换。
     * 规则与单个“权限设置”相同：用户须在操作员的管理范围之内（组织在范围内、等级不高于操作员），要加的角色等级不能高于用户，
     * 三权分立开启时按用户类型对改写后的全部角色校验；移除角色只会减少权限，不经三权分立。
     * 角色已持有（添加）或本来就没有（移除）的用户不算问题，计入 {@link RolesPlan#unchanged()}
     *
     * @param roles  要添加或移除的角色，调用方已核对存在
     * @param remove {@code true} 为移除，否则为添加
     */
    public RolesPlan planRoles(List<Long> userIds, List<Role> roles, boolean remove, User operator, Locale locale) {
        List<RoleChange> changes = new ArrayList<>();
        List<UserIssue> issues = new ArrayList<>();
        Map<Long, Boolean> permittedOrgs = new HashMap<>();
        int unchanged = 0;
        for (Long userId : userIds.stream().filter(Objects::nonNull).distinct().toList()) {
            User user = userService.select(userId);
            if (user == null) {
                addIssue(issues, userId, "", new Refusal("user.batchRoles.notFound", String.valueOf(userId)), locale);
                continue;
            }
            // 用户是否在操作员的管理范围之内：组织、等级
            if (!permittedOrgs.computeIfAbsent(user.getOrgId(), orgId -> permitsOrg(operator, orgId))) {
                addIssue(issues, userId, user.getUsername(),
                        new Refusal("user.batchRoles.orgForbidden", user.getUsername()), locale);
                continue;
            }
            if (operator.getRank() > user.getRank()) {
                addIssue(issues, userId, user.getUsername(),
                        new Refusal("user.batchRoles.rankForbidden", user.getUsername()), locale);
                continue;
            }
            List<Role> held = user.getRoleList();
            Set<Long> heldIds = held.stream().map(Role::getId).collect(Collectors.toSet());
            List<Role> target;
            if (remove) {
                Set<Long> removing = roles.stream().map(Role::getId).collect(Collectors.toSet());
                target = held.stream().filter(role -> !removing.contains(role.getId())).toList();
            } else {
                List<Role> adding = roles.stream().filter(role -> !heldIds.contains(role.getId())).toList();
                Role tooHigh = adding.stream().filter(role -> role.getRank() < user.getRank()).findFirst().orElse(null);
                if (tooHigh != null) {
                    addIssue(issues, userId, user.getUsername(),
                            new Refusal("user.batchRoles.roleRankTooHigh", tooHigh.getName(), user.getUsername()), locale);
                    continue;
                }
                target = new ArrayList<>(held);
                target.addAll(adding);
            }
            if (target.size() == held.size()) {
                unchanged++;
                continue;
            }
            if (!remove) {
                try {
                    separationOfDuties.check(user.getType(), target);
                } catch (Http403Exception e) {
                    addIssue(issues, userId, user.getUsername(), new Refusal(StringUtils.defaultString(e.getMessage())), locale);
                    continue;
                }
            }
            changes.add(new RoleChange(user, target.stream().map(Role::getId).toList()));
        }
        return new RolesPlan(changes, unchanged, issues);
    }

    private void addIssue(List<UserIssue> issues, Long userId, String username, Refusal refusal, Locale locale) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(new UserIssue(userId, username,
                    messageSource.getMessage(refusal.code(), refusal.args(), refusal.code(), locale)));
        }
    }

    /**
     * 写入批量加减角色的结果：全有或全无。与单个“权限设置”走同一条保存路径
     *
     * @return 改写的用户数
     */
    @Transactional(rollbackFor = Exception.class)
    public int applyRoles(List<RoleChange> changes) {
        for (RoleChange change : changes) {
            userService.update(change.user(), change.roleIds());
        }
        return changes.size();
    }

    private static final class Verdict {
        @Nullable
        private final Prepared prepared;
        private final boolean skipped;
        private final String code;
        private final Object[] args;

        private Verdict(@Nullable Prepared prepared, boolean skipped, String code, Object... args) {
            this.prepared = prepared;
            this.skipped = skipped;
            this.code = code;
            this.args = args;
        }

        static Verdict ok(Prepared prepared) {
            return new Verdict(prepared, false, "");
        }

        static Verdict skip(String code, Object... args) {
            return new Verdict(null, true, code, args);
        }

        static Verdict reject(String code, Object... args) {
            return new Verdict(null, false, code, args);
        }
    }

    /**
     * 一次核对里各行共用的东西：默认值、操作员、安全设置，以及逐行累积的用户名、手机号、邮箱和查过的组织、用户组
     */
    private final class Run {
        private final Defaults defaults;
        private final User operator;
        private final Config.Security security;
        private final Set<String> seenUsernames = new HashSet<>();
        private final Set<String> seenMobiles = new HashSet<>();
        private final Set<String> seenEmails = new HashSet<>();
        private final Map<Long, @Nullable Org> orgs = new HashMap<>();
        private final Map<Long, Boolean> permittedOrgs = new HashMap<>();
        private final Map<Long, @Nullable Group> groups = new HashMap<>();
        private final Map<Long, @Nullable Role> roles = new HashMap<>();

        private Run(Defaults defaults, User operator, Config.Security security) {
            this.defaults = defaults;
            this.operator = operator;
            this.security = security;
        }

        @Nullable
        private Org org(Long id) {
            return orgs.computeIfAbsent(id, orgService::select);
        }

        private boolean permitsOrg(Long id) {
            return permittedOrgs.computeIfAbsent(id, key -> UserBatchService.this.permitsOrg(operator, key));
        }

        @Nullable
        private Group group(Long id) {
            return groups.computeIfAbsent(id, groupService::select);
        }

        @Nullable
        private Role role(Long id) {
            return roles.computeIfAbsent(id, roleService::select);
        }
    }

    private Verdict check(Row row, Run run) {
        // 用户名
        String username = StringUtils.trimToEmpty(row.username());
        if (username.isEmpty()) {
            return Verdict.reject("user.batch.row.usernameRequired");
        }
        if (username.length() > USERNAME_MAX) {
            return Verdict.reject("user.batch.row.usernameTooLong", USERNAME_MAX);
        }
        if (USERNAME_FORBIDDEN.matcher(username).find()) {
            return Verdict.reject("user.batch.row.usernameInvalid", username);
        }
        // 同一批里重复：不分大小写——MySQL 的默认排序规则也不分
        if (!run.seenUsernames.add(username.toLowerCase(Locale.ROOT))) {
            return Verdict.reject("user.batch.row.usernameRepeated", username);
        }
        if (userService.selectByUsername(username) != null) {
            return run.defaults.skipExisting() ? Verdict.skip("user.batch.row.usernameSkipped", username)
                    : Verdict.reject("user.batch.row.usernameExists", username);
        }
        // 真实姓名
        String realName = StringUtils.trimToNull(row.realName());
        if (realName != null && realName.length() > REAL_NAME_MAX) {
            return Verdict.reject("user.batch.row.realNameTooLong", REAL_NAME_MAX);
        }
        // 手机号：按盲索引比对，与数据库的唯一规则相同
        String mobile = StringUtils.trimToNull(row.mobile());
        if (mobile != null) {
            if (!MOBILE.matcher(mobile).matches()) {
                return Verdict.reject("user.batch.row.mobileInvalid", mobile);
            }
            String index = PiiBlindIndex.mobileIndex(mobile);
            if (index != null && !run.seenMobiles.add(index)) {
                return Verdict.reject("user.batch.row.mobileRepeated", mobile);
            }
            if (userService.selectByMobile(mobile) != null) {
                return Verdict.reject("user.batch.row.mobileExists", mobile);
            }
        }
        // 邮箱
        String email = StringUtils.trimToNull(row.email());
        if (email != null) {
            if (email.length() > EMAIL_MAX) {
                return Verdict.reject("user.batch.row.emailTooLong", EMAIL_MAX);
            }
            if (!EMAIL.matcher(email).matches()) {
                return Verdict.reject("user.batch.row.emailInvalid", email);
            }
            String index = PiiBlindIndex.emailIndex(email);
            if (index != null && !run.seenEmails.add(index)) {
                return Verdict.reject("user.batch.row.emailRepeated", email);
            }
            if (userService.selectByEmail(email) != null) {
                return Verdict.reject("user.batch.row.emailExists", email);
            }
        }
        // 密码：行里的优先，其次是统一的初始密码；都没有就不设密码
        String password = StringUtils.defaultIfBlank(row.password(), run.defaults.password());
        if (StringUtils.isNotBlank(password)) {
            Config.Security security = run.security;
            if (password.length() < security.getPasswordMinLength() || password.length() > security.getPasswordMaxLength()) {
                return Verdict.reject("user.batch.row.passwordLength", security.getPasswordMinLength(),
                        security.getPasswordMaxLength());
            }
            if (!Pattern.matches(security.getPasswordPattern(), password)) {
                return Verdict.reject("user.batch.row.passwordStrength." + security.getPasswordStrength());
            }
        }
        // 组织
        Long orgId = row.orgId() != null ? row.orgId() : run.defaults.orgId();
        if (orgId == null) {
            return Verdict.reject("user.batch.row.orgRequired");
        }
        Org org = run.org(orgId);
        if (org == null) {
            return Verdict.reject("user.batch.row.orgNotFound", String.valueOf(orgId));
        }
        if (!run.permitsOrg(orgId)) {
            return Verdict.reject("user.batch.row.orgForbidden", org.getName());
        }
        // 用户组
        Long groupId = row.groupId() != null ? row.groupId() : run.defaults.groupId();
        if (groupId == null) {
            return Verdict.reject("user.batch.row.groupRequired");
        }
        Group group = run.group(groupId);
        if (group == null) {
            return Verdict.reject("user.batch.row.groupNotFound", String.valueOf(groupId));
        }
        if (!Objects.equals(group.getType(), Group.TYPE_REGULAR)) {
            return Verdict.reject("user.batch.row.groupNotRegular", group.getName());
        }
        // 性别
        short gender = row.gender() != null ? row.gender() : User.GENDER_SECRET;
        if (gender < 0 || gender > 2) {
            return Verdict.reject("user.batch.row.genderInvalid", String.valueOf(gender));
        }
        short rank = (short) (run.operator.getRank() + 1);
        // 角色：行里的优先，其次是统一的角色。规则与“权限设置”相同：角色的等级不能高于用户，三权分立按用户类型校验
        List<Long> roleIds = row.roleIds() != null && !row.roleIds().isEmpty() ? row.roleIds() : run.defaults.roleIds();
        List<Role> roles = new ArrayList<>(roleIds.size());
        for (Long roleId : roleIds) {
            Role role = roleId == null ? null : run.role(roleId);
            if (role == null) {
                return Verdict.reject("user.batch.row.roleNotFound", String.valueOf(roleId));
            }
            if (role.getRank() < rank) {
                return Verdict.reject("user.batch.row.roleRankTooHigh", role.getName());
            }
            roles.add(role);
        }
        User user = new User();
        if (!roles.isEmpty()) {
            try {
                separationOfDuties.check(user.getType(), roles);
            } catch (Http403Exception e) {
                return Verdict.reject(StringUtils.defaultString(e.getMessage()));
            }
        }
        user.setUsername(username);
        user.setRealName(realName);
        user.setMobile(mobile);
        user.setEmail(email);
        user.setGender(gender);
        user.setOrgId(orgId);
        user.setGroupId(groupId);
        user.setRank(rank);
        // 后台批量新建的用户与单个新增相同：标记首次登录强制改密（仅在安全设置开启时于登录处生效）
        user.setPwdMustChange(true);
        if (StringUtils.isNotBlank(password)) {
            user.setPassword(passwordEncoder.encode(password));
        }
        return Verdict.ok(new Prepared(row.number(), user, roles.stream().map(Role::getId).distinct().toList()));
    }
}
