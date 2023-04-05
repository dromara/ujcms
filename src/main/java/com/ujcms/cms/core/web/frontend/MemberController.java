package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.util.misc.BaseNum;
import com.ujcms.util.web.exception.Http404Exception;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 会员 Controller
 *
 * @author PONY
 */
@Controller("frontendMemberController")
public class MemberController {
    private final UserService userService;
    private final ConfigService configService;
    private final SiteResolver siteResolver;

    public MemberController(UserService userService, ConfigService configService, SiteResolver siteResolver) {
        this.userService = userService;
        this.configService = configService;
        this.siteResolver = siteResolver;
    }

    private static final String USER_TEMPLATE = "mem_user";
    private static final String LOGIN_TEMPLATE = "mem_login";
    private static final String REGISTER_TEMPLATE = "mem_register";
    private static final String PASSWORD_RESET_TEMPLATE = "mem_password_reset";

    @GetMapping({"/login", "/{subDir:[\\w-]+}/login"})
    public String login(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(LOGIN_TEMPLATE);
    }

    @GetMapping({"/register", "/{subDir:[\\w-]+}/register"})
    public String register(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        Config.Register register = configService.getUnique().getRegister();
        if (!register.isEnabled()) {
            throw new Http404Exception("error.registerNotEnabled");
        }
        return site.assembleTemplate(REGISTER_TEMPLATE);
    }

    @GetMapping({"/password-reset", "/{subDir:[\\w-]+}/password-reset"})
    public String passwordReset(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(PASSWORD_RESET_TEMPLATE);
    }

    @GetMapping({"/users/{stringId}", "/{subDir:[\\w-]+}/users/{stringId}"})
    public String users(@PathVariable String stringId, @PathVariable(required = false) String subDir,
                        HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        byte[] idBytes = stringId.getBytes(UTF_8);
        if (!BaseNum.isBase36Encoding(idBytes)) {
            throw new Http404Exception("User not found. stringId: " + stringId);
        }
        User targetUser = Optional.ofNullable(userService.select(BaseNum.base36().toReverseInt(stringId)))
                .orElseThrow(() -> new Http404Exception("User not found. stringId: " + stringId));
        modelMap.put("targetUser", targetUser);
        return site.assembleTemplate(USER_TEMPLATE);
    }
}
