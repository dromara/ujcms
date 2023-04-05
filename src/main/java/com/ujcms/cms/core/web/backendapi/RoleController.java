package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.RoleService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.service.args.RoleArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.exception.Http400Exception;
import com.ujcms.util.web.exception.Http404Exception;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_PRIVATE;
import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.web.support.ValidUtils.*;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

/**
 * 角色 Controller
 *
 * @author PONY
 */
@RestController("backendRoleController")
@RequestMapping(BACKEND_API + "/core/role")
public class RoleController {
    private final UserService userService;
    private final RoleService service;

    public RoleController(UserService userService, RoleService service) {
        this.userService = userService;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('role:list','*')")
    public List<Role> list(HttpServletRequest request) {
        RoleArgs args = RoleArgs.of(getQueryMap(request.getQueryString()))
                .scopeSiteId(getCurrentSiteId());
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('role:show','*')")
    public Role show(@PathVariable Integer id) {
        Role bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception("Role not found. ID = " + id);
        }
        dataInSite(bean.getSiteId(), getCurrentSiteId());
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('role:create','*')")
    @OperationLog(module = "role", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody Role bean) {
        User currentUser = Contexts.getCurrentUser();
        Role role = new Role();
        Entities.copy(bean, role, Role.PERMISSION_FIELDS);
        validateBean(role, false, role.getRank(), currentUser);
        // 赋予当前用户相同的数据权限
        role.setAllPermission(currentUser.hasAllPermission());
        role.setAllGrantPermission(currentUser.hasAllGrantPermission());
        role.setAllArticlePermission(currentUser.hasAllArticlePermission());
        role.setAllChannelPermission(currentUser.hasAllChannelPermission());
        role.setGlobalPermission(currentUser.hasGlobalPermission());
        role.setDataScope(currentUser.getDataScope());
        service.insert(role, getCurrentSiteId());
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('role:update','*')")
    @OperationLog(module = "role", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody Role bean) {
        User currentUser = Contexts.getCurrentUser();
        Role role = service.select(bean.getId());
        if (role == null) {
            return Responses.notFound("Role not found. ID = " + bean.getId());
        }
        boolean origGlobal = bean.isGlobal();
        Short origRank = bean.getRank();
        Entities.copy(bean, role, Role.PERMISSION_FIELDS);
        validateBean(role, origGlobal, origRank, currentUser);
        service.update(role, getCurrentSiteId());
        return Responses.ok();
    }

    @PutMapping("permission")
    @PreAuthorize("hasAnyAuthority('role:updatePermission','*')")
    @OperationLog(module = "role", operation = "updatePermission", type = OperationType.UPDATE)
    public ResponseEntity<Body> updatePermission(@RequestBody Role bean) {
        User currentUser = Contexts.getCurrentUser();
        Role role = service.select(bean.getId());
        if (role == null) {
            return Responses.notFound("Role not found. ID = " + bean.getId());
        }
        Entities.copyIncludes(bean, role, Role.PERMISSION_FIELDS);
        validateBean(role, false, role.getRank(), currentUser);
        service.update(role, bean.getArticlePermissions(), bean.getChannelPermissions(), getCurrentSiteId());
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('role:update','*')")
    @OperationLog(module = "role", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        User currentUser = Contexts.getCurrentUser();
        Site currentSite = Contexts.getCurrentSite();
        List<Role> list = new ArrayList<>();
        for (Integer id : ids) {
            Role role = service.select(id);
            if (role == null) {
                return Responses.notFound("Role not found. ID = " + id);
            }
            dataInSite(role.getSiteId(), currentSite.getId());
            if (role.isGlobal() && !currentUser.hasGlobalPermission()) {
                continue;
            }
            list.add(role);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('role:delete','*')")
    @OperationLog(module = "role", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        User currentUser = Contexts.getCurrentUser();
        ids.forEach(id -> Optional.ofNullable(id).map(service::select).ifPresent(
                bean -> validatePermission(bean.getSiteId(), bean.isGlobal(), bean.getRank(), currentUser)));
        service.delete(ids);
        return Responses.ok();
    }

    @GetMapping("article-permissions")
    @PreAuthorize("hasAnyAuthority('role:list','*')")
    public List<Integer> articlePermissions(Integer roleId, @Nullable Integer siteId) {
        return service.listArticlePermissions(roleId, siteId != null ? siteId : getCurrentSiteId());
    }

    @GetMapping("channel-permissions")
    @PreAuthorize("hasAnyAuthority('role:list','*')")
    public List<Integer> channelPermissions(Integer roleId, @Nullable Integer siteId) {
        return service.listChannelPermissions(roleId, siteId != null ? siteId : getCurrentSiteId());
    }

    /**
     * 全局共享数据如果被其它站点使用，则不可以改为本站私有数据
     */
    @GetMapping("scope-not-allowed")
    @PreAuthorize("hasAnyAuthority('role:validation','*')")
    public boolean scopeNotAllowed(int scope, Integer roleId) {
        Site site = Contexts.getCurrentSite();
        return scope == SCOPE_PRIVATE && userService.existsByRoleId(roleId, site.getOrgId());
    }

    private void validatePermission(Integer siteId, boolean isGlobal, Short rank, User currentUser) {
        dataInSite(siteId, getCurrentSiteId());
        rankPermission(rank, currentUser.getRank());
        globalPermission(isGlobal, currentUser.hasGlobalPermission());
    }

    private void validateBean(Role role, boolean origGlobal, Short origRank, User currentUser) {
        boolean isGlobal = role.isGlobal() || origGlobal;
        Short rank = origRank > role.getRank() ? origRank : role.getRank();
        validatePermission(role.getSiteId(), isGlobal, rank, currentUser);
        if (scopeNotAllowed(role.getScope(), role.getId())) {
            throw new Http400Exception("scope not allowed " + role.getScope());
        }
    }
}