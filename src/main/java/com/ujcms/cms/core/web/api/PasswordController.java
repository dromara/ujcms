package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.component.PasswordService;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.captcha.IpLoginAttemptService;
import com.ujcms.commons.security.Secures;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.exception.Http400Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Optional;

import static com.ujcms.cms.core.support.Constants.DEMO_USER_ID;
import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 密码 Controller
 *
 * @author PONY
 */
@Tag(name = "密码接口")
@RestController
public class PasswordController {
    private final UserService userService;
    private final ConfigService configService;
    private final PasswordService passwordService;
    private final LoginLogService loginLogService;
    private final ShortMessageService shortMessageService;
    private final IpLoginAttemptService ipLoginAttemptService;
    private final Props props;

    public PasswordController(UserService userService, ConfigService configService, PasswordService passwordService,
                              LoginLogService loginLogService, ShortMessageService shortMessageService,
                              IpLoginAttemptService ipLoginAttemptService, Props props) {
        this.userService = userService;
        this.configService = configService;
        this.passwordService = passwordService;
        this.loginLogService = loginLogService;
        this.shortMessageService = shortMessageService;
        this.ipLoginAttemptService = ipLoginAttemptService;
        this.props = props;
    }

    @Operation(summary = "更新密码")
    @PutMapping({API + "/update-password", FRONTEND_API + "/update-password"})
    public ResponseEntity<Responses.Body> updatePassword(
            @RequestBody @Valid UpdatePasswordParams params, HttpServletRequest request) {
        if (StringUtils.isBlank(params.username)) {
            // 未提供用户名，则需要已登录状态
            params.username = Optional.ofNullable(Contexts.findCurrentUsername()).orElseThrow(
                    () -> new Http400Exception("username cannot be null"));
        }
        String ip = Servlets.getRemoteAddr(request);
        Config.Security security = configService.getUnique().getSecurity();
        // IP登录失败超过限制次数
        if (ipLoginAttemptService.isExcessive(ip, security.getIpMaxAttempts())) {
            loginLogService.loginFailure(params.username, ip, LoginLog.STATUS_IP_EXCESSIVE_ATTEMPTS);
            return Responses.failure(request, "error.ipExcessiveAttempts");
        }
        User user = userService.selectByUsername(params.username);
        if (user == null) {
            ipLoginAttemptService.failure(ip);
            loginLogService.loginFailure(params.username, ip, LoginLog.STATUS_LOGIN_NAME_NOT_FOUND);
            return Responses.failure(request, "error.usernameNotExist");
        }
        if (props.isDemo() && DEMO_USER_ID == user.getId()) {
            return Responses.failure(request, "error.demoUserForbidden");
        }
        return passwordService.updatePassword(user, params.password, params.newPassword, ip, security, request);
    }

    @Schema(name = "PasswordController.UpdatePasswordParams", description = "更新密码参数")
    public static class UpdatePasswordParams {
        @Schema(description = "用户名")
        public String username;
        @Schema(description = "原密码。需要使用sm2加密")
        public String password;
        @Schema(description = "新密码。需要使用sm2加密")
        @NotBlank
        public String newPassword;
    }

    @Operation(summary = "重置密码")
    @PutMapping({API + "/reset-password", FRONTEND_API + "/reset-password"})
    public ResponseEntity<Responses.Body> resetPassword(
            @RequestBody @Valid ResetPasswordParams params) {
        Config config = configService.getUnique();
        User user;
        if (StringUtils.isNotBlank(params.email)) {
            if (!shortMessageService.validateCode(params.emailMessageId, params.email,
                    params.emailMessageValue, config.getEmail().getCodeExpires())) {
                throw new Http400Exception("error.emailMessageIncorrect");
            }
            user = Optional.ofNullable(userService.selectByEmail(params.email)).orElseThrow(() ->
                    new IllegalStateException("User not found by email: " + params.email));
        } else if (StringUtils.isNotBlank(params.mobile)) {
            if (!shortMessageService.validateCode(params.mobileMessageId, params.mobile,
                    params.mobileMessageValue, config.getSms().getCodeExpires())) {
                throw new Http400Exception("error.mobileMessageIncorrect");
            }
            user = Optional.ofNullable(userService.selectByMobile(params.mobile)).orElseThrow(() ->
                    new IllegalStateException("User not found by mobile: " + params.mobile));
        } else {
            throw new Http400Exception("Email and mobile cannot be empty at the same time");
        }
        String password = Secures.sm2Decrypt(params.password, props.getClientSm2PrivateKey());
        userService.updatePassword(user, user.getExt(), password);
        return Responses.ok();
    }

    @Schema(name = "PasswordController.ResetPasswordParams", description = "重置密码参数")
    public static class ResetPasswordParams {
        @Schema(description = "新密码。需要使用sm2加密")
        public String password;
        @Schema(description = "邮箱地址")
        public String email;
        @Schema(description = "邮件短信ID")
        public Long emailMessageId;
        @Schema(description = "邮件验证码")
        public String emailMessageValue;
        @Schema(description = "手机号码")
        public String mobile;
        @Schema(description = "手机短信ID")
        public Long mobileMessageId;
        @Schema(description = "短信验证码")
        public String mobileMessageValue;
    }
}
