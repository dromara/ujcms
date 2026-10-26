package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.web.support.FrontendController;
import com.ujcms.common.web.exception.Http404Exception;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.Optional;

/**
 * 会员 Controller
 *
 * @author PONY
 */
@FrontendController("frontendMemberController")
public class MemberController {
    private final UserService userService;
    private final ConfigService configService;

    public MemberController(UserService userService, ConfigService configService) {
        this.userService = userService;
        this.configService = configService;
    }

    private static final String USER_TEMPLATE = "mem_user";
    private static final String LOGIN_TEMPLATE = "mem_login";
    private static final String REGISTER_TEMPLATE = "mem_register";
    private static final String PASSWORD_RESET_TEMPLATE = "mem_password_reset";

    @GetMapping("/login")
    public String login(Site site) {
        return site.assembleTemplate(LOGIN_TEMPLATE);
    }

    @GetMapping("/register")
    public String register(Site site) {
        Config.Register register = configService.getUnique().getRegister();
        if (!register.isEnabled()) {
            throw new Http404Exception("error.registerNotEnabled");
        }
        return site.assembleTemplate(REGISTER_TEMPLATE);
    }

    @GetMapping("/password-reset")
    public String passwordReset(Site site) {
        return site.assembleTemplate(PASSWORD_RESET_TEMPLATE);
    }

    /**
     * 用户名解析跳转。供评论 @提及 链接使用（见 docs/spec/membership/comment-mention.md），
     * 与注册时的用户名重复校验等价，不新增用户名枚举面
     */
    @GetMapping("/users/name/{username}")
    public String userByName(Site site, @PathVariable String username) {
        User targetUser = Optional.ofNullable(userService.selectByUsername(username))
                .orElseThrow(() -> new Http404Exception("User not found. Username: " + username));
        // redirect: 以 / 开头的地址相对于 contextPath，因此用子目录拼接，不用已含 contextPath 的 site.getDy()
        String subDir = site.getSubDir();
        if (StringUtils.isNotBlank(subDir)) {
            return "redirect:/" + subDir + "/users/" + targetUser.getId();
        }
        return "redirect:/users/" + targetUser.getId();
    }

    @GetMapping("/users/{id}")
    public String users(Site site, @PathVariable Long id, Map<String, Object> modelMap) {
        // 已注销账号与不存在同样答 404（docs/spec/membership/member-center.md §13）
        User targetUser = Optional.ofNullable(userService.select(id)).filter(it -> !it.isCancelled())
                .orElseThrow(() -> new Http404Exception(User.NOT_FOUND + id));
        modelMap.put("targetUser", targetUser);
        return site.assembleTemplate(USER_TEMPLATE);
    }
}
