package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.util.security.CredentialsDigest;
import com.ujcms.util.security.Secures;
import com.ujcms.util.web.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 密码 Service
 *
 * @author PONY
 */
@Component
public class PasswordService {
    private LoginLogService loginLogService;
    private UserService userService;
    private CredentialsDigest credentialsDigest;
    private Props props;

    public PasswordService(LoginLogService loginLogService, UserService userService,
                           CredentialsDigest credentialsDigest, Props props) {
        this.loginLogService = loginLogService;
        this.userService = userService;
        this.credentialsDigest = credentialsDigest;
        this.props = props;
    }

    public ResponseEntity<Responses.Body> changePassword(
            User user, String encryptedPassword, String plainPassword,
            String ip, Config.Security security, HttpServletRequest request) {
        // 用户登录是否超过尝试次数
        if (user.isExcessiveAttempts(security.getUserMaxAttempts(), security.getUserLockMinutes())) {
            loginLogService.changePasswordFailure(user.getId(), ip, LoginLog.STATUS_USER_EXCESSIVE_ATTEMPTS);
            return Responses.failure(request, "error.userExcessiveAttempts");
        }
        // 前台密码已通过SM2加密，此处进行解密
        String password = Secures.sm2Decrypt(encryptedPassword, props.getClientSm2PrivateKey());
        if (!credentialsDigest.matches(user.getPassword(), password, user.getSalt())) {
            // 记录用户错误次数
            userService.loginFailure(user.getExt(), security.getUserLockMinutes());
            loginLogService.changePasswordFailure(user.getId(), ip, LoginLog.STATUS_PASSWORD_WRONG);
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
        // 是否使用历史密码
        int passwordMaxHistory = security.getPasswordMaxHistory();
        for (User.SaltPassword saltPassword : user.getHistoryPasswordList(passwordMaxHistory)) {
            if (credentialsDigest.matches(saltPassword.getPassword(), plainPassword, saltPassword.getSalt())) {
                return Responses.failure(request, "error.passwordMatchesHistory");
            }
        }
        userService.changePassword(user, user.getExt(), plainPassword);
        return Responses.ok();
    }
}
