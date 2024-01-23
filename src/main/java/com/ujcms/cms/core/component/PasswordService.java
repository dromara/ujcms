package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.security.Secures;
import com.ujcms.commons.web.Responses;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 密码 Service
 *
 * @author PONY
 */
@Component
public class PasswordService {
    private final LoginLogService loginLogService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final Props props;

    public PasswordService(LoginLogService loginLogService, UserService userService,
                           PasswordEncoder passwordEncoder, Props props) {
        this.loginLogService = loginLogService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    public ResponseEntity<Responses.Body> updatePassword(
            User user, @Nullable String encryptedPassword, String newEncryptedPassword,
            String ip, Config.Security security, HttpServletRequest request) {
        // 用户登录是否超过尝试次数
        if (user.isExcessiveAttempts(security.getUserMaxAttempts(), security.getUserLockMinutes())) {
            loginLogService.updatePasswordFailure(user.getId(), ip, LoginLog.STATUS_USER_EXCESSIVE_ATTEMPTS);
            return Responses.failure(request, "error.userExcessiveAttempts");
        }
        String rawPassword = StringUtils.isNotBlank(encryptedPassword) ?
                Secures.sm2Decrypt(encryptedPassword, props.getClientSm2PrivateKey()) : null;
        String password = user.getPassword();
        if (rawPassword == null && StringUtils.isNotBlank(password)) {
            return Responses.failure("password cannot be null");
        }
        boolean isWrongPassword = rawPassword != null &&
                (StringUtils.isBlank(password) || !passwordEncoder.matches(rawPassword, password));
        if (isWrongPassword) {
            // 记录用户错误次数
            userService.loginFailure(user.getExt(), security.getUserLockMinutes());
            loginLogService.updatePasswordFailure(user.getId(), ip, LoginLog.STATUS_PASSWORD_WRONG);
            int maxAttempts = security.getUserMaxAttempts();
            if (maxAttempts > 0) {
                return Responses.failure(request, "error.passwordIncorrectAndWarning",
                        maxAttempts - user.getErrorCount());
            }
            return Responses.failure(request, "error.passwordIncorrect");
        }
        // 是否达到最小使用天数
        if (user.getPasswordDays() < security.getPasswordMinDays()) {
            return Responses.failure(request, "error.passwordNotReachedMinDays");
        }
        // 前台密码已通过SM2加密，此处进行解密
        String newPassword = Secures.sm2Decrypt(newEncryptedPassword, props.getClientSm2PrivateKey());
        // 是否使用历史密码
        int passwordMaxHistory = security.getPasswordMaxHistory();
        for (String historyPassword : user.getHistoryPasswordList(passwordMaxHistory)) {
            if (passwordEncoder.matches(newPassword, historyPassword)) {
                return Responses.failure(request, "error.passwordMatchesHistory");
            }
        }
        userService.updatePassword(user, user.getExt(), newPassword);
        return Responses.ok();
    }
}
