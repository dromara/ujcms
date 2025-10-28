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
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_PRIVATE;
import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.cms.core.web.support.ValidUtils.*;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

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
    public Role show(@PathVariable Long id) {
        Role bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception(ROLE_NOT_FOUND + id);
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
            throw new Http404Exception(ROLE_NOT_FOUND + bean.getId());
        }
        boolean origGlobal = role.isGlobal();
        Short origRank = role.getRank();
        Entities.copy(bean, role, Role.PERMISSION_FIELDS);
        validateBean(role, origGlobal, origRank, currentUser);
        service.update(role, getCurrentSiteId());
        return Responses.ok();
    }

    @PutMapping("permission")
    @PreAuthorize("hasAnyAuthority('role:updatePermission','*')")
    @OperationLog(module = "role", operation = "updatePermission", type = OperationType.UPDATE)
    public ResponseEntity<Body> updatePermission(@RequestBody RoleParams bean) {
        User currentUser = Contexts.getCurrentUser();
        Role role = service.select(bean.getId());
        if (role == null) {
            throw new Http404Exception(ROLE_NOT_FOUND + bean.getId());
        }
        Short origRank = role.getRank();
        Entities.copyIncludes(bean, role, Role.PERMISSION_FIELDS);
        validateBean(role, false, origRank, currentUser);
        service.update(role, bean.getArticlePermissions(), bean.getChannelPermissions(), bean.getOrgPermissions(),
                getCurrentSiteId());
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('role:update','*')")
    @OperationLog(module = "role", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody Long[] ids) {
        User currentUser = Contexts.getCurrentUser();
        Site currentSite = Contexts.getCurrentSite();
        List<Role> list = new ArrayList<>();
        for (Long id : ids) {
            Role role = service.select(id);
            if (role == null) {
                throw new Http404Exception(ROLE_NOT_FOUND + id);
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
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        User currentUser = Contexts.getCurrentUser();
        ids.forEach(id -> Optional.ofNullable(id).map(service::select).ifPresent(
                bean -> validatePermission(bean.getSiteId(), bean.isGlobal(), bean.getRank(), bean.getRank(), currentUser)));
        service.delete(ids);
        return Responses.ok();
    }

    @GetMapping("article-permissions")
    @PreAuthorize("hasAnyAuthority('role:list','*')")
    public List<Long> articlePermissions(Long roleId, @Nullable Long siteId) {
        return service.listArticlePermissions(roleId, siteId != null ? siteId : getCurrentSiteId());
    }

    @GetMapping("channel-permissions")
    @PreAuthorize("hasAnyAuthority('role:list','*')")
    public List<Long> channelPermissions(Long roleId, @Nullable Long siteId) {
        return service.listChannelPermissions(roleId, siteId != null ? siteId : getCurrentSiteId());
    }

    @GetMapping("org-permissions")
    @PreAuthorize("hasAnyAuthority('role:list','*')")
    public List<Long> orgPermissions(Long roleId, @Nullable Long siteId) {
        return service.listOrgPermissions(roleId, siteId != null ? siteId : getCurrentSiteId());
    }

    /**
     * 全局共享数据如果被其它站点使用，则不可以改为本站私有数据
     */
    @GetMapping("scope-not-allowed")
    @PreAuthorize("hasAnyAuthority('role:validation','*')")
    public boolean scopeNotAllowed(int scope, Long roleId) {
        Site site = Contexts.getCurrentSite();
        return scope == SCOPE_PRIVATE && userService.existsByRoleId(roleId, site.getOrgId());
    }

    private void validatePermission(Long siteId, boolean isGlobal, Short origRank, Short newRank, User currentUser) {
        dataInSite(siteId, getCurrentSiteId());
        rankPermission(origRank, newRank, currentUser.getRank());
        globalPermission(isGlobal, currentUser.hasGlobalPermission());
    }

    private void validateBean(Role role, boolean origGlobal, Short origRank, User currentUser) {
        boolean isGlobal = role.isGlobal() || origGlobal;
        validatePermission(role.getSiteId(), isGlobal, origRank, role.getRank(), currentUser);
        if (scopeNotAllowed(role.getScope(), role.getId())) {
            throw new Http400Exception("scope not allowed " + role.getScope());
        }
    }

    private static final String ROLE_NOT_FOUND = "Role not found. ID = ";

    public static class RoleParams extends Role {
        private static final long serialVersionUID = 1L;
        /**
         * 文章权限，栏目ID列表
         */
        private List<Long> articlePermissions = new ArrayList<>();
        /**
         * 栏目权限，栏目ID列表
         */
        private List<Long> channelPermissions = new ArrayList<>();
        /**
         * 组织ID权限
         */
        private List<Long> orgPermissions = new ArrayList<>();

        public List<Long> getArticlePermissions() {
            return articlePermissions;
        }

        public void setArticlePermissions(List<Long> articlePermissions) {
            this.articlePermissions = articlePermissions;
        }

        public List<Long> getChannelPermissions() {
            return channelPermissions;
        }

        public void setChannelPermissions(List<Long> channelPermissions) {
            this.channelPermissions = channelPermissions;
        }

        public List<Long> getOrgPermissions() {
            return orgPermissions;
        }

        public void setOrgPermissions(List<Long> orgPermissions) {
            this.orgPermissions = orgPermissions;
        }
    }
}