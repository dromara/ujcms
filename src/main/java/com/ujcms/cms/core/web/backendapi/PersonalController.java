package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.component.PasswordService;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Servlets;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotBlank;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

/**
 * 个人设置 Controller
 *
 * @author PONY
 */
@RestController("backendPersonalController")
@RequestMapping(BACKEND_API + "/core/personal")
public class PersonalController {
    private UserService userService;
    private ConfigService configService;
    private PasswordService passwordService;

    public PersonalController(UserService userService, ConfigService configService, PasswordService passwordService) {
        this.userService = userService;
        this.configService = configService;
        this.passwordService = passwordService;
    }

    @PutMapping("password")
    @RequiresPermissions("password:update")
    public ResponseEntity<Responses.Body> updatePassword(@RequestBody UpdatePasswordParams params,
                                                         HttpServletRequest request) {
        String ip = Servlets.getRemoteAddr(request);
        Config.Security security = configService.getUnique().getSecurity();
        User currentUser = Contexts.getCurrentUser(userService);
        return passwordService.changePassword(currentUser, params.password, params.plainPassword, ip,
                security, request);
    }

    public static class UpdatePasswordParams {
        @NotBlank
        public String password;
        @NotBlank
        public String plainPassword;
    }
}
