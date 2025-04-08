package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.file.FileHandler;
import com.ujcms.commons.security.Secures;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.PathResolver;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import static com.ujcms.cms.core.support.Constants.DEMO_USER_ID;
import static com.ujcms.cms.core.support.ErrorConstants.DEMO_USER_FORBIDDEN;
import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.commons.web.Uploads.AVATAR_TYPE;

/**
 * 会员设置 Controller
 *
 * @author PONY
 */
@Tag(name = "会员设置接口")
@RestController
@RequestMapping({API + "/settings", FRONTEND_API + "/settings"})
public class SettingsController {
    private final ShortMessageService shortMessageService;
    private final ConfigService configService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final PathResolver pathResolver;
    private final Props props;

    public SettingsController(ShortMessageService shortMessageService, ConfigService configService,
                              UserService userService, PasswordEncoder passwordEncoder,
                              PathResolver pathResolver, Props props) {
        this.shortMessageService = shortMessageService;
        this.configService = configService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.pathResolver = pathResolver;
        this.props = props;
    }

    @Operation(summary = "更新个人信息")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/profile")
    public ResponseEntity<Body> updateProfile(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "用户对象。不可用属性：\"nickname\", \"realName\", \"gender\", \"birthday\", \"location\", \"bio\"")
            @RequestBody User bean, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        if (props.isDemo() && DEMO_USER_ID == user.getId()) {
            return Responses.failure(request, DEMO_USER_FORBIDDEN);
        }
        Entities.copyIncludes(bean, user, "nickname", "realName", "gender", "birthday", "location", "bio");
        userService.update(user, user.getExt());
        return Responses.ok();
    }

    @Operation(summary = "更新个人头像")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/avatar")
    public ResponseEntity<Body> updateAvatar(@RequestBody @Valid AvatarParams params, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        if (props.isDemo() && DEMO_USER_ID == user.getId()) {
            return Responses.failure(request, DEMO_USER_FORBIDDEN);
        }
        Config config = configService.getUnique();
        FileHandler fileHandler = config.getUploadStorage().getFileHandler(pathResolver);
        String url = fileHandler.getDisplayPrefix() + "/" + AVATAR_TYPE + "/" + user.getId() + "/" + params.image;
        user.setAvatar(url);
        userService.update(user);
        return Responses.ok();
    }

    @Operation(summary = "更新邮箱地址")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/email")
    public ResponseEntity<Body> updateEmail(@RequestBody EmailParams params, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        if (props.isDemo() && DEMO_USER_ID == user.getId()) {
            return Responses.failure(request, DEMO_USER_FORBIDDEN);
        }
        Config config = configService.getUnique();
        Config.Security securityConfig = config.getSecurity();
        // 用户登录是否超过尝试次数
        if (user.isExcessiveAttempts(securityConfig.getUserMaxAttempts(), securityConfig.getUserLockMinutes())) {
            return Responses.failure(request, "error.userExcessiveAttempts");
        }
        ResponseEntity<Body> failure = validatePassword(params.password, props.getClientSm2PrivateKey(), user, securityConfig, request);
        if (failure != null) {
            return failure;
        }
        if (!shortMessageService.validateCode(params.emailMessageId, params.email, params.emailMessageValue,
                config.getEmail().getCodeExpires())) {
            return Responses.failure(request, "error.emailMessageIncorrect");
        }
        user.setEmail(params.email);
        userService.update(user);
        return Responses.ok();
    }

    @Operation(summary = "更新手机号码")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/mobile")
    public ResponseEntity<Body> updateMobile(@RequestBody MobileParams params, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        if (props.isDemo() && DEMO_USER_ID == user.getId()) {
            return Responses.failure(request, DEMO_USER_FORBIDDEN);
        }
        Config config = configService.getUnique();
        Config.Security securityConfig = config.getSecurity();
        ResponseEntity<Body> failure = validatePassword(params.password, props.getClientSm2PrivateKey(), user, securityConfig, request);
        if (failure != null) {
            return failure;
        }
        if (!shortMessageService.validateCode(params.mobileMessageId, params.mobile, params.mobileMessageValue,
                config.getSms().getCodeExpires())) {
            return Responses.failure(request, "error.mobileMessageIncorrect");
        }
        user.setMobile(params.mobile);
        userService.update(user);
        return Responses.ok();
    }

    private ResponseEntity<Body> validatePassword(String encryptedPassword, String privateKey, User user,
                                                  Config.Security securityConfig, HttpServletRequest request) {
        if (StringUtils.isBlank(encryptedPassword) && StringUtils.isNotBlank(user.getPassword())) {
            return Responses.failure("password cannot be null");
        }
        String rawPassword = null;
        if (StringUtils.isNotBlank(encryptedPassword)) {
            rawPassword = Secures.sm2Decrypt(encryptedPassword, privateKey);
        }
        String password = user.getPassword();
        boolean isWrongPassword = rawPassword != null &&
                (StringUtils.isBlank(password) || !passwordEncoder.matches(rawPassword, password));
        if (isWrongPassword) {
            // 记录用户错误次数
            userService.loginFailure(user.getExt(), securityConfig.getUserLockMinutes());
            int maxAttempts = securityConfig.getUserMaxAttempts();
            if (maxAttempts > 0) {
                return Responses.failure(request, "error.passwordIncorrectAndWarning",
                        maxAttempts - user.getErrorCount());
            }
            return Responses.failure(request, "error.passwordIncorrect");
        }
        return null;
    }

    @Schema(name = "SettingsController.EmailParams", description = "邮箱参数")
    public static class EmailParams {
        @Schema(description = "邮箱地址")
        @NotBlank
        public String email;
        @Schema(description = "密码。需使用sm2加密")
        public String password;
        @Schema(description = "邮件短信ID")
        @NotNull
        public Long emailMessageId;
        @Schema(description = "邮件验证码")
        @NotBlank
        public String emailMessageValue;
    }

    @Schema(name = "SettingsController.MobileParams", description = "手机参数")
    public static class MobileParams {
        @Schema(description = "手机号码")
        @NotBlank
        public String mobile;
        @Schema(description = "密码。需使用sm2加密")
        public String password;
        @Schema(description = "手机短信ID")
        @NotNull
        public Long mobileMessageId;
        @Schema(description = "手机验证码")
        @NotBlank
        public String mobileMessageValue;
    }

    @Schema(name = "SettingsController.AvatarParams", description = "头像参数")
    public static class AvatarParams {
        @Schema(description = "图片名")
        @Pattern(regexp = "[\\w.]{32,38}")
        public String image;
    }
}
