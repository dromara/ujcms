package com.ujcms.core.web.api;

import com.ujcms.core.domain.User;
import com.ujcms.core.service.UserService;
import com.ujcms.core.support.Contexts;
import com.ujcms.core.support.Props;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
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
    public Map<String, Object> current() {
        User user = Optional.ofNullable(Contexts.findCurrentUser(userService)).filter(User::isNormal).orElse(null);
        if (user == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>(16);
        result.put("username", user.getUsername());
        result.put("avatar", user.getAvatar());
        result.put("permissions", user.getPermissions());
        result.put("loginDate", user.getLoginDate());
        result.put("loginIp", user.getLoginIp());
        result.put("epExcludes", Props.EP_EXCLUDES);
        result.put("epDisplay", Props.EP_DISPLAY);
        result.put("epRank", Props.EP_RANK);
        result.put("epActivated", Props.EP_ACTIVATED);
        return result;
    }
}
