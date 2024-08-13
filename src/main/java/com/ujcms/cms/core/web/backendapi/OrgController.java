package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.args.OrgArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.MoveParams;
import com.ujcms.commons.db.tree.TreeSortEntity;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http403Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 组织 Controller
 *
 * @author PONY
 */
@RestController("backendOrgController")
@RequestMapping(BACKEND_API + "/core/org")
public class OrgController {
    private final OrgService service;

    public OrgController(OrgService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('org:list','*')")
    public Object list(@RequestParam(defaultValue = "false") boolean current,
                       @RequestParam(defaultValue = "true") boolean isIncludeChildren,
                       @RequestParam(defaultValue = "false") boolean isIncludeSelf,
                       @Nullable Long parentId,
                       HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        boolean needParentId = current || !user.hasGlobalPermission();
        if (parentId == null && needParentId) {
            parentId = Contexts.getCurrentSite().getOrgId();
        }
        OrgArgs args = OrgArgs.of(getQueryMap(request.getQueryString()));
        if (isIncludeChildren) {
            args.ancestorId(parentId);
        } else if (parentId == null) {
            args.parentIdIsNull();
        } else if (isIncludeSelf) {
            args.parentIdAndSelf(parentId);
        } else {
            args.parentId(parentId);
        }
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('org:show','*')")
    public Object show(@PathVariable Long id) {
        Org bean = service.select(id);
        if (bean == null) {
            return Responses.notFound(ORG_NOT_FOUND + id);
        }
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Long> orgIds = service.listByAncestorId(site.getOrgId());
        validateDataPermission(orgIds, user, bean.getId());
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('org:create','*')")
    @OperationLog(module = "org", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody Org bean) {
        Org org = new Org();
        Entities.copy(bean, org);
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Long> orgIds = service.listByAncestorId(site.getOrgId());
        validateDataPermission(orgIds, user, org.getParentId());
        service.insert(org);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('org:update','*')")
    @OperationLog(module = "org", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody Org bean) {
        Org org = service.select(bean.getId());
        if (org == null) {
            return Responses.notFound(ORG_NOT_FOUND + bean.getId());
        }
        Long origParentId = org.getParentId();
        Entities.copy(bean, org, "parentId", "order");
        List<Long> ancestorIds = service.listByAncestorId(org.getId());
        if (ancestorIds.contains(org.getParentId())) {
            return Responses.notFound("Org parentId cannot be its own descendant. ID = " + org.getParentId());
        }
        User user = Contexts.getCurrentUser();
        List<Long> permissionOrgIds = service.listByAncestorId(user.getOrgId());
        if (Objects.equals(origParentId, org.getParentId())) {
            validateDataPermission(permissionOrgIds, user, org.getId());
        } else {
            validateDataPermission(permissionOrgIds, user, org.getId(), org.getParentId());
        }
        service.update(org);
        return Responses.ok();
    }

    @PutMapping("permission")
    @PreAuthorize("hasAnyAuthority('org:updatePermission','*')")
    @OperationLog(module = "org", operation = "updatePermission", type = OperationType.UPDATE)
    public ResponseEntity<Body> updatePermission(@RequestBody RoleController.RoleParams bean) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Org org = service.select(bean.getId());
        if (org == null) {
            throw new Http404Exception(ORG_NOT_FOUND + bean.getId());
        }
        List<Long> permissionOrgIds = service.listByAncestorId(user.getOrgId());
        validateDataPermission(permissionOrgIds, user, bean.getId());
        service.updatePermission(org.getId(), site.getId(), bean.getArticlePermissions(), bean.getChannelPermissions(),
                bean.isGlobal() ? null : site.getOrgId(), bean.getOrgPermissions());
        return Responses.ok();
    }

    @PutMapping("move")
    @PreAuthorize("hasAnyAuthority('org:update','*')")
    @OperationLog(module = "org", operation = "move", type = OperationType.UPDATE)
    public ResponseEntity<Body> move(@RequestBody MoveParams params) {
        Org from = Optional.ofNullable(service.select(params.getFromId()))
                .orElseThrow(() -> new Http404Exception(Channel.NOT_FOUND + params.getFromId()));
        Org to = Optional.ofNullable(service.select(params.getToId()))
                .orElseThrow(() -> new Http404Exception(Channel.NOT_FOUND + params.getToId()));
        // 不能移动到自己的子节点下
        for (Org parent : to.getPaths()) {
            if (parent.getId().equals(from.getId())) {
                throw new Http400Exception(String.format("Cannot move Org(id=%s) to child(id=%s)",
                        params.getFromId(), params.getToId()));
            }
        }
        service.move(from, to, params.getType());
        return Responses.ok();
    }

    /**
     * 整理树形结构
     */
    @PutMapping("tidy-tree")
    @PreAuthorize("hasAnyAuthority('org:tidyTree','*')")
    @OperationLog(module = "org", operation = "tidyTree", type = OperationType.UPDATE)
    public ResponseEntity<Body> tidyTree() {
        List<Org> list = service.listForTidy();
        List<TreeSortEntity> tree = service.toTree(list);
        int size = list.size();
        service.tidyTreeOrderAndDepth(tree, size);
        service.deleteRelation();
        service.tidyTreeRelation(tree, size);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('org:delete','*')")
    @OperationLog(module = "org", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Long> orgIds = service.listByAncestorId(site.getOrgId());
        for (Long id : ids) {
            Org bean = service.select(id);
            if (bean == null) {
                continue;
            }
            validateDataPermission(orgIds, user, bean.getId());
            delete(bean);
        }
        return Responses.ok();
    }

    /**
     * 递归删除组织。不能放到Service里面递归，这样会因事务太大，导致性能低下。
     *
     * @param bean 组织
     * @return 删除条数
     */
    private int delete(Org bean) {
        int deleted = 0;
        for (Org o : service.listChildren(bean.getId())) {
            deleted += delete(o);
        }
        deleted = service.delete(bean);
        return deleted;
    }

    @GetMapping("article-permissions")
    @PreAuthorize("hasAnyAuthority('org:list','*')")
    public List<Long> articlePermissions(Long orgId, @Nullable Long siteId) {
        return service.listArticlePermissions(orgId, siteId != null ? siteId : getCurrentSiteId());
    }

    @GetMapping("channel-permissions")
    @PreAuthorize("hasAnyAuthority('org:list','*')")
    public List<Long> channelPermissions(Long orgId, @Nullable Long siteId) {
        return service.listChannelPermissions(orgId, siteId != null ? siteId : getCurrentSiteId());
    }

    @GetMapping("org-permissions")
    @PreAuthorize("hasAnyAuthority('org:list','*')")
    public List<Long> orgPermissions(Long orgId, @RequestParam(defaultValue = "false") boolean global) {
        Site site = Contexts.getCurrentSite();
        return service.listOrgPermissions(orgId, global ? null : site.getOrgId());
    }

    @GetMapping("permissions")
    @PreAuthorize("hasAnyAuthority('org:list','*')")
    public Collection<Long> permissions() {
        User user = Contexts.getCurrentUser();
        if (user.getDataScope() == Role.DATA_SCOPE_PERM_ORG) {
            return service.listPermissions(user.fetchRoleIds(), user.fetchAllOrgIds());
        } else {
            return user.fetchAllOrgIds();
        }
    }

    private void validateDataPermission(List<Long> permissionOrgIds, User currentUser, Long... orgIds) {
        if (currentUser.hasGlobalPermission()) {
            return;
        }
        for (Long orgId : orgIds) {
            if (!permissionOrgIds.contains(orgId)) {
                throw new Http403Exception("Org data forbidden. ID: " + orgId);
            }
        }
    }

    private static final String ORG_NOT_FOUND = "Org not found. ID = ";

    public static class OrgParams extends Org {
        private static final long serialVersionUID = 1L;
        /**
         * 是否全局数据
         */
        private boolean global = false;
        /**
         * 文章权限，栏目ID列表
         */
        private List<Integer> articlePermissions = new ArrayList<>();
        /**
         * 栏目权限，栏目ID列表
         */
        private List<Integer> channelPermissions = new ArrayList<>();
        /**
         * 组织ID权限
         */
        private List<Integer> orgPermissions = new ArrayList<>();

        public boolean isGlobal() {
            return global;
        }

        public void setGlobal(boolean global) {
            this.global = global;
        }

        public List<Integer> getArticlePermissions() {
            return articlePermissions;
        }

        public void setArticlePermissions(List<Integer> articlePermissions) {
            this.articlePermissions = articlePermissions;
        }

        public List<Integer> getChannelPermissions() {
            return channelPermissions;
        }

        public void setChannelPermissions(List<Integer> channelPermissions) {
            this.channelPermissions = channelPermissions;
        }

        public List<Integer> getOrgPermissions() {
            return orgPermissions;
        }

        public void setOrgPermissions(List<Integer> orgPermissions) {
            this.orgPermissions = orgPermissions;
        }
    }
}