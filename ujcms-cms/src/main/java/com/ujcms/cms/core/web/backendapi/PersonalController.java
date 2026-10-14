package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.component.PasswordService;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.UserExt;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Servlets;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;

import java.util.Optional;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

/**
 * 个人设置 Controller
 *
 * @author PONY
 */
@RestController("backendPersonalController")
@RequestMapping(BACKEND_API + "/core/personal")
public class PersonalController {
    private final ConfigService configService;
    private final PasswordService passwordService;
    private final UserService userService;
    private final Props props;

    public PersonalController(ConfigService configService, PasswordService passwordService,
                              UserService userService, Props props) {
        this.configService = configService;
        this.passwordService = passwordService;
        this.userService = userService;
        this.props = props;
    }

    @PutMapping("password")
    @PreAuthorize("hasAnyAuthority('password:update','*')")
    @OperationLog(module = "personal", operation = "updatePassword", type = OperationType.UPDATE)
    public ResponseEntity<Responses.Body> updatePassword(
            @RequestBody UpdatePasswordParams params, HttpServletRequest request) {
        String ip = Servlets.getRemoteAddr(request, props.getIpProxyDepth());
        Config.Security security = configService.getUnique().getSecurity();
        User currentUser = Contexts.getCurrentUser();
        return passwordService.updatePassword(currentUser, params.password, params.newPassword, ip,
                security, request);
    }

    /**
     * 当前用户的控制台偏好（docs/spec/console-shell）。每个后台用户都可读写自己的偏好，故只要求 backend 权限，
     * 与 {@code HomepageController.contentStat} 同例，不设独立权限码。
     */
    @GetMapping("preferences")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public UserExt.Preferences preferences() {
        return Optional.ofNullable(Contexts.getCurrentUser().getExt())
                .map(UserExt::getPreferences).orElseGet(UserExt.Preferences::new);
    }

    @PutMapping("preferences")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public ResponseEntity<Responses.Body> updatePreferences(@RequestBody @Valid UserExt.Preferences bean) {
        userService.updatePreferences(Contexts.getCurrentUser().getId(), bean);
        return Responses.ok();
    }

    public static class UpdatePasswordParams {
        @NotBlank
        public String password;
        @NotBlank
        public String newPassword;
    }
}
