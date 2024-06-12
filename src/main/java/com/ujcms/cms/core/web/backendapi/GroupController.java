package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.service.args.GroupArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
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

import static com.ujcms.cms.core.support.Contexts.getCurrentSiteId;
import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 用户组 Controller
 *
 * @author PONY
 */
@RestController("backendGroupController")
@RequestMapping(BACKEND_API + "/core/group")
public class GroupController {
    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('group:list','*')")
    public List<Group> list(@Nullable Short type, HttpServletRequest request) {
        GroupArgs args = GroupArgs.of(getQueryMap(request.getQueryString())).type(type);
        return service.selectList(args);
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('group:show','*')")
    public Group show(@PathVariable Long id) {
        Group bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception(Group.NOT_FOUND + id);
        }
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('group:create','*')")
    @OperationLog(module = "group", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody Group bean) {
        service.insert(bean);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('group:update','*')")
    @OperationLog(module = "group", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody Group bean) {
        service.update(bean);
        return Responses.ok();
    }

    @PutMapping("permission")
    @PreAuthorize("hasAnyAuthority('group:updatePermission','*')")
    @OperationLog(module = "group", operation = "updatePermission", type = OperationType.UPDATE)
    public ResponseEntity<Body> updatePermission(@RequestBody Group bean) {
        Group group = service.select(bean.getId());
        if (group == null) {
            return Responses.notFound("Group not found. ID = " + bean.getId());
        }
        Site site = Contexts.getCurrentSite();
        Entities.copyIncludes(bean, group, "allAccessPermission");
        service.update(group, bean.getAccessPermissions(), site.getId());
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('group:update','*')")
    @OperationLog(module = "group", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateOrder(@RequestBody Long[] ids) {
        List<Group> list = new ArrayList<>();
        for (Long id : ids) {
            Group bean = service.select(id);
            if (bean == null) {
                return Responses.notFound("Role not found. ID = " + id);
            }
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('group:delete','*')")
    @OperationLog(module = "group", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        service.delete(ids);
        return Responses.ok();
    }

    @GetMapping("access-permissions")
    @PreAuthorize("hasAnyAuthority('group:list','*')")
    public List<Long> accessPermissions(Long groupId, @Nullable Long siteId) {
        return service.listAccessPermissions(groupId, siteId != null ? siteId : getCurrentSiteId());
    }
}