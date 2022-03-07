package com.ujcms.core.web.backendapi;

import com.ofwise.util.db.MyBatis;
import com.ofwise.util.query.QueryUtils;
import com.ofwise.util.web.Entities;
import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Responses.Body;
import com.ofwise.util.web.Servlets;
import com.ujcms.core.domain.User;
import com.ujcms.core.service.UserService;
import com.ujcms.core.support.Contexts;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ujcms.core.support.Constants.validPage;
import static com.ujcms.core.support.Constants.validPageSize;
import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 用户 Controller
 *
 * @author PONY
 */
@RestController("backendUserController")
@RequestMapping(BACKEND_API + "/core/user")
public class UserController {
    private UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("user:list")
    public Object list(Integer page, Integer pageSize, HttpServletRequest request) {
        Map<String, Object> queryMap = QueryUtils.getQueryMap(request.getQueryString());
        return MyBatis.toPage(service.selectPage(queryMap, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("{id}")
    @RequiresPermissions("user:show")
    public Object show(@PathVariable Integer id) {
        User bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("User not found. ID = " + id);
        }
        return bean;
    }

    private static final String[] EXCLUDE_FIELDS = {"password", "salt", "status",
            "created", "loginDate", "loginIp", "loginCount", "errorDate", "errorCount"};

    @PostMapping
    @RequiresPermissions("user:create")
    public ResponseEntity<Body> create(@RequestBody User bean) {
        User currentUser = Contexts.getCurrentUser(service);
        if (currentUser.getRank() > bean.getRank()) {
            return Responses.forbidden("User rank cannot be higher than itself: " + currentUser.getRank());
        }
        User user = Entities.copy(bean, new User(), EXCLUDE_FIELDS);
        service.insert(user, user.getExt(), bean.getRoleIds());
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("user:update")
    public ResponseEntity<Body> update(@RequestBody User bean) {
        User user = service.select(bean.getId());
        if (user == null) {
            return Responses.notFound("User not found. ID = " + bean.getId());
        }
        User currentUser = Contexts.getCurrentUser(service);
        if (currentUser.getRank() > bean.getRank() || currentUser.getRank() > user.getRank()) {
            return Responses.badRequest("User rank cannot be higher than itself: " + currentUser.getRank());
        }
        Entities.copy(bean, user, EXCLUDE_FIELDS);
        service.update(user, user.getExt(), bean.getRoleIds());
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("user:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }

    @GetMapping("username-validation")
    public boolean usernameValidation(HttpServletRequest request) {
        String username = Servlets.getParam(request.getQueryString(), "username");
        if (StringUtils.isBlank(username)) {
            return false;
        }
        return service.selectByUsername(username) == null;
    }

    @GetMapping("email-validation")
    public boolean emailValidation(HttpServletRequest request) {
        String email = Servlets.getParam(request.getQueryString(), "email");
        if (StringUtils.isBlank(email)) {
            return true;
        }
        return service.selectByEmail(email) == null;
    }

    @GetMapping("mobile-validation")
    public boolean mobileValidation(HttpServletRequest request) {
        String mobile = Servlets.getParam(request.getQueryString(), "mobile");
        if (StringUtils.isBlank(mobile)) {
            return true;
        }
        return service.selectByMobile(mobile) == null;
    }
}