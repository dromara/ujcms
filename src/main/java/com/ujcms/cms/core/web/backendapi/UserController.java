package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.RoleService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.service.args.UserArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.security.Secures;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http403Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ujcms.cms.core.support.Constants.*;
import static com.ujcms.cms.core.support.Constants.validLimit;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.web.support.ValidUtils.rankPermission;
import static com.ujcms.commons.db.MyBatis.springPage;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 用户 Controller
 *
 * @author PONY
 */
@RestController("backendUserController")
@RequestMapping(BACKEND_API + "/core/user")
public class UserController {
    private final RoleService roleService;
    private final OrgService orgService;
    private final UserService service;
    private final Props props;

    public UserController(RoleService roleService, OrgService orgService, UserService service, Props props) {
        this.roleService = roleService;
        this.orgService = orgService;
        this.service = service;
        this.props = props;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('user:list','*')")
    public Page<User> page(@Nullable Long orgId, @RequestParam(defaultValue = "false") boolean current,
                           Integer page, Integer pageSize, HttpServletRequest request) {
        if (current && orgId == null) {
            orgId = Contexts.getCurrentSite().getOrgId();
        }
        UserArgs args = UserArgs.of(getQueryMap(request.getQueryString())).orgId(orgId);
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("list")
    @PreAuthorize("hasAnyAuthority('user:list','*')")
    public List<User> list(@Nullable Long orgId, @RequestParam(defaultValue = "false") boolean current,
                           @Nullable Integer offset, @Nullable Integer limit,HttpServletRequest request) {
        if (current && orgId == null) {
            orgId = Contexts.getCurrentSite().getOrgId();
        }
        UserArgs args = UserArgs.of(getQueryMap(request.getQueryString())).orgId(orgId);
        return service.selectList(args, validOffset(offset), validLimit(limit));
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('user:show','*')")
    public User show(@PathVariable Long id) {
        User bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception(USER_NOT_FOUND + id);
        }
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('user:create','*')")
    @OperationLog(module = "user", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody UserParams bean) {
        User currentUser = Contexts.getCurrentUser();
        User user = new User();
        Entities.copy(bean, user, EXCLUDE_FIELDS);
        user.setRank((short) (currentUser.getRank() + 1));
        validateBean(user, user.getRank(), null, null, null, currentUser);
        service.insert(user, user.getExt(), bean.getOrgIds());
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('user:update','*')")
    @OperationLog(module = "user", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody UserParams bean) {
        Site site = Contexts.getCurrentSite();
        User user = service.select(bean.getId());
        if (user == null) {
            return Responses.notFound(USER_NOT_FOUND + bean.getId());
        }
        User currentUser = Contexts.getCurrentUser();
        Short origRank = user.getRank();
        String origUsername = user.getUsername();
        String origMobile = user.getMobile();
        String origEmail = user.getEmail();
        Entities.copy(bean, user, EXCLUDE_FIELDS);
        validateBean(user, origRank, origUsername, origEmail, origMobile, currentUser);
        service.update(user, user.getExt(), bean.isGlobal() ? null : site.getOrgId(), bean.getOrgIds());
        return Responses.ok();
    }

    @PutMapping("permission")
    @PreAuthorize("hasAnyAuthority('user:updatePermission','*')")
    @OperationLog(module = "user", operation = "updatePermission", type = OperationType.UPDATE)
    public ResponseEntity<Body> updatePermission(@RequestBody UserParams bean) {
        User currentUser = Contexts.getCurrentUser();
        User user = service.select(bean.getId());
        if (user == null) {
            return Responses.notFound(USER_NOT_FOUND + bean.getId());
        }
        Short origRank = user.getRank();
        Entities.copyIncludes(bean, user, PERMISSION_FIELDS);
        validatePermission(user.getOrgId(), user.getRank(), origRank, currentUser);
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

    public static class UpdateStatusParams {
        @NotNull
        public List<Long> ids;
        @NotNull
        @Min(0)
        @Max(3)
        public Short status;
    }

    @PutMapping("status")
    @PreAuthorize("hasAnyAuthority('user:updateStatus','*')")
    @OperationLog(module = "user", operation = "updateStatus", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateStatus(@RequestBody @Valid UpdateStatusParams params) {
        User currentUser = Contexts.getCurrentUser();
        params.ids.stream().filter(Objects::nonNull).map(service::select).filter(Objects::nonNull).forEach(user -> {
            validatePermission(user.getOrgId(), user.getRank(), user.getRank(), currentUser);
            user.setStatus(params.status);
            service.update(user);
        });
        return Responses.ok();
    }

    public static class UpdatePasswordParams {
        @NotNull
        public Long id;
        @NotBlank
        public String password;
    }

    @PutMapping("password")
    @PreAuthorize("hasAnyAuthority('user:updatePassword','*')")
    @OperationLog(module = "user", operation = "updatePassword", type = OperationType.UPDATE)
    public ResponseEntity<Body> updatePassword(@RequestBody @Valid UpdatePasswordParams params) {
        User currentUser = Contexts.getCurrentUser();
        User user = service.select(params.id);
        if (user == null) {
            return Responses.notFound(USER_NOT_FOUND + params.id);
        }
        validatePermission(user.getOrgId(), user.getRank(), user.getRank(), currentUser);
        String password = Secures.sm2Decrypt(params.password, props.getClientSm2PrivateKey());
        service.updatePassword(user, user.getExt(), password);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('user:delete','*')")
    @OperationLog(module = "user", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        User currentUser = Contexts.getCurrentUser();
        ids.stream().filter(Objects::nonNull).map(service::select).filter(Objects::nonNull).forEach(user ->
                validatePermission(user.getOrgId(), user.getRank(), user.getRank(), currentUser));
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

    private void validatePermission(Long orgId, Short rank, Short origRank, User currentUser) {
        // 没有全局权限且用户不属于当前组织
        if (!currentUser.hasGlobalPermission() &&
                !orgService.isDescendant(currentUser.getOrg().getId(), orgId)) {
            throw new Http403Exception(String.format("user org(ID=%d) not in current org(ID=%d)",
                    orgId, currentUser.getOrgId()));
        }
        // rank权限
        rankPermission(origRank, rank, currentUser.getRank());
    }

    private void validateBean(User user, Short origRank, @Nullable String origUsername, @Nullable String origEmail,
                              @Nullable String origMobile, User currentUser) {
        validatePermission(user.getOrgId(), user.getRank(), origRank, currentUser);
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

    public static class UserParams extends User {
        private static final long serialVersionUID = 1L;

        private boolean global = false;
        private List<Long> roleIds = new ArrayList<>();
        private List<Long> orgIds = new ArrayList<>();

        public boolean isGlobal() {
            return global;
        }

        public void setGlobal(boolean global) {
            this.global = global;
        }

        public List<Long> getRoleIds() {
            return roleIds;
        }

        public void setRoleIds(List<Long> roleIds) {
            this.roleIds = roleIds;
        }

        public List<Long> getOrgIds() {
            return orgIds;
        }

        public void setOrgIds(List<Long> orgIds) {
            this.orgIds = orgIds;
        }
    }

    /**
     * 需排除的字段。不能直接修改
     */
    private static final String[] EXCLUDE_FIELDS = {"password", "status",
            "created", "loginDate", "loginIp", "loginCount", "errorDate", "errorCount"};
    /**
     * 权限字段
     */
    private static final String[] PERMISSION_FIELDS = {"rank"};

    private static final String USER_NOT_FOUND = "User not found. ID = ";
}