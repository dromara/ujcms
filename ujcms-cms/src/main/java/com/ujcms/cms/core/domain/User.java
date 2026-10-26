package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.generated.GeneratedOrg;
import com.ujcms.cms.core.domain.generated.GeneratedRole;
import com.ujcms.cms.core.domain.generated.GeneratedUser;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.support.Commercial;
import com.ujcms.cms.core.support.StaticProps;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 用户实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(value = {"password", "handler"}, allowSetters = true)
public class User extends GeneratedUser implements UserDetails {

    /**
     * 用户名和用户真实姓名
     */
    @Schema(description = "用户名和用户真实姓名")
    public String getName() {
        StringBuilder buff = new StringBuilder(getUsername());
        String realName = getRealName();
        if (StringUtils.isNotBlank(realName)) {
            buff.append("(").append(realName).append(")");
        }
        return buff.toString();
    }

    /**
     * 个人主页
     */
    @Schema(description = "个人主页")
    public String getHomepage() {
        return "/users/" + getId();
    }

    @Schema(description = "大头像")
    @Nullable
    public String getLargeAvatar() {
        return getOtherSizeAvatar(Config.Register.AVATAR_LARGE);
    }

    @Schema(description = "中头像")
    @Nullable
    public String getMediumAvatar() {
        return getOtherSizeAvatar(Config.Register.AVATAR_MEDIUM);
    }

    @Schema(description = "小头像")
    @Nullable
    public String getSmallAvatar() {
        return getOtherSizeAvatar(Config.Register.AVATAR_SMALL);
    }

    @JsonIgnore
    public List<String> getAvatarList() {
        if (StringUtils.isBlank(getAvatar())) {
            return Collections.emptyList();
        }
        return Arrays.asList(getAvatar(), getLargeAvatar(), getMediumAvatar(), getSmallAvatar());
    }

    @Nullable
    private String getOtherSizeAvatar(String size) {
        if (getAvatar() == null) {
            return null;
        }
        String extension = FilenameUtils.getExtension(getAvatar());
        return getAvatar() + size + extension;
    }

    /**
     * 是否登录尝试过多。登录错误次数超过8次，且在半小时内。
     */
    @JsonIgnore
    public boolean isExcessiveAttempts(int maxAttempts, int lockMinutes) {
        // maxAttempts 为 0 则不限制
        return maxAttempts > 0 && getExt().getErrorCount() >= maxAttempts
                && Duration.between(getErrorDate(), OffsetDateTime.now()).toMinutes() <= lockMinutes;
    }

    /**
     * 密码是否过期
     *
     * @param maxDays 密码最长使用天数
     */
    @JsonIgnore
    public boolean isPasswordExpired(int maxDays) {
        // maxDays 为 0 则不过期
        return maxDays > 0 && getPasswordRemainingDays(maxDays) < 0;
    }

    /**
     * 密码剩余天数
     *
     * @param maxDay 密码最长使用天数
     */
    @JsonIgnore
    public int getPasswordRemainingDays(int maxDay) {
        return maxDay - getPasswordDays();
    }

    /**
     * 密码使用天数
     */
    @JsonIgnore
    public int getPasswordDays() {
        return (int) Duration.between(getPasswordModified(), OffsetDateTime.now()).toDays();
    }

    /**
     * 获取权限列表
     */
    @JsonIgnore
    public List<String> getPermissions() {
        String permission = getRoleList().stream().map(GeneratedRole::getPermission)
                .filter(Objects::nonNull).collect(Collectors.joining(","));
        List<String> list = Stream.of(StringUtils.split(permission, ",")).distinct().collect(Collectors.toList());
        // 拥有后台角色，则授予后台通用权限
        if (!getRoleList().isEmpty()) {
            list.add(Role.PERMISSION_BACKEND);
        }
        // 超级管理员，授予所有权限
        if (hasAllPermission()) {
            list.add("*");
        }
        return list;
    }

    /**
     * 获取授权权限
     */
    @JsonIgnore
    public List<String> getGrantPermissions() {
        String permission = getRoleList().stream().map(GeneratedRole::getGrantPermission)
                .filter(Objects::nonNull).collect(Collectors.joining(","));
        List<String> list = Stream.of(StringUtils.split(permission, ",")).distinct().collect(Collectors.toList());
        // 超级管理员，授予所有权限
        if (hasAllGrantPermission()) {
            list.add("*");
        }
        return list;
    }

    /**
     * 是否拥有全局数据权限
     */
    @JsonIgnore
    public boolean hasGlobalPermission() {
        if (isRoot()) {
            return true;
        }
        for (Role role : getRoleList()) {
            if (Boolean.TRUE.equals(role.getGlobalPermission())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否拥有所有权限
     */
    @JsonIgnore
    public boolean hasAllPermission() {
        if (isRoot()) {
            return true;
        }
        for (Role role : getRoleList()) {
            if (Boolean.TRUE.equals(role.getAllPermission())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否可进入后台：超级管理员（id=1，不论是否开启三权分立），或拥有任一后台角色。
     * <p>
     * 用于判断账号是否属于双因子登录要保护的对象，如开启双因子后禁止这类账号经短信自助找回密码。
     */
    @JsonIgnore
    public boolean hasBackendAccess() {
        return getId() == 1 || !getRoleList().isEmpty();
    }

    /**
     * 是否拥有所有授权权限
     */
    @JsonIgnore
    public boolean hasAllGrantPermission() {
        if (isRoot()) {
            return true;
        }
        for (Role role : getRoleList()) {
            if (Boolean.TRUE.equals(role.getAllGrantPermission())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否拥有所有文章数据权限
     */
    @JsonIgnore
    public boolean hasAllArticlePermission() {
        if (isRoot()) {
            return true;
        }
        for (Role role : getRoleList()) {
            if (Boolean.TRUE.equals(role.getAllArticlePermission())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否拥有所有状态数据权限
     */
    @JsonIgnore
    public boolean hasAllStatusPermission() {
        if (isRoot()) {
            return true;
        }
        for (Role role : getRoleList()) {
            if (Boolean.TRUE.equals(role.getAllStatusPermission())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否拥有所有栏目数据权限
     */
    @JsonIgnore
    public boolean hasAllChannelPermission() {
        if (isRoot()) {
            return true;
        }
        for (Role role : getRoleList()) {
            if (Boolean.TRUE.equals(role.getAllChannelPermission())) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public List<Long> fetchRoleIds() {
        return getRoleList().stream().map(Role::getId).toList();
    }

    @JsonIgnore
    public Set<Long> fetchAllOrgIds() {
        Set<Long> orgIds = getOrgList().stream().map(GeneratedOrg::getId).collect(Collectors.toSet());
        orgIds.add(getOrgId());
        return orgIds;
    }

    @JsonIgnore
    public short getDataScope() {
        if (isRoot()) {
            return Role.DATA_SCOPE_ALL;
        }
        short dataScope = Role.DATA_SCOPE_SELF;
        for (Role role : getRoleList()) {
            if (role.getDataScope() < dataScope) {
                dataScope = role.getDataScope();
            }
        }
        return dataScope;
    }

    @JsonIgnore
    public List<String> getHistoryPasswordList() {
        String whole = getHistoryPassword();
        if (StringUtils.isBlank(whole)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(whole.split(",")));
    }

    @JsonIgnore
    public List<String> getHistoryPasswordList(int maxHistory) {
        if (maxHistory <= 0) {
            return new ArrayList<>();
        }
        List<String> list = getHistoryPasswordList();
        int size = list.size();
        if (size > maxHistory) {
            return list.subList(size - maxHistory, size);
        }
        return list;
    }

    public void setHistoryPasswordList(List<String> passwordList) {
        if (passwordList.isEmpty()) {
            setHistoryPassword(null);
            return;
        }
        setHistoryPassword(String.join(",", passwordList));
    }

    public void addHistoryPassword(String password) {
        List<String> list = getHistoryPasswordList();
        list.add(password);
        int size = list.size();
        if (size > PASSWORD_MAX_HISTORY) {
            list = list.subList(size - PASSWORD_MAX_HISTORY, PASSWORD_MAX_HISTORY);
        }
        setHistoryPasswordList(list);
    }

    /**
     * 纯 OAuth 账号的口令哨兵值。第三方登录创建的账号没有本地口令，口令列存 {@code "0"}。
     *
     * @see #isHasPassword()
     */
    public static final String PASSWORD_NONE = "0";

    /**
     * 是否设置过本地口令（用于区分纯 OAuth 账号）。
     * <p>
     * 模板中以 {@code user.hasPassword} 访问：会员“账号设置”页据此决定是显示“修改密码”（需填原密码）
     * 还是“设置密码”。此前主题直接读 {@code user.password} 与哨兵值 {@code "0"} 比较——把口令散列
     * 暴露给了模板，且经 {@code article.user} 一类实体图链可读到<b>他人</b>的散列。
     * {@code getPassword()} 现已从模板对象包装器中摘除（{@code TemplateMemberAccessPolicy}，
     * design-review-2026-07 T5），本方法是其唯一正当用途的替代读法，语义完全等价：
     * 口令非空且不等于 {@code "0"}。
     * <p>
     * {@code @JsonIgnore}：仅供模板使用。JSON 侧同名字段由 {@code UserController.MeUser} 投影提供，
     * 此处不参与序列化，避免改变既有 API 响应形状。
     */
    @JsonIgnore
    public boolean isHasPassword() {
        String pw = getPassword();
        return StringUtils.isNotBlank(pw) && !PASSWORD_NONE.equals(pw);
    }

    /**
     * 是否超级管理员。ID为1的用户为超级管理员
     */
    @Schema(description = "是否超级管理员。ID为1的用户为超级管理员")
    public boolean isRoot() {
        // 三权分立开启时，超级管理员(id=1)的全局权限旁路被中和（等保三级 访问控制 / G4）
        return getId() == 1 && !StaticProps.isSeparationOfDuties();
    }

    /**
     * 是否审计对象。系统管理员和安全管理员为审计对象，操作日志由审计管理员审计
     */
    @JsonIgnore
    public boolean isAuditObject() {
        return getType().equals(TYPE_SYSTEM) || getType().equals(TYPE_SECURITY);
    }

    /**
     * 是否管理员类型（系统管理员、安全管理员、审计管理员、常规管理员）。私信发送门禁的豁免条件
     */
    @JsonIgnore
    public boolean isAdminType() {
        return getType() < TYPE_MEMBER;
    }

    /**
     * 是否未激活
     */
    @JsonIgnore
    public boolean isUnactivated() {
        return getStatus() == STATUS_UNACTIVATED;
    }

    /**
     * 是否锁定
     */
    @JsonIgnore
    public boolean isLocked() {
        return getStatus() == STATUS_LOCKED;
    }

    /**
     * 是否注销
     */
    @JsonIgnore
    public boolean isCancelled() {
        return getStatus() == STATUS_CANCELLED;
    }

    /**
     * 注销账号（会员自助，{@code DELETE /api/settings/account}；docs/spec/membership/member-center.md §13）。
     * <p>
     * 行保留、状态置为已注销，个人信息就地抹除：评论、文章等引用 {@code user_id_} 的数据不悬空，
     * 登录与好友 / 私信等处的 {@link #isCancelled()} 门禁照旧生效。用户名改为 {@code cancelled_<id>}：
     * 原用户名释放给新注册者；以原用户名为主体的访问令牌、记住我令牌与会话再也查不到用户，
     * 无需吊销名单。第三方登录绑定由 {@code UserService#cancel} 一并删除。
     * 保留 ID、所属组 / 组织、注册时间与登录统计；登录日志、操作日志按审计要求留存。
     */
    public void cancel() {
        setStatus(STATUS_CANCELLED);
        setUsername(CANCELLED_USERNAME_PREFIX + getId());
        setPassword(null);
        setEmail(null);
        setMobile(null);
        setAlias(null);
        setNickname(CANCELLED_NICKNAME);
        setRealName(null);
        setGender(GENDER_SECRET);
        setAvatar(null);
        setCustoms(new HashMap<>(0));
        UserExt userExt = getExt();
        userExt.setBirthday(null);
        userExt.setLocation(null);
        userExt.setBio(null);
        userExt.setHistoryPassword(null);
    }

    /**
     * 是否正常状态
     */
    @Schema(description = "是否正常状态")
    @Override
    public boolean isEnabled() {
        // 三权分立开启时，强制停用超级管理员(id=1)且不可重新启用（即使库中 status 被改回正常，登录/认证仍判为禁用）
        if (StaticProps.isSeparationOfDuties() && getId() == 1) {
            return false;
        }
        return getStatus() == STATUS_ENABLED;
    }

    /**
     * 是否禁用。非正常状态，就是禁用。
     */
    @JsonIgnore
    public boolean isDisabled() {
        return !isEnabled();
    }
    // region UserDetails

    /**
     * 安全上下文中的权限。商业权限码在当前版本未解锁时（{@link Commercial#isUnlocked}）不进入权限集合：
     * 社区版的角色树为推广而显示带徽标的商业节点、角色 CSV 也可能自更高版本迁移而来，
     * 只由类路径裁剪不足以拦住留在 core 的接口（全站置灰、站点全文重建、组织权限等），故在此统一收口。
     * 超级权限 {@code *} 不在映射内，不受影响。
     */
    @JsonIgnore
    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return getPermissions().stream().filter(Commercial::isUnlocked)
                .map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    /**
     * 是否未锁定
     */
    @JsonIgnore
    @Override
    public boolean isAccountNonLocked() {
        return !isLocked();
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    // endregion

    // region Associations
    /**
     * 用户扩展对象
     */
    @JsonIgnore
    private UserExt ext = new UserExt();
    /**
     * 用户组
     */
    @JsonIncludeProperties({"id", "name"})
    private Group group = new Group();
    /**
     * 组织
     */
    @JsonIncludeProperties({"id", "name"})
    private Org org = new Org();
    /**
     * 角色列表
     */
    @JsonIncludeProperties({"id", "name"})
    private List<Role> roleList = new ArrayList<>();
    /**
     * 扩展组织列表
     */
    @JsonIncludeProperties({"id", "name"})
    private List<Org> orgList = new ArrayList<>();

    public UserExt getExt() {
        return ext;
    }

    public void setExt(UserExt ext) {
        this.ext = ext;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public Org getOrg() {
        return org;
    }

    public void setOrg(Org org) {
        this.org = org;
    }

    public List<Role> getRoleList() {
        return roleList;
    }

    public void setRoleList(List<Role> roleList) {
        this.roleList = roleList;
    }

    public List<Org> getOrgList() {
        return orgList;
    }

    public void setOrgList(List<Org> orgList) {
        this.orgList = orgList;
    }
    // endregion

    // region UserExt

    @Schema(description = "出生日期")
    @Nullable
    public OffsetDateTime getBirthday() {
        return getExt().getBirthday();
    }

    public void setBirthday(@Nullable OffsetDateTime birthday) {
        getExt().setBirthday(birthday);
    }

    @Length(max = 200)
    @Nullable
    @Schema(description = "居住地")
    public String getLocation() {
        return getExt().getLocation();
    }

    public void setLocation(@Nullable String location) {
        getExt().setLocation(location);
    }

    @Length(max = 1000)
    @Nullable
    @Schema(description = "自我介绍")
    public String getBio() {
        return getExt().getBio();
    }

    public void setBio(@Nullable String bio) {
        getExt().setBio(bio);
    }

    @Schema(description = "创建日期")
    public OffsetDateTime getCreated() {
        return getExt().getCreated();
    }

    public void setCreated(OffsetDateTime created) {
        getExt().setCreated(created);
    }

    @JsonIgnore
    @Nullable
    public String getHistoryPassword() {
        return getExt().getHistoryPassword();
    }

    public void setHistoryPassword(@Nullable String historyPassword) {
        getExt().setHistoryPassword(historyPassword);
    }

    @NotNull
    @Schema(description = "最后登录日期")
    public OffsetDateTime getLoginDate() {
        return getExt().getLoginDate();
    }

    public void setLoginDate(OffsetDateTime loginDate) {
        getExt().setLoginDate(loginDate);
    }

    @Length(max = 45)
    @NotNull
    @Schema(description = "最后登录IP")
    public String getLoginIp() {
        return getExt().getLoginIp();
    }

    public void setLoginIp(String loginIp) {
        getExt().setLoginIp(loginIp);
    }

    @NotNull
    @Schema(description = "登录次数")
    public int getLoginCount() {
        return getExt().getLoginCount();
    }

    public void setLoginCount(int loginCount) {
        getExt().setLoginCount(loginCount);
    }

    @JsonIgnore
    public OffsetDateTime getErrorDate() {
        return getExt().getErrorDate();
    }

    public void setErrorDate(OffsetDateTime errorDate) {
        getExt().setErrorDate(errorDate);
    }

    @JsonIgnore
    public int getErrorCount() {
        return getExt().getErrorCount();
    }

    public void setErrorCount(int errorCount) {
        getExt().setErrorCount(errorCount);
    }
    // endregion

    // region StaticField
    /**
     * 密码最大历史记录
     */
    public static final int PASSWORD_MAX_HISTORY = 24;
    /**
     * 用户状态：正常
     */
    public static final short STATUS_ENABLED = 0;
    /**
     * 用户状态：未激活
     */
    public static final short STATUS_UNACTIVATED = 1;
    /**
     * 用户状态：已锁定
     */
    public static final short STATUS_LOCKED = 2;
    /**
     * 用户状态：已注销
     */
    public static final short STATUS_CANCELLED = 3;
    /**
     * 注销后的用户名前缀，其后接用户 ID（{@link #cancel()}）
     */
    public static final String CANCELLED_USERNAME_PREFIX = "cancelled_";
    /**
     * 注销后的昵称。评论等处按“昵称，缺省用户名”署名，于是显示为中性标签而非占位用户名
     */
    public static final String CANCELLED_NICKNAME = "已注销用户";
    /**
     * 性别：保密
     */
    public static final short GENDER_SECRET = 0;

    /**
     * 用户类型：系统管理员
     */
    public static final short TYPE_SYSTEM = 1;
    /**
     * 用户类型：安全管理员
     */
    public static final short TYPE_SECURITY = 2;
    /**
     * 用户类型：审计管理员
     */
    public static final short TYPE_AUDIT = 3;
    /**
     * 用户类型：常规管理员
     */
    public static final short TYPE_NORMAL = 4;
    /**
     * 用户类型：前台会员
     */
    public static final short TYPE_MEMBER = 5;

    /**
     * 匿名用户ID
     */
    public static final Long ANONYMOUS_ID = 0L;

    public static final String NOT_FOUND = "User not found. ID: ";

    /**
     * 用户模型类别
     */
    public static final String MODEL_TYPE = "user";
    // endregion

    // region JsonFields

    /**
     * 自定义字段
     */
    @Nullable
    private transient Map<String, Object> customs;

    @Schema(description = "自定义字段")
    public Map<String, Object> getCustoms() {
        if (customs != null) {
            return customs;
        }
        customs = new HashMap<>(16);
        Model model = getUserModel();
        if (model == null) {
            return customs;
        }
        customs.putAll(model.assembleMap(getMainsJson()));
        customs.putAll(model.assembleMap(getClobsJson()));
        return customs;
    }

    public void setCustoms(Map<String, Object> customs) {
        this.customs = customs;
    }

    public void disassembleCustoms(Model model, HtmlSanitizeService sanitizeService) {
        Map<String, Object> map = model.sanitizeMap(getCustoms(), sanitizeService);
        setCustoms(map);
        model.disassembleMap(map, this::setMainsJson, this::setClobsJson);
    }

    /**
     * 用户模型。由 UserService 在加载时统一注入；无全局用户模型时为 {@code null}。
     */
    @JsonIgnore
    @Nullable
    private transient Model userModel;

    @JsonIgnore
    @Nullable
    public Model getUserModel() {
        return userModel;
    }

    public void setUserModel(@Nullable Model userModel) {
        this.userModel = userModel;
    }
    // endregion

    // region BlindIndex
    /**
     * 手机号盲索引（HMAC-SM3，64 个十六进制字符）。由 {@code UserService} 在写入时统一计算，
     * 不接受外部入参、也不对外序列化（{@code @JsonIgnore}）。值为空时为 {@code null}。
     *
     * @see com.ujcms.cms.core.security.PiiBlindIndex
     */
    @Nullable
    private String mobileIndex;
    /**
     * 邮箱盲索引（HMAC-SM3，64 个十六进制字符）。语义同 {@link #mobileIndex}。
     */
    @Nullable
    private String emailIndex;

    @JsonIgnore
    @Nullable
    public String getMobileIndex() {
        return mobileIndex;
    }

    @JsonIgnore
    public void setMobileIndex(@Nullable String mobileIndex) {
        this.mobileIndex = mobileIndex;
    }

    @JsonIgnore
    @Nullable
    public String getEmailIndex() {
        return emailIndex;
    }

    @JsonIgnore
    public void setEmailIndex(@Nullable String emailIndex) {
        this.emailIndex = emailIndex;
    }
    // endregion

    // region PwdMustChange
    /**
     * 首次登录强制改密标记。开启 {@code Config.Security.forceChangePasswordOnFirstLogin} 后，
     * 该值为 {@code true} 的用户登录时需先改密。由后台“新增用户 / 重置密码”置位、用户自助改密时清除，
     * 不接受外部入参（{@code @JsonIgnore}，且在 UserController 的 EXCLUDE_FIELDS 中排除）。
     */
    @JsonIgnore
    private Boolean pwdMustChange = false;

    @JsonIgnore
    public Boolean getPwdMustChange() {
        return pwdMustChange;
    }

    @JsonIgnore
    public void setPwdMustChange(Boolean pwdMustChange) {
        this.pwdMustChange = pwdMustChange;
    }
    // endregion
}
