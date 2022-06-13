package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.RoleService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.service.args.UserArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.exception.Http400Exception;
import com.ujcms.util.web.exception.Http403Exception;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

import static com.ujcms.cms.core.domain.User.EXCLUDE_FIELDS;
import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.web.support.Validations.rankPermission;
import static com.ujcms.util.db.MyBatis.springPage;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

/**
 * 用户 Controller
 *
 * @author PONY
 */
@RestController("backendUserController")
@RequestMapping(BACKEND_API + "/core/user")
public class UserController {
    private RoleService roleService;
    private UserService service;

    public UserController(RoleService roleService, UserService service) {
        this.roleService = roleService;
        this.service = service;
    }

    @GetMapping
    @RequiresPermissions("user:list")
    public Object list(@Nullable Integer orgId, @RequestParam(defaultValue = "false") boolean current,
                       Integer page, Integer pageSize, HttpServletRequest request) {
        if (current && orgId == null) {
            orgId = Contexts.getCurrentSite().getOrgId();
        }
        UserArgs args = UserArgs.of(getQueryMap(request.getQueryString())).orgId(orgId);
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
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

    @PostMapping
    @RequiresPermissions("user:create")
    public ResponseEntity<Body> create(@RequestBody User bean) {
        User currentUser = Contexts.getCurrentUser(service);
        User user = new User();
        Entities.copy(bean, user, EXCLUDE_FIELDS);
        validateBean(user, user.getRank(), null, null, null, currentUser);
        service.insert(user, user.getExt(), null);
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
        Short origRank = user.getRank();
        String origUsername = user.getUsername();
        String origMobile = user.getMobile();
        String origEmail = user.getEmail();
        Entities.copy(bean, user, User.EXCLUDE_FIELDS);
        validateBean(user, origRank, origUsername, origEmail, origMobile, currentUser);
        service.update(user, user.getExt(), null);
        return Responses.ok();
    }

    @PutMapping("permission")
    @RequiresPermissions("role:updatePermission")
    public ResponseEntity<Body> updatePermission(@RequestBody User bean) {
        User currentUser = Contexts.getCurrentUser(service);
        User user = service.select(bean.getId());
        if (user == null) {
            return Responses.notFound("User not found. ID = " + bean.getId());
        }
        Entities.copyIncludes(bean, user, User.PERMISSION_FIELDS);
        validatePermission(user.getOrgId(), user.getRank(), currentUser);
        bean.getRoleIds().stream().filter(Objects::nonNull).map(roleService::select)
                .filter(Objects::nonNull).forEach(role -> {
            if (role.getRank() < user.getRank()) {
                throw new Http403Exception(String.format("user rank(%d) below then role rank(%d)",
                        bean.getRank(), role.getRank()));
            }
        });
        service.update(user, bean.getRoleIds());
        return Responses.ok();
    }

    static class UpdateStatusParams {
        @NotNull
        public List<Integer> ids;
        @NotNull
        @Min(0)
        @Max(3)
        public Short status;
    }

    @PutMapping("status")
    @RequiresPermissions("role:updateStatus")
    public ResponseEntity<Body> updateStatus(@RequestBody @Valid UpdateStatusParams params) {
        User currentUser = Contexts.getCurrentUser(service);
        params.ids.stream().filter(Objects::nonNull).map(service::select).filter(Objects::nonNull).forEach(user -> {
            validatePermission(user.getOrgId(), user.getRank(), currentUser);
            user.setStatus(params.status);
            service.update(user);
        });
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("user:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        User currentUser = Contexts.getCurrentUser(service);
        ids.stream().filter(Objects::nonNull).map(service::select).filter(Objects::nonNull).forEach(user ->
                validatePermission(user.getOrgId(), user.getRank(), currentUser));
        service.delete(ids);
        return Responses.ok();
    }

    @GetMapping("username-exist")
    public boolean usernameExist(@NotBlank String username) {
        return service.selectByUsername(username) != null;
    }

    @GetMapping("email-exist")
    public boolean emailExist(@NotBlank String email) {
        return service.selectByEmail(email) != null;
    }

    @GetMapping("mobile-exist")
    public boolean mobileExist(@NotBlank String mobile) {
        return service.selectByMobile(mobile) != null;
    }

    private void validatePermission(Integer orgId, Short rank, User currentUser) {
        // 没有全局权限且用户不属于当前组织
        if (!currentUser.hasGlobalPermission() && !currentUser.getOrg().getDescendants().contains(orgId)) {
            throw new Http403Exception(String.format("user org(ID=%d) not in current org(ID=%d)",
                    orgId, currentUser.getOrgId()));
        }
        // rank权限
        rankPermission(rank, currentUser.getRank());
    }

    private void validateBean(User user, Short origRank, @Nullable String origUsername, @Nullable String origEmail,
                              @Nullable String origMobile, User currentUser) {
        Short rank = origRank > user.getRank() ? origRank : user.getRank();
        validatePermission(user.getOrgId(), rank, currentUser);
        String username = user.getUsername();
        if (!Objects.equals(username, origUsername) && usernameExist(username)) {
            throw new Http400Exception("username exist: " + username);
        }
        String email = user.getEmail();
        if (!Objects.equals(email, origEmail) && emailExist(email)) {
            throw new Http400Exception("email exist: " + email);
        }
        String mobile = user.getMobile();
        if (!Objects.equals(mobile, origMobile) && emailExist(mobile)) {
            throw new Http400Exception("mobile exist: " + mobile);
        }
    }
}