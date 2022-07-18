package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.component.PasswordService;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.captcha.IpLoginAttemptService;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Servlets;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotBlank;

/**
 * 密码 Controller
 *
 * @author PONY
 */
@RestController
@RequestMapping(UrlConstants.API + "/change-password")
public class PasswordController {
    private UserService userService;
    private ConfigService configService;
    private PasswordService passwordService;
    private LoginLogService loginLogService;
    private IpLoginAttemptService ipLoginAttemptService;

    public PasswordController(UserService userService, ConfigService configService, PasswordService passwordService,
                              LoginLogService loginLogService, IpLoginAttemptService ipLoginAttemptService) {
        this.userService = userService;
        this.configService = configService;
        this.passwordService = passwordService;
        this.loginLogService = loginLogService;
        this.ipLoginAttemptService = ipLoginAttemptService;
    }

    @PostMapping
    public ResponseEntity<Responses.Body> changePassword(@RequestBody UpdatePasswordParams params,
                                                         HttpServletRequest request) {
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
        return passwordService.changePassword(user, params.password, params.plainPassword, ip, security, request);
    }

    public static class UpdatePasswordParams {
        @NotBlank
        public String username;
        @NotBlank
        public String password;
        @NotBlank
        public String plainPassword;
    }
}
