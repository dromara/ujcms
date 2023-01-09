package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.OrgService;
import com.ujcms.cms.core.service.args.OrgArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.exception.Http403Exception;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

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
                       @Nullable Integer parentId, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        boolean needParentId = current || !user.hasGlobalPermission();
        if (parentId == null && needParentId) {
            parentId = Contexts.getCurrentSite().getOrgId();
        }
        OrgArgs args = OrgArgs.of(getQueryMap(request.getQueryString())).parentId(parentId);
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('org:show','*')")
    public Object show(@PathVariable Integer id) {
        Org bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Org not found. ID = " + id);
        }
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Integer> orgIds = service.listByAncestorId(site.getOrgId());
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
        List<Integer> orgIds = service.listByAncestorId(site.getOrgId());
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
            return Responses.notFound("Org not found. ID = " + bean.getId());
        }
        Integer origParentId = org.getParentId();
        Entities.copy(bean, org, "parentId", "order");
        List<Integer> ancestorIds = service.listByAncestorId(org.getId());
        if (ancestorIds.contains(org.getParentId())) {
            return Responses.notFound("Org parentId cannot be its own descendant. ID = " + org.getParentId());
        }
        User user = Contexts.getCurrentUser();
        List<Integer> permissionOrgIds = service.listByAncestorId(user.getOrgId());
        if (Objects.equals(origParentId, org.getParentId())) {
            validateDataPermission(permissionOrgIds, user, org.getId());
        } else {
            validateDataPermission(permissionOrgIds, user, org.getId(), org.getParentId());
        }
        service.update(org, bean.getParentId());
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('org:update','*')")
    @OperationLog(module = "org", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody Integer[] ids) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Integer> orgIds = service.listByAncestorId(site.getOrgId());
        List<Org> list = new ArrayList<>();
        for (Integer id : ids) {
            Org bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Org not found. ID = " + id);
            }
            validateDataPermission(orgIds, user, bean.getId());
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('org:delete','*')")
    @OperationLog(module = "org", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Integer> orgIds = service.listByAncestorId(site.getOrgId());
        for (Integer id : ids) {
            Org bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Org not found. ID = " + id);
            }
            validateDataPermission(orgIds, user, bean.getId());
            service.delete(id);
        }
        return Responses.ok();
    }

    private void validateDataPermission(List<Integer> permissionOrgIds, User currentUser, Integer... orgIds) {
        if (currentUser.hasGlobalPermission()) {
            return;
        }
        for (Integer orgId : orgIds) {
            if (!permissionOrgIds.contains(orgId)) {
                throw new Http403Exception("Org data forbidden. ID: " + orgId);
            }
        }
    }
}