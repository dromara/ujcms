package com.ujcms.cms.core.web.backendapi;

import com.ujcms.util.security.CredentialsDigest;
import com.ujcms.util.security.Secures;
import com.ujcms.util.web.Responses;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private CredentialsDigest credentialsDigest;
    private UserService service;
    private Props props;

    public PersonalController(CredentialsDigest credentialsDigest, UserService service, Props props) {
        this.credentialsDigest = credentialsDigest;
        this.service = service;
        this.props = props;
    }

    @PutMapping("password")
    @RequiresPermissions("password:update")
    public ResponseEntity<Responses.Body> updatePassword(@RequestBody User bean) {
        User currentUser = Contexts.getCurrentUser(service);
        // 前台密码已通过SM2加密，此处进行解密
        String password = Secures.sm2Decrypt(bean.getPassword(), props.getClientSm2PrivateKey());
        if (!credentialsDigest.matches(currentUser.getPassword(), password, currentUser.getSalt())) {
            return Responses.badRequest("Password wrong!");
        }
        currentUser.setPlainPassword(bean.getPlainPassword());
        service.update(currentUser);
        return Responses.ok();
    }

    @GetMapping("password-matches")
    @RequiresPermissions("password:matches")
    public boolean passwordMatches(@NotBlank String password, HttpServletRequest request) {
        String decryptedPassword = Secures.sm2Decrypt(password, props.getClientSm2PrivateKey());
        User currentUser = Contexts.getCurrentUser(service);
        return credentialsDigest.matches(currentUser.getPassword(), decryptedPassword, currentUser.getSalt());
    }
}
