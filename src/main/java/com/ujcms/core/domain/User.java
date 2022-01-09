package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ujcms.core.domain.base.RoleBase;
import com.ujcms.core.domain.base.UserBase;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.io.Serializable;
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
    public boolean isExcessiveAttempts() {
        return getExt().getErrorCount() >= LOGIN_ERROR_COUNT
                && System.currentTimeMillis() - getExt().getErrorDate().toEpochSecond() < LOGIN_ERROR_MILLIS;
    }

    /**
     * 获取权限列表
     */
    public List<String> getPermissions() {
        String permission = getRoleList().stream().map(RoleBase::getPermission)
                .filter(Objects::nonNull).collect(Collectors.joining(","));
        return Stream.of(StringUtils.split(permission, ",")).collect(Collectors.toList());
    }

    /**
     * 是否锁定
     */
    public boolean isLocked() {
        return getStatus() == STATUS_LOCKED;
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

    /**
     * 用户状态：正常
     */
    public static final short STATUS_NORMAL = 0;
    /**
     * 用户状态：锁定
     */
    public static final short STATUS_LOCKED = 1;

    /**
     * 登录错误最大次数
     */
    public static final int LOGIN_ERROR_COUNT = 8;
    /**
     * 登录错误间隔时间
     */
    public static final int LOGIN_ERROR_MILLIS = 30 * 60 * 1000;

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
     * 角色ID列表。用于获取前台提交的数据。
     */
    private List<Integer> roleIds = new ArrayList<>();

    @Nullable
    private String plainPassword;

    @JsonIgnore
    public List<Integer> getRoleIds() {
        return roleIds;
    }

    @JsonProperty
    public void setRoleIds(List<Integer> roleIds) {
        this.roleIds = roleIds;
    }

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

    // UserExt 对象属性

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

    public OffsetDateTime getErrorDate() {
        return getExt().getErrorDate();
    }

    public void setErrorDate(OffsetDateTime errorDate) {
        getExt().setErrorDate(errorDate);
    }

    public int getErrorCount() {
        return getExt().getErrorCount();
    }

    public void setErrorCount(int errorCount) {
        getExt().setErrorCount(errorCount);
    }

    @Nullable
    public String getPlainPassword() {
        return plainPassword;
    }

    public void setPlainPassword(@Nullable String plainPassword) {
        this.plainPassword = plainPassword;
    }
}
