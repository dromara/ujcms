package com.ujcms.core.web.backendapi;

import com.ofwise.util.security.CredentialsDigest;
import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Servlets;
import com.ujcms.core.domain.User;
import com.ujcms.core.service.UserService;
import com.ujcms.core.support.Contexts;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 个人设置 Controller
 *
 * @author PONY
 */
@RestController("backendPersonalController")
@RequestMapping(BACKEND_API + "/core/personal")
public class PersonalController {
    private CredentialsDigest credentialsDigest;
    private UserService service;

    public PersonalController(CredentialsDigest credentialsDigest, UserService service) {
        this.credentialsDigest = credentialsDigest;
        this.service = service;
    }

    @PutMapping("password")
    @RequiresPermissions("password:update")
    public ResponseEntity<Responses.Body> updatePassword(@RequestBody User bean) {
        User currentUser = Contexts.getCurrentUser(service);
        if (!credentialsDigest.matches(currentUser.getPassword(), bean.getPassword(), currentUser.getSalt())) {
            return Responses.badRequest("Password wrong!");
        }
        currentUser.setPlainPassword(bean.getPlainPassword());
        service.update(currentUser);
        return Responses.ok();
    }

    @GetMapping("password-validation")
    @RequiresPermissions("password:update")
    public boolean passwordValidation(HttpServletRequest request) {
        String plainPassword = Servlets.getParam(request.getQueryString(), "password");
        if (StringUtils.isBlank(plainPassword)) {
            return false;
        }
        User currentUser = Contexts.getCurrentUser(service);
        return credentialsDigest.matches(currentUser.getPassword(), plainPassword, currentUser.getSalt());
    }
}
