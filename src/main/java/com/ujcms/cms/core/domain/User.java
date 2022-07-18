package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ujcms.cms.core.domain.base.RoleBase;
import com.ujcms.cms.core.domain.base.UserBase;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 用户 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties(value = {"password", "salt"}, allowSetters = true)
public class User extends UserBase implements Serializable {
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
        return maxDays > 0 && getPasswordRemainingDays(maxDays) > maxDays;
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
        String permission = getRoleList().stream().map(RoleBase::getPermission)
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
        String permission = getRoleList().stream().map(RoleBase::getGrantPermission)
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
            if (role.getGlobalPermission()) {
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
            if (role.getAllPermission()) {
                return true;
            }
        }
        return false;
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
            if (role.getAllGrantPermission()) {
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
            if (role.getAllArticlePermission()) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public List<Integer> fetchRoleIds() {
        return getRoleList().stream().map(Role::getId).collect(Collectors.toList());
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
    public List<SaltPassword> getHistoryPasswordList() {
        String whole = getHistoryPassword();
        if (StringUtils.isBlank(whole)) {
            return new ArrayList<>();
        }
        List<SaltPassword> passwordList = new ArrayList<>();
        char semicolon = ';';
        char comma = ',';
        for (String part : StringUtils.split(whole, semicolon)) {
            String[] pair = StringUtils.split(part, comma);
            passwordList.add(new SaltPassword(pair[0], pair[1]));
        }
        return passwordList;
    }

    @JsonIgnore
    public List<SaltPassword> getHistoryPasswordList(int maxHistory) {
        if (maxHistory <= 0) {
            return new ArrayList<>();
        }
        List<SaltPassword> list = getHistoryPasswordList();
        int size = list.size();
        if (size > maxHistory) {
            return list.subList(size - maxHistory, size);
        }
        return list;
    }

    public void setHistoryPasswordList(List<SaltPassword> passwordList) {
        if (passwordList.isEmpty()) {
            setHistoryPassword(null);
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (SaltPassword pass : passwordList) {
            builder.append(pass.getSalt()).append(",").append(pass.getPassword()).append(";");
        }
        setHistoryPassword(builder.toString());
    }

    public void addHistoryPassword(String salt, String password) {
        List<SaltPassword> list = getHistoryPasswordList();
        list.add(new SaltPassword(salt, password));
        int size = list.size();
        if (size > PASSWORD_MAX_HISTORY) {
            list = list.subList(size - PASSWORD_MAX_HISTORY, PASSWORD_MAX_HISTORY);
        }
        setHistoryPasswordList(list);
    }

    /**
     * 是否超级管理员
     */
    public boolean isRoot() {
        return getId() == 1;
    }

    /**
     * 是否未激活
     */
    public boolean isInactivated() {
        return getStatus() == STATUS_INACTIVATED;
    }

    /**
     * 是否锁定
     */
    public boolean isLocked() {
        return getStatus() == STATUS_LOCKED;
    }

    /**
     * 是否注销
     */
    public boolean isCancelled() {
        return getStatus() == STATUS_CANCELLED;
    }

    /**
     * 是否正常
     */
    public boolean isNormal() {
        return getStatus() == STATUS_NORMAL;
    }

    /**
     * 是否禁用。非正常状态，就是禁用。
     */
    public boolean isDisabled() {
        return getStatus() != STATUS_NORMAL;
    }

    // region TempFields

    @Nullable
    private String plainPassword;
    /**
     * 角色ID列表。用于获取前台提交的数据。
     */
    private List<Integer> roleIds = new ArrayList<>();

    @Nullable
    public String getPlainPassword() {
        return plainPassword;
    }

    public void setPlainPassword(@Nullable String plainPassword) {
        this.plainPassword = plainPassword;
    }

    @JsonIgnore
    public List<Integer> getRoleIds() {
        return roleIds;
    }

    @JsonProperty
    public void setRoleIds(List<Integer> roleIds) {
        this.roleIds = roleIds;
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
    // endregion

    // region UserExt

    @Nullable
    public String getRealName() {
        return getExt().getRealName();
    }

    public void setRealName(@Nullable String realName) {
        getExt().setRealName(realName);
    }

    public String getGender() {
        return getExt().getGender();
    }

    public void setGender(String gender) {
        getExt().setGender(gender);
    }

    @Nullable
    public OffsetDateTime getBirthday() {
        return getExt().getBirthday();
    }

    public void setBirthday(@Nullable OffsetDateTime birthday) {
        getExt().setBirthday(birthday);
    }

    @Nullable
    public String getLocation() {
        return getExt().getLocation();
    }

    public void setLocation(@Nullable String location) {
        getExt().setLocation(location);
    }

    @Nullable
    public String getBio() {
        return getExt().getBio();
    }

    public void setBio(@Nullable String bio) {
        getExt().setBio(bio);
    }

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

    public OffsetDateTime getLoginDate() {
        return getExt().getLoginDate();
    }

    public void setLoginDate(OffsetDateTime loginDate) {
        getExt().setLoginDate(loginDate);
    }

    public String getLoginIp() {
        return getExt().getLoginIp();
    }

    public void setLoginIp(String loginIp) {
        getExt().setLoginIp(loginIp);
    }

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
    public static final short STATUS_NORMAL = 0;
    /**
     * 用户状态：未激活
     */
    public static final short STATUS_INACTIVATED = 1;
    /**
     * 用户状态：已锁定
     */
    public static final short STATUS_LOCKED = 2;
    /**
     * 用户状态：已注销
     */
    public static final short STATUS_CANCELLED = 3;

    /**
     * 需排除的字段。不能直接修改
     */
    public static final String[] EXCLUDE_FIELDS = {"password", "salt", "status",
            "created", "loginDate", "loginIp", "loginCount", "errorDate", "errorCount"};
    /**
     * 权限字段
     */
    public static final String[] PERMISSION_FIELDS = {"rank"};
    // endregion

    public static class SaltPassword {
        public SaltPassword() {
        }

        public SaltPassword(String salt, String password) {
            this.salt = salt;
            this.password = password;
        }

        private String salt = "";
        private String password = "";

        public String getSalt() {
            return salt;
        }

        public void setSalt(String salt) {
            this.salt = salt;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
