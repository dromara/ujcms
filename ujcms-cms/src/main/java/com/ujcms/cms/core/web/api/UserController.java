package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 用户 Controller
 *
 * @author PONY
 */
@Tag(name = "用户接口")
@RestController
@RequestMapping({API + "/user", FRONTEND_API + "/user"})
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /**
     * 注册表单的用户名预检。用户名由访客自己起、敏感度低、表单需要即时反馈，故匿名直答。
     * 手机号 / 邮箱没有预检接口：“某号码是否在本站注册”是可字典遍历的账号枚举口子（OWASP WSTG-IDNT-04），
     * 业界做法是只在提交注册时（{@code POST /api/register}，始终可见的人机验证之后）报重复；
     * 曾有的 {@code *-exist} / {@code mobile-not-exist} / {@code email-not-exist} 已移除（backend-authorization §3）。
     */
    @Operation(summary = "用户名是否未使用")
    @GetMapping("/username-not-exist")
    public boolean usernameNotExist(@Parameter(description = "用户名") @NotBlank String username) {
        return service.selectByUsername(username) == null;
    }

    @Operation(summary = "用户公开信息")
    @GetMapping("/{id:[\\d]+}")
    public PublicUser show(@Parameter(description = "用户ID") @PathVariable Long id) {
        User user = service.select(id);
        // 已注销账号与不存在同样答 404：个人信息已抹除，也不暴露账号曾经存在
        if (user == null || user.isCancelled()) {
            throw new Http404Exception(User.NOT_FOUND + id);
        }
        return PublicUser.from(user);
    }

    /**
     * 当前登录会员信息（CG2 in api-inventory.md）。
     * <p>
     * 与管理后台用的 {@code /api/env/backend-state} 区分开来：本接口只返回会员
     * 自身的资料字段（昵称、头像、邮箱、手机号等），不包含 {@code permissions /
     * dataScope / databaseType} 等管理端字段；适合公网模板（SSR 头部芯片、
     * /settings/* 页面、个人主页跳转等）独立调用，互不污染。
     * <p>
     * 匿名调用返回空对象 {@code {} }，与 {@code /api/env/backend-state} 的语义
     * 保持一致 —— 由调用方将空 Map 归一化为 {@code null}。
     */
    @Operation(summary = "当前登录会员信息")
    @GetMapping("/me")
    public Object me() {
        User user = Optional.ofNullable(Contexts.findCurrentUser()).filter(User::isEnabled).orElse(null);
        if (user == null) {
            return Collections.emptyMap();
        }
        return MeUser.from(user);
    }

    /**
     * 当前登录会员的个人资料 DTO。包含 PublicUser 的所有公开字段，外加仅当前
     * 用户自己可见的敏感字段（email / mobile）以及衍生字段 hasPassword（用于
     * 区分纯第三方登录账号 / 设置过密码的账号），不包含密码哈希或权限集合。
     */
    @Schema(name = "UserController.MeUser", description = "当前登录会员信息")
    public record MeUser(
            @Schema(description = "用户ID") Long id,
            @Schema(description = "用户名") String username,
            @Schema(description = "昵称") @Nullable String nickname,
            @Schema(description = "真实姓名") @Nullable String realName,
            @Schema(description = "性别(0:保密,1:男,2:女)") Short gender,
            @Schema(description = "出生日期") @Nullable OffsetDateTime birthday,
            @Schema(description = "居住地") @Nullable String location,
            @Schema(description = "自我介绍") @Nullable String bio,
            @Schema(description = "邮箱") @Nullable String email,
            @Schema(description = "手机号") @Nullable String mobile,
            @Schema(description = "头像URL") @Nullable String avatar,
            @Schema(description = "小头像") @Nullable String smallAvatar,
            @Schema(description = "中头像") @Nullable String mediumAvatar,
            @Schema(description = "大头像") @Nullable String largeAvatar,
            @Schema(description = "个人主页") String homepage,
            @Schema(description = "是否设置过密码（区分纯 OAuth 账号）") boolean hasPassword,
            @Schema(description = "注册日期") OffsetDateTime created
    ) {
        public static MeUser from(User u) {
            boolean hasPw = u.isHasPassword();
            return new MeUser(
                    u.getId(),
                    u.getUsername(),
                    u.getNickname(),
                    u.getRealName(),
                    u.getGender(),
                    u.getBirthday(),
                    u.getLocation(),
                    u.getBio(),
                    u.getEmail(),
                    u.getMobile(),
                    u.getAvatar(),
                    u.getSmallAvatar(),
                    u.getMediumAvatar(),
                    u.getLargeAvatar(),
                    u.getHomepage(),
                    hasPw,
                    u.getCreated()
            );
        }
    }

    /**
     * 用户公开信息。仅暴露公共主页所需的字段，刻意排除 email / mobile / 等级 /
     * 组织 / 历史登录信息等敏感字段，避免直接序列化 {@link User} 实体造成隐私泄漏。
     */
    @Schema(name = "UserController.PublicUser", description = "用户公开信息")
    public record PublicUser(
            @Schema(description = "用户ID") Long id,
            @Schema(description = "用户名") String username,
            @Schema(description = "昵称") @Nullable String nickname,
            @Schema(description = "性别(0:保密,1:男,2:女)") Short gender,
            @Schema(description = "出生日期") @Nullable OffsetDateTime birthday,
            @Schema(description = "居住地") @Nullable String location,
            @Schema(description = "自我介绍") @Nullable String bio,
            @Schema(description = "头像URL") @Nullable String avatar,
            @Schema(description = "小头像") @Nullable String smallAvatar,
            @Schema(description = "中头像") @Nullable String mediumAvatar,
            @Schema(description = "大头像") @Nullable String largeAvatar,
            @Schema(description = "个人主页") String homepage,
            @Schema(description = "注册日期") OffsetDateTime created
    ) {
        public static PublicUser from(User u) {
            return new PublicUser(
                    u.getId(),
                    u.getUsername(),
                    u.getNickname(),
                    u.getGender(),
                    u.getBirthday(),
                    u.getLocation(),
                    u.getBio(),
                    u.getAvatar(),
                    u.getSmallAvatar(),
                    u.getMediumAvatar(),
                    u.getLargeAvatar(),
                    u.getHomepage(),
                    u.getCreated()
            );
        }
    }
}
