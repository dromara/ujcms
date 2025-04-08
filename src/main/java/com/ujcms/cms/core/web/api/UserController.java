package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotBlank;

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

    @Operation(summary = "用户名是否未使用")
    @GetMapping("/username-not-exist")
    public boolean usernameNotExist(@Parameter(description = "用户名") @NotBlank String username) {
        return service.selectByUsername(username) == null;
    }

    @Operation(summary = "用户名是否存在")
    @GetMapping("/username-exist")
    public boolean usernameExist(@Parameter(description = "用户名") @NotBlank String username) {
        return service.selectByUsername(username) != null;
    }

    @Operation(summary = "手机号码是否未使用")
    @GetMapping("/mobile-not-exist")
    public boolean mobileNotExist(@Parameter(description = "手机号码") @NotBlank String mobile) {
        return service.selectByMobile(mobile) == null;
    }

    @Operation(summary = "手机号码是否存在")
    @GetMapping("/mobile-exist")
    public boolean mobileExist(@Parameter(description = "手机号码") @NotBlank String mobile) {
        return service.selectByMobile(mobile) != null;
    }

    @Operation(summary = "邮箱地址是否未使用")
    @GetMapping("/email-not-exist")
    public boolean emailNotExist(@Parameter(description = "邮箱地址") @NotBlank String email) {
        return service.selectByEmail(email) == null;
    }

    @Operation(summary = "邮箱地址是否存在")
    @GetMapping("/email-exist")
    public boolean emailExist(@Parameter(description = "邮箱地址") @NotBlank String email) {
        return service.selectByEmail(email) != null;
    }
}
