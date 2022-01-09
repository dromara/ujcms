package com.ujcms.core.web.api;

import com.ujcms.core.domain.User;
import com.ujcms.core.service.UserService;
import com.ujcms.core.support.Contexts;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static com.ujcms.core.support.UrlConstants.API;

/**
 * 用户 Controller
 *
 * @author PONY
 */
@RestController
@RequestMapping(API + "/user")
public class UserController {
    private UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获得当前登录用户
     */
    @GetMapping("/current")
    public User current() {
        return Optional.ofNullable(Contexts.findCurrentUser(userService)).filter(User::isNormal).orElse(null);
    }
}
